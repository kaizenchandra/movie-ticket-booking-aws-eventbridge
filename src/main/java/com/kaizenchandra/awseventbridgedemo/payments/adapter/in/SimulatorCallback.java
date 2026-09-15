package com.kaizenchandra.awseventbridgedemo.payments.adapter.in;

import com.kaizenchandra.awseventbridgedemo.payments.application.PaymentCallbacks;
import com.kaizenchandra.awseventbridgedemo.shared.domain.Problem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

@RestController
@Profile({"local", "test"})
public class SimulatorCallback {
    private final PaymentCallbacks callbacks;
    private final reactor.core.scheduler.Scheduler scheduler;
    private final String secret;
    private final Clock clock;
    private final JsonMapper json;

    public SimulatorCallback(PaymentCallbacks callbacks, reactor.core.scheduler.Scheduler scheduler, @Value("${payment.callback-secret}") String secret, Clock clock, JsonMapper json) {
        this.callbacks = callbacks;
        this.scheduler = scheduler;
        this.secret = secret;
        this.clock = clock;
        this.json = json;
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

    public record Callback(UUID bookingId, boolean success) {
    }
}
