package com.kaizenchandra.awseventbridgedemo.notifications.adapter.in;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.*;
import java.util.*;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import tools.jackson.databind.json.JsonMapper;
import com.kaizenchandra.awseventbridgedemo.shared.application.Transactions;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.out.Sql;
import com.kaizenchandra.awseventbridgedemo.notifications.application.IntegrationEvent;

@Component
public class NotificationConsumer {
    private final io.opentelemetry.api.OpenTelemetry telemetry;
    private final Transactions tx;
    private final Sql sql;
    private final SqsClient sqs;
    private final JsonMapper json;
    private final String queue;

    public NotificationConsumer(Transactions tx, Sql sql, SqsClient sqs, JsonMapper json, @Value("${aws.queue-url}") String queue,io.opentelemetry.api.OpenTelemetry telemetry) {
        this.telemetry=telemetry;
        this.tx = tx;
        this.sql = sql;
        this.sqs = sqs;
        this.json = json;
        this.queue = queue;
    }

    public void accept(String body) {
        var envelope = json.readTree(body);
        if (!"cinema.booking".equals(envelope.path("source").asText()) || !"BookingChanged.v1".equals(envelope.path("detail-type").asText()))
            throw new IllegalArgumentException("UNSUPPORTED_EVENT");
        var e = json.treeToValue(envelope.get("detail"), IntegrationEvent.class);
        if (e.schemaVersion() != 1 || !"BookingChanged".equals(e.type()) || e.eventId() == null || e.aggregateId() == null || e.aggregateVersion() < 0 || e.occurredAt() == null)
            throw new IllegalArgumentException("INVALID_EVENT");
        com.kaizenchandra.awseventbridgedemo.booking.domain.BookingStatus.valueOf(e.status());
        var parent=io.opentelemetry.context.Context.root();
        if(e.traceParent()!=null) {
            parent=telemetry.getPropagators().getTextMapPropagator().extract(parent,Map.of("traceparent",e.traceParent()),new io.opentelemetry.context.propagation.TextMapGetter<Map<String,String>>() {
                public Iterable<String> keys(Map<String,String> carrier){return carrier.keySet();}
                public String get(Map<String,String> carrier,String key){return carrier.get(key);}
            });
        }
        var span=telemetry.getTracer("cinema.notifications").spanBuilder("notification.consume").setParent(parent).startSpan();
        try(var scope=span.makeCurrent()) {
        tx.execute(() -> {
            if (sql.update("INSERT INTO consumer_receipt(consumer,event_id) VALUES('notification',?1) ON CONFLICT DO NOTHING", e.eventId()) == 0)
                return false;
            sql.update("INSERT INTO notification(booking_id,version,status,updated_at) VALUES(?1,?2,?3,?4) ON CONFLICT(booking_id) DO UPDATE SET version=excluded.version,status=excluded.status,updated_at=excluded.updated_at WHERE notification.version<excluded.version", e.aggregateId(), e.aggregateVersion(), e.status(), e.occurredAt());
            return true;
        });
        } finally {span.end();}
    }

    public void poll() {
        var response = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queue).waitTimeSeconds(5).maxNumberOfMessages(5).visibilityTimeout(60).messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT).build());
        for (var message : response.messages()) {
            try {
                accept(message.body());
                sqs.deleteMessage(b -> b.queueUrl(queue).receiptHandle(message.receiptHandle()));
            } catch (RuntimeException e) {
                int count = Integer.parseInt(message.attributesAsStrings().getOrDefault("ApproximateReceiveCount", "1"));
                sqs.changeMessageVisibility(b -> b.queueUrl(queue).receiptHandle(message.receiptHandle()).visibilityTimeout(Math.min(300, 1 << Math.min(count, 8))));
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("consumer failure messageId={} type={}", message.messageId(), e.getClass().getSimpleName());
            }
        }
    }
}
