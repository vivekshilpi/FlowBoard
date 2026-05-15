package com.flowboard.notification_service.service;

import com.flowboard.notification_service.dto.NotificationResponse;
import com.flowboard.notification_service.dto.SendBulkNotificationRequest;
import com.flowboard.notification_service.dto.SendNotificationRequest;
import com.flowboard.notification_service.dto.AuthUserLookupResponse;
import com.flowboard.notification_service.entity.Notification;
import com.flowboard.notification_service.enums.NotificationType;
import com.flowboard.notification_service.exception.CustomException;
import com.flowboard.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService{

    private final NotificationRepository repository;
    private final EmailNotificationService emailService;
    private final RestTemplate restTemplate = buildRestTemplate();

    @Override
    @Transactional
    public NotificationResponse send(SendNotificationRequest request) {
        if (request.getType() == NotificationType.WORKSPACE_INVITE
                && request.getRelatedId() != null
                && request.getRelatedType() != null) {
            var existing = repository.findFirstByRecipientIdAndTypeAndRelatedIdAndRelatedTypeOrderByCreatedAtDesc(
                    request.getRecipientId(),
                    request.getType(),
                    request.getRelatedId(),
                    request.getRelatedType()
            );

            if (existing.isPresent() && !existing.get().isRead()) {
                return toResponse(existing.get());
            }
        }

        Notification notification = Notification.builder()
                .recipientId(request.getRecipientId())
                .actorId(request.getActorId())
                .type(request.getType())
                .title(request.getTitle())
                .message(request.getMessage())
                .relatedId(request.getRelatedId())
                .relatedType(request.getRelatedType())
                .deepLinkUrl(request.getDeepLinkUrl())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        repository.save(notification);

        String recipientEmail = resolveRecipientEmail(request);
        if(request.isSendEmail()
        && recipientEmail != null
        && !recipientEmail.isBlank()){
            emailService.sendNotificationEmail(
                    recipientEmail,
                    request.getTitle(),
                    request.getMessage(),
                    request.getDeepLinkUrl()
            );
        }
        log.info("Notification sent: type={} recipientId={} relatedId={}",
                request.getType(), request.getRecipientId(), request.getRelatedId());
        return toResponse(notification);
    }

    @Override
    @Transactional
    public List<NotificationResponse> sendBulk(SendBulkNotificationRequest request) {
        List<Notification> notifications = request.getRecipientIds()
                .stream()
                .map(recipientId -> Notification.builder()
                        .recipientId(recipientId)
                        .actorId(request.getActorId())
                        .type(request.getType())
                        .title(request.getTitle())
                        .message(request.getMessage())
                        .relatedId(request.getRelatedId())
                        .relatedType(request.getRelatedType())
                        .deepLinkUrl(request.getDeepLinkUrl())
                        .isRead(false)
                        .createdAt(LocalDateTime.now())
                        .build()).toList();
        repository.saveAll(notifications);

        log.info("Bulk notification sent: type={} recipientCount={}",
                request.getType(), request.getRecipientIds().size());
        return notifications.stream().map(this::toResponse).toList();
    }

    @Override
    public NotificationResponse getById(Long notificationId, Long recipientId) {
        Notification notification = repository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new CustomException(
                        "Notification not found or access denied",
                        HttpStatus.NOT_FOUND));
        return repairAndMap(notification);
    }

    @Override
    public List<NotificationResponse> getByRecipient(Long recipientId) {
        return repository
                .findByRecipientIdOrderByCreatedAtDesc(recipientId)
                .stream().map(this::repairAndMap).toList();
    }

    @Override
    public List<NotificationResponse> getByRecipient(Long recipientId, Integer limit) {
        if (limit == null || limit <= 0) {
            return getByRecipient(recipientId);
        }

        return repository
                .findByRecipientIdOrderByCreatedAtDesc(recipientId, PageRequest.of(0, limit))
                .stream().map(this::repairAndMap).toList();
    }

    @Override
    public List<NotificationResponse> getUnreadByRecipient(Long recipientId) {
        return repository
                .findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId)
                .stream().map(this::repairAndMap).toList();
    }

    @Override
    public List<NotificationResponse> getByRecipientAndType(Long recipientId, NotificationType type) {
        return repository.findByRecipientIdAndTypeOrderByCreatedAtDesc(recipientId,
                type).stream()
                .map(this::repairAndMap).toList();
    }

    @Override
    public List<NotificationResponse> getAll() {
        return repository.findAll()
                .stream().map(this::repairAndMap).toList();
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long recipientId) {

        if(!repository.existsByIdAndRecipientId(notificationId, recipientId)){
            throw new CustomException(
                    "Notification not found or access denied",
                    HttpStatus.NOT_FOUND);
        }

        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new CustomException(
                        "Notification not found", HttpStatus.NOT_FOUND));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            repository.save(notification);
        }

        return toResponse(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long recipientId) {
            repository.markAllAsRead(recipientId);
            log.info("All notifications marked as read for recipientId={}", recipientId);
    }

    @Override
    public long getUnreadCount(Long recipientId) {
        return repository.countByRecipientIdAndIsReadFalse(recipientId);
    }

    @Override
    @Transactional
    public void deleteNotification(Long notificationId, Long recipientId) {
        if (!repository.existsByIdAndRecipientId(notificationId, recipientId)) {
            throw new CustomException(
                    "Notification not found or access denied",
                    HttpStatus.NOT_FOUND);
        }
        repository.deleteById(notificationId);
        log.info("Notification deleted: id={}", notificationId);
    }

    @Override
    @Transactional
    public void deleteReadNotifications(Long recipientId) {
        repository.deleteReadByRecipientId(recipientId);
        log.info("Read notifications deleted for recipientId={}", recipientId);
    }

    @Override
    @Transactional
    public void deleteByRelated(Long relatedId, String relatedType) {
        repository.deleteByRelatedIdAndRelatedType(relatedId, relatedType);
        log.info("Notifications deleted for relatedType={} relatedId={}", relatedType, relatedId);
    }

    @Override
    @Transactional
    public void deleteWorkspaceInviteForRecipient(Long recipientId, Long workspaceId) {
        repository.deleteByRecipientAndRelatedAndType(
                recipientId,
                workspaceId,
                "WORKSPACE",
                NotificationType.WORKSPACE_INVITE
        );
        log.info("Workspace invite notifications deleted for recipientId={} workspaceId={}",
                recipientId, workspaceId);
    }

    @Override
    @Transactional
    public void notifyAssignment(Long recipientId, Long actorId, Long cardId, String cardTitle, String recipientEmail) {
            SendNotificationRequest req = new SendNotificationRequest();
            req.setRecipientId(recipientId);
            req.setActorId(actorId);
            req.setType(NotificationType.ASSIGNMENT);
            req.setTitle("Card assigned to you");
            req.setMessage("You have been assigned to card: '" + cardTitle + "'");
            req.setRelatedId(cardId);
            req.setRelatedType("CARD");
            req.setDeepLinkUrl(null);
            // Assignment always triggers email — critical event
            req.setSendEmail(true);
            req.setRecipientEmail(recipientEmail);
            send(req);
    }

    @Override
    @Transactional
    public void notifyMention(Long recipientId, Long actorId, Long cardId, Long boardId, String cardTitle) {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(recipientId);
        req.setActorId(actorId);
        req.setType(NotificationType.MENTION);
        req.setTitle("You were mentioned in a comment");
        req.setMessage("Someone mentioned you in a comment on '"
                + cardTitle + "'");
        req.setRelatedId(cardId);
        req.setRelatedType("CARD");
        req.setDeepLinkUrl(cardDeepLink(boardId, cardId));
        req.setSendEmail(true);
        send(req);
    }

    @Override
    @Transactional
    public void notifyDueDateApproaching(Long recipientId, Long cardId, Long boardId, String cardTitle, String timeLeft) {

        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(recipientId);
        req.setActorId(null); // system-generated
        req.setType(NotificationType.DUE_DATE);
        req.setTitle("Card due " + timeLeft);
        req.setMessage("Card '" + cardTitle
                + "' is due in " + timeLeft + ". Mark it done to clear.");
        req.setRelatedId(cardId);
        req.setRelatedType("CARD");
        req.setDeepLinkUrl(cardDeepLink(boardId, cardId));
        req.setSendEmail(true);
        send(req);
    }

    @Override
    @Transactional
    public void notifyCardMovedToDone(Long recipientId, Long actorId, Long cardId, Long boardId, String cardTitle) {

        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(recipientId);
        req.setActorId(actorId);
        req.setType(NotificationType.MOVE);
        req.setTitle("Card moved to Done");
        req.setMessage("Card '" + cardTitle + "' has been marked as Done.");
        req.setRelatedId(cardId);
        req.setRelatedType("CARD");
        req.setDeepLinkUrl(cardDeepLink(boardId, cardId));
        req.setSendEmail(true);
        send(req);
    }

    @Override
    @Transactional
    public void notifyCommentReply(Long recipientId, Long actorId, Long cardId, Long boardId, String cardTitle) {

        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(recipientId);
        req.setActorId(actorId);
        req.setType(NotificationType.COMMENT);
        req.setTitle("New reply on your comment");
        req.setMessage("Someone replied to your comment on '"
                + cardTitle + "'");
        req.setRelatedId(cardId);
        req.setRelatedType("CARD");
        req.setDeepLinkUrl(cardDeepLink(boardId, cardId));
        req.setSendEmail(false);
        send(req);
    }

    @Override
    @Transactional
    public void notifyOverdue(Long recipientId, Long cardId, Long boardId, String cardTitle, String dueDate, String recipientEmail) {

        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(recipientId);
        req.setActorId(null); // system-generated
        req.setType(NotificationType.OVERDUE);
        req.setTitle("Overdue card");
        req.setMessage("Card '" + cardTitle
                + "' was due on " + dueDate
                + " and is not yet done.");
        req.setRelatedId(cardId);
        req.setRelatedType("CARD");
        req.setDeepLinkUrl(cardDeepLink(boardId, cardId));
        // Overdue always sends email — critical event
        req.setSendEmail(true);
        req.setRecipientEmail(recipientEmail);
        send(req);
    }

    private String cardDeepLink(Long boardId, Long cardId) {
        return boardId == null ? null : "/board/" + boardId + "?cardId=" + cardId;
    }

    private String resolveRecipientEmail(SendNotificationRequest request) {
        if (request.getRecipientEmail() != null && !request.getRecipientEmail().isBlank()) {
            return request.getRecipientEmail();
        }

        if (!request.isSendEmail() || request.getRecipientId() == null) {
            return null;
        }

        AuthUserLookupResponse recipient = lookupUserById(request.getRecipientId());
        return recipient == null ? null : recipient.getEmail();
    }

    private NotificationResponse repairAndMap(Notification notification) {
        return toResponse(repairWorkspaceInviteNotification(notification));
    }

    private Notification repairWorkspaceInviteNotification(Notification notification) {
        if (notification.getType() != NotificationType.WORKSPACE_INVITE
                || notification.getActorId() == null
                || notification.getMessage() == null
                || !notification.getMessage().contains("FlowBoard User invited you")) {
            return notification;
        }

        AuthUserLookupResponse actor = lookupUserById(notification.getActorId());
        if (actor == null || actor.getFullName() == null || actor.getFullName().isBlank()) {
            return notification;
        }

        String repairedMessage = actor.getFullName() + notification.getMessage()
                .substring("FlowBoard User".length());
        notification.setMessage(repairedMessage);
        return repository.save(notification);
    }

    private AuthUserLookupResponse lookupUserById(Long userId) {
        try {
            ResponseEntity<AuthUserLookupResponse> response = restTemplate.getForEntity(
                    "http://localhost:8081/api/v1/auth/internal/by-id?id={id}",
                    AuthUserLookupResponse.class,
                    userId
            );
            return response.getBody();
        } catch (RestClientException e) {
            log.warn("Failed to repair notification actor {} from auth-service: {}", userId, e.getMessage());
            return null;
        }
    }

    private NotificationResponse toResponse(Notification n){
        return NotificationResponse.builder()
                .id(n.getId())
                .recipientId(n.getRecipientId())
                .actorId(n.getActorId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .relatedId(n.getRelatedId())
                .relatedType(n.getRelatedType())
                .deepLinkUrl(n.getDeepLinkUrl())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .build();

    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(1500);
        return new RestTemplate(factory);
    }
}
