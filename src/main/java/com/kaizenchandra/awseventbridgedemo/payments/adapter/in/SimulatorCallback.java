package com.kaizenchandra.awseventbridgedemo.payments.adapter.in;

import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import com.kaizenchandra.awseventbridgedemo.booking.application.*;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.in.BlockingBoundary;
import com.kaizenchandra.awseventbridgedemo.shared.domain.Problem;

@RestController
@Profile({"local", "test"})
public class SimulatorCallback {
    private final com.kaizenchandra.awseventbridgedemo.payments.application.PaymentCallbacks callbacks;
    private final reactor.core.scheduler.Scheduler scheduler;
    private final String secret;
    private final Clock clock;
    private final JsonMapper json;

    public SimulatorCallback(com.kaizenchandra.awseventbridgedemo.payments.application.PaymentCallbacks callbacks, reactor.core.scheduler.Scheduler scheduler, @Value("${payment.callback-secret}") String secret, Clock clock, JsonMapper json) {
        this.callbacks = callbacks;
        this.scheduler = scheduler;
        this.secret = secret;
        this.clock = clock;
        this.json = json;
    }

    public record Callback(UUID bookingId, boolean success) {
    }

    @PostMapping("/api/simulator/callback")
    public Mono<?> callback(@RequestHeader("X-Timestamp") long timestamp, @RequestHeader("X-Signature") String signature, @RequestBody String body) throws Exception {
        Problem.require(Math.abs(clock.instant().getEpochSecond() - timestamp) <= 300, "INVALID_SIGNATURE");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        Problem.require(MessageDigest.isEqual(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)), HexFormat.of().parseHex(signature)), "INVALID_SIGNATURE");
        var c = json.readValue(body, Callback.class);
        return Mono.fromCallable(() -> callbacks.receive(c.bookingId(), c.success())).subscribeOn(scheduler).timeout(Duration.ofSeconds(8));
    }
}
