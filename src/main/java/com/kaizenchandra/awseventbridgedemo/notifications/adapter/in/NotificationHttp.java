package com.kaizenchandra.awseventbridgedemo.notifications.adapter.in;

import com.kaizenchandra.awseventbridgedemo.notifications.application.NotificationPort;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.in.BlockingBoundary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class NotificationHttp {
    private final NotificationPort notifications;
    private final BlockingBoundary boundary;

    public NotificationHttp(NotificationPort notifications, BlockingBoundary boundary) {
        this.notifications = notifications;
        this.boundary = boundary;
    }

    @GetMapping("/api/notifications")
    public Mono<?> inbox(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return boundary.call(() -> notifications.inbox(jwt.getSubject(), page, size));
    }
}
