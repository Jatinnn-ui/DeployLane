package com.deployforge.notification;

import com.deployforge.common.api.PageResponse;
import com.deployforge.notification.NotificationService.NotificationResponse;
import com.deployforge.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Validated
@Tag(name = "Notifications", description = "In-app deployment notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return notificationService.list(user.userId(), page, size);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread notifications, used for the sidebar badge")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("count", notificationService.unreadCount(user.userId()));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID notificationId) {
        notificationService.markRead(notificationId, user.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("updated", notificationService.markAllRead(user.userId()));
    }
}
