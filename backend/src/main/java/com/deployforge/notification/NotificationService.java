package com.deployforge.notification;

import com.deployforge.common.api.PageResponse;
import com.deployforge.common.error.Exceptions.NotFoundException;
import com.deployforge.security.SecretRedactionService;
import com.deployforge.workspace.WorkspaceMemberRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications.
 *
 * <p>Fan-out is per workspace member, so a failed production deploy reaches everyone who can act on it,
 * not just whoever pressed the button (a webhook triggered deploy has no button at all).
 *
 * <p>Delivery is best effort: this must never be able to fail a deployment.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final WorkspaceMemberRepository memberRepository;
    private final SecretRedactionService redaction;

    public NotificationService(
            NotificationRepository repository,
            WorkspaceMemberRepository memberRepository,
            SecretRedactionService redaction) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.redaction = redaction;
    }

    @Transactional
    public void notifyWorkspace(
            UUID workspaceId,
            NotificationType type,
            String title,
            String message,
            UUID projectId,
            UUID deploymentId) {
        try {
            String safeMessage = redaction.redact(message);
            List<Notification> notifications =
                    memberRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                            .map(
                                    member ->
                                            new Notification(
                                                    member.getUserId(),
                                                    type,
                                                    title,
                                                    safeMessage,
                                                    projectId,
                                                    deploymentId))
                            .toList();
            repository.saveAll(notifications);
        } catch (RuntimeException e) {
            log.warn("notification_fanout_failed workspace={} type={} reason={}", workspaceId, type, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, int page, int size) {
        return PageResponse.from(
                repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        Notification notification =
                repository
                        .findById(notificationId)
                        .filter(candidate -> candidate.getUserId().equals(userId))
                        .orElseThrow(() -> NotFoundException.of("Notification", notificationId));
        notification.markRead();
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repository.markAllRead(userId);
    }

    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        return repository.deleteOlderThan(cutoff);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getProjectId(),
                notification.getDeploymentId(),
                notification.getCreatedAt());
    }

    public record NotificationResponse(
            UUID id,
            NotificationType type,
            String title,
            String message,
            boolean read,
            UUID projectId,
            UUID deploymentId,
            Instant createdAt) {}
}
