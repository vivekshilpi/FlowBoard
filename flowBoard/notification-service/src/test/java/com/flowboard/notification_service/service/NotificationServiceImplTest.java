package com.flowboard.notification_service.service;

import com.flowboard.notification_service.dto.*;
import com.flowboard.notification_service.entity.Notification;
import com.flowboard.notification_service.enums.NotificationType;
import com.flowboard.notification_service.exception.CustomException;
import com.flowboard.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl Tests")
class NotificationServiceImplTest {

    @Mock NotificationRepository repository;
    @Mock EmailNotificationService emailService;
    @Mock RestTemplate restTemplate;

    @InjectMocks NotificationServiceImpl notificationService;

    private Notification sampleNotif;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "restTemplate", restTemplate);
        sampleNotif = Notification.builder()
                .id(1L).recipientId(2L).actorId(1L)
                .type(NotificationType.ASSIGNMENT)
                .title("Card assigned").message("You were assigned")
                .isRead(false).createdAt(LocalDateTime.now()).build();
    }

    @Test @DisplayName("send() — saves notification, no email when sendEmail=false")
    void send_noEmail_success() {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(2L); req.setActorId(1L);
        req.setType(NotificationType.ASSIGNMENT);
        req.setTitle("Test"); req.setMessage("...");
        req.setSendEmail(false);

        when(repository.save(any())).thenReturn(sampleNotif);

        NotificationResponse resp = notificationService.send(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getType()).isEqualTo(NotificationType.ASSIGNMENT);
        verify(emailService, never()).sendNotificationEmail(
                any(), any(), any(), any());
    }

    @Test @DisplayName("send() — triggers email when sendEmail=true with email")
    void send_withEmail_triggersEmail() {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(2L); req.setType(NotificationType.ASSIGNMENT);
        req.setTitle("Test"); req.setMessage("...");
        req.setSendEmail(true);
        req.setRecipientEmail("user@example.com");

        when(repository.save(any())).thenReturn(sampleNotif);

        notificationService.send(req);

        verify(emailService).sendNotificationEmail(
                eq("user@example.com"), any(), any(), any());
    }

    @Test @DisplayName("send() — resolves recipient email from auth lookup when omitted")
    void send_withEmail_lookupFallback_triggersEmail() {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(2L);
        req.setType(NotificationType.MOVE);
        req.setTitle("Moved");
        req.setMessage("Card moved");
        req.setSendEmail(true);

        AuthUserLookupResponse lookupResponse = new AuthUserLookupResponse();
        lookupResponse.setId(2L);
        lookupResponse.setEmail("lookup@example.com");

        when(repository.save(any())).thenReturn(sampleNotif);
        when(restTemplate.getForEntity(anyString(), eq(AuthUserLookupResponse.class), eq(2L)))
                .thenReturn(ResponseEntity.ok(lookupResponse));

        notificationService.send(req);

        verify(emailService).sendNotificationEmail(
                eq("lookup@example.com"), eq("Moved"), eq("Card moved"), isNull());
    }

    @Test @DisplayName("send() — deduplicates unread workspace invite notifications")
    void send_workspaceInvite_deduplicatesUnreadNotification() {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(2L);
        req.setActorId(1L);
        req.setType(NotificationType.WORKSPACE_INVITE);
        req.setTitle("Invite");
        req.setMessage("Join workspace");
        req.setRelatedId(99L);
        req.setRelatedType("WORKSPACE");

        Notification existing = Notification.builder()
                .id(22L)
                .recipientId(2L)
                .actorId(1L)
                .type(NotificationType.WORKSPACE_INVITE)
                .title("Invite")
                .message("Join workspace")
                .relatedId(99L)
                .relatedType("WORKSPACE")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(repository.findFirstByRecipientIdAndTypeAndRelatedIdAndRelatedTypeOrderByCreatedAtDesc(
                2L, NotificationType.WORKSPACE_INVITE, 99L, "WORKSPACE"))
                .thenReturn(Optional.of(existing));

        NotificationResponse resp = notificationService.send(req);

        assertThat(resp.getId()).isEqualTo(22L);
        verify(repository, never()).save(any());
        verify(emailService, never()).sendNotificationEmail(any(), any(), any(), any());
    }

    @Test @DisplayName("sendBulk() — saves all notifications in one call")
    void sendBulk_success() {
        SendBulkNotificationRequest req = new SendBulkNotificationRequest();
        req.setRecipientIds(List.of(1L, 2L, 3L));
        req.setType(NotificationType.BROADCAST);
        req.setTitle("Maintenance"); req.setMessage("...");

        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<NotificationResponse> responses = notificationService.sendBulk(req);

        assertThat(responses).hasSize(3);
    }

    @Test @DisplayName("markAsRead() — sets isRead=true and readAt")
    void markAsRead_success() {
        when(repository.existsByIdAndRecipientId(1L, 2L)).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(sampleNotif));
        when(repository.save(any())).thenReturn(sampleNotif);

        notificationService.markAsRead(1L, 2L);

        assertThat(sampleNotif.isRead()).isTrue();
        assertThat(sampleNotif.getReadAt()).isNotNull();
    }

    @Test @DisplayName("markAsRead() — throws 404 for wrong recipient")
    void markAsRead_wrongRecipient_throws() {
        when(repository.existsByIdAndRecipientId(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> notificationService.markAsRead(1L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test @DisplayName("getUnreadCount() — returns count from repository")
    void getUnreadCount_success() {
        when(repository.countByRecipientIdAndIsReadFalse(2L)).thenReturn(7L);

        long count = notificationService.getUnreadCount(2L);

        assertThat(count).isEqualTo(7L);
    }

    @Test
    @DisplayName("getById() repairs workspace invite message using actor lookup")
    void getById_repairsWorkspaceInviteMessage() {
        Notification invite = Notification.builder()
                .id(3L)
                .recipientId(2L)
                .actorId(9L)
                .type(NotificationType.WORKSPACE_INVITE)
                .title("Invite")
                .message("FlowBoard User invited you to join workspace 'Dev Team' as MEMBER.")
                .createdAt(LocalDateTime.now())
                .build();
        AuthUserLookupResponse actor = new AuthUserLookupResponse();
        actor.setFullName("Vivek Shilpi");

        when(repository.findByIdAndRecipientId(3L, 2L)).thenReturn(Optional.of(invite));
        when(restTemplate.getForEntity(anyString(), eq(AuthUserLookupResponse.class), eq(9L)))
                .thenReturn(ResponseEntity.ok(actor));
        when(repository.save(invite)).thenReturn(invite);

        NotificationResponse response = notificationService.getById(3L, 2L);

        assertThat(response.getMessage()).startsWith("Vivek Shilpi invited you");
        verify(repository).save(invite);
    }

    @Test
    @DisplayName("getByRecipient(limit) uses paged repository call")
    void getByRecipient_limit_usesPageable() {
        when(repository.findByRecipientIdOrderByCreatedAtDesc(eq(2L), eq(PageRequest.of(0, 2))))
                .thenReturn(List.of(sampleNotif));

        List<NotificationResponse> responses = notificationService.getByRecipient(2L, 2);

        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("getByRecipient(limit<=0) falls back to unpaged lookup")
    void getByRecipient_nonPositiveLimit_fallsBack() {
        when(repository.findByRecipientIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(sampleNotif));

        List<NotificationResponse> responses = notificationService.getByRecipient(2L, 0);

        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("getUnread and getByType and getAll return mapped responses")
    void listRetrievalMethods_returnMappedResponses() {
        when(repository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(2L)).thenReturn(List.of(sampleNotif));
        when(repository.findByRecipientIdAndTypeOrderByCreatedAtDesc(2L, NotificationType.ASSIGNMENT))
                .thenReturn(List.of(sampleNotif));
        when(repository.findAll()).thenReturn(List.of(sampleNotif));

        assertThat(notificationService.getUnreadByRecipient(2L)).hasSize(1);
        assertThat(notificationService.getByRecipientAndType(2L, NotificationType.ASSIGNMENT)).hasSize(1);
        assertThat(notificationService.getAll()).hasSize(1);
    }

    @Test
    @DisplayName("getById returns 404 when notification missing")
    void getById_missing_throws() {
        when(repository.findByIdAndRecipientId(7L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getById(7L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("markAsRead leaves already-read notification untouched")
    void markAsRead_alreadyRead_skipsSave() {
        sampleNotif.setRead(true);
        when(repository.existsByIdAndRecipientId(1L, 2L)).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(sampleNotif));

        NotificationResponse response = notificationService.markAsRead(1L, 2L);

        assertThat(response.isRead()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("markAllAsRead delegates to repository")
    void markAllAsRead_delegates() {
        notificationService.markAllAsRead(2L);
        verify(repository).markAllAsRead(2L);
    }

    @Test
    @DisplayName("deleteNotification checks ownership before deletion")
    void deleteNotification_deletesOwnedNotification() {
        when(repository.existsByIdAndRecipientId(1L, 2L)).thenReturn(true);

        notificationService.deleteNotification(1L, 2L);

        verify(repository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteNotification throws when recipient is wrong")
    void deleteNotification_wrongRecipient_throws() {
        when(repository.existsByIdAndRecipientId(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> notificationService.deleteNotification(1L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("deleteReadNotifications and deleteByRelated delegate to repository")
    void deleteHelpers_delegateToRepository() {
        notificationService.deleteReadNotifications(2L);
        notificationService.deleteByRelated(99L, "CARD");
        notificationService.deleteWorkspaceInviteForRecipient(2L, 88L);

        verify(repository).deleteReadByRecipientId(2L);
        verify(repository).deleteByRelatedIdAndRelatedType(99L, "CARD");
        verify(repository).deleteByRecipientAndRelatedAndType(2L, 88L, "WORKSPACE", NotificationType.WORKSPACE_INVITE);
    }

    @Test
    @DisplayName("notify helper methods build the expected notification types")
    void notifyHelpers_buildExpectedRequests() {
        when(repository.save(any())).thenReturn(sampleNotif);
        when(restTemplate.getForEntity(anyString(), eq(AuthUserLookupResponse.class), anyLong()))
                .thenThrow(new RestClientException("down"));

        notificationService.notifyAssignment(2L, 1L, 8L, "Card", "user@test.com");
        notificationService.notifyMention(2L, 1L, 8L, 6L, "Card");
        notificationService.notifyDueDateApproaching(2L, 8L, 6L, "Card", "tomorrow");
        notificationService.notifyCardMovedToDone(2L, 1L, 8L, 6L, "Card");
        notificationService.notifyCommentReply(2L, 1L, 8L, 6L, "Card");
        notificationService.notifyOverdue(2L, 8L, 6L, "Card", "2026-05-01", "user@test.com");

        verify(repository, times(6)).save(any());
    }

    @Test
    @DisplayName("recipient email lookup failures are tolerated")
    void send_withLookupFailure_doesNotSendEmail() {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(2L);
        req.setType(NotificationType.MOVE);
        req.setTitle("Moved");
        req.setMessage("Card moved");
        req.setSendEmail(true);

        when(repository.save(any())).thenReturn(sampleNotif);
        when(restTemplate.getForEntity(anyString(), eq(AuthUserLookupResponse.class), eq(2L)))
                .thenThrow(new RestClientException("down"));

        notificationService.send(req);

        verify(emailService, never()).sendNotificationEmail(any(), any(), any(), any());
    }

    @Test
    @DisplayName("workspace invite repair skips save when actor lookup is blank")
    void getByRecipient_workspaceInviteRepairSkipsWhenActorMissing() {
        Notification invite = Notification.builder()
                .id(3L)
                .recipientId(2L)
                .actorId(9L)
                .type(NotificationType.WORKSPACE_INVITE)
                .message("FlowBoard User invited you to join workspace 'Dev Team' as MEMBER.")
                .createdAt(LocalDateTime.now())
                .build();
        AuthUserLookupResponse actor = new AuthUserLookupResponse();
        actor.setFullName(" ");

        when(repository.findByRecipientIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(invite));
        when(restTemplate.getForEntity(anyString(), eq(AuthUserLookupResponse.class), eq(9L)))
                .thenReturn(ResponseEntity.ok(actor));

        List<NotificationResponse> responses = notificationService.getByRecipient(2L);

        assertThat(responses).hasSize(1);
        verify(repository, never()).save(any());
    }
}
