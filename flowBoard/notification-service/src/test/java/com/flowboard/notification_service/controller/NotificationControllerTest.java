package com.flowboard.notification_service.controller;

import com.flowboard.notification_service.dto.NotificationResponse;
import com.flowboard.notification_service.dto.SendBulkNotificationRequest;
import com.flowboard.notification_service.dto.SendNotificationRequest;
import com.flowboard.notification_service.enums.NotificationType;
import com.flowboard.notification_service.exception.CustomException;
import com.flowboard.notification_service.service.BoardRealtimeStreamService;
import com.flowboard.notification_service.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private BoardRealtimeStreamService boardRealtimeStreamService;

    private NotificationController controller;
    private NotificationResponse response;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationService, boardRealtimeStreamService);
        response = NotificationResponse.builder()
                .id(1L)
                .recipientId(2L)
                .type(NotificationType.ASSIGNMENT)
                .title("Assigned")
                .message("Assigned to card")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("send returns created response")
    void send_returnsCreatedResponse() {
        SendNotificationRequest request = new SendNotificationRequest();
        when(notificationService.send(request)).thenReturn(response);

        ResponseEntity<NotificationResponse> result = controller.send(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    @DisplayName("sendBulk returns created response list")
    void sendBulk_returnsCreatedResponseList() {
        SendBulkNotificationRequest request = new SendBulkNotificationRequest();
        when(notificationService.sendBulk(request)).thenReturn(List.of(response));

        ResponseEntity<List<NotificationResponse>> result = controller.sendBulk(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("notification helper endpoints delegate to service")
    void helperEndpoints_delegateToService() {
        assertThat(controller.notifyAssignment(2L, 1L, 9L, "Card", "a@b.com").getBody())
                .isEqualTo("Assignment notification sent");
        verify(notificationService).notifyAssignment(2L, 1L, 9L, "Card", "a@b.com");

        assertThat(controller.notifyMention(2L, 1L, 9L, 7L, "Card").getBody())
                .isEqualTo("Mention notification sent");
        verify(notificationService).notifyMention(2L, 1L, 9L, 7L, "Card");

        assertThat(controller.notifyDueDate(2L, 9L, 7L, "Card", "2 hours").getBody())
                .isEqualTo("Due date notification sent");
        verify(notificationService).notifyDueDateApproaching(2L, 9L, 7L, "Card", "2 hours");

        assertThat(controller.notifyDone(2L, 1L, 9L, 7L, "Card").getBody())
                .isEqualTo("Done notification sent");
        verify(notificationService).notifyCardMovedToDone(2L, 1L, 9L, 7L, "Card");

        assertThat(controller.notifyReply(2L, 1L, 9L, 7L, "Card").getBody())
                .isEqualTo("Reply notification sent");
        verify(notificationService).notifyCommentReply(2L, 1L, 9L, 7L, "Card");

        assertThat(controller.notifyOverdue(2L, 9L, 7L, "Card", "2026-05-01", "user@test.com").getBody())
                .isEqualTo("Overdue notification sent");
        verify(notificationService).notifyOverdue(2L, 9L, 7L, "Card", "2026-05-01", "user@test.com");
    }

    @Test
    @DisplayName("body notification endpoints parse request map")
    void bodyNotificationEndpoints_parseRequestMap() {
        Map<String, Object> dueDateRequest = Map.of(
                "recipientId", 2,
                "cardId", 9,
                "boardId", 7,
                "cardTitle", "Card",
                "timeLeft", "tomorrow"
        );

        Map<String, Object> overdueRequest = Map.of(
                "recipientId", 2,
                "cardId", 9,
                "boardId", 7,
                "cardTitle", "Card",
                "dueDate", "2026-05-01",
                "recipientEmail", "user@test.com"
        );

        assertThat(controller.notifyDueDateBody(dueDateRequest).getBody()).isEqualTo("Due date notification sent");
        verify(notificationService).notifyDueDateApproaching(2L, 9L, 7L, "Card", "tomorrow");

        assertThat(controller.notifyOverdueBody(overdueRequest).getBody()).isEqualTo("Overdue notification sent");
        verify(notificationService).notifyOverdue(2L, 9L, 7L, "Card", "2026-05-01", "user@test.com");
    }

    @Test
    @DisplayName("retrieval endpoints require user header and delegate")
    void retrievalEndpoints_requireUserHeaderAndDelegate() {
        when(notificationService.getByRecipient(2L, 5)).thenReturn(List.of(response));
        when(notificationService.getById(1L, 2L)).thenReturn(response);
        when(notificationService.getUnreadByRecipient(2L)).thenReturn(List.of(response));
        when(notificationService.getUnreadCount(2L)).thenReturn(3L);
        when(notificationService.getByRecipientAndType(2L, NotificationType.ASSIGNMENT)).thenReturn(List.of(response));
        when(notificationService.getAll()).thenReturn(List.of(response));

        assertThat(controller.getMyNotifications(5, 2L).getBody()).hasSize(1);
        assertThat(controller.getById(1L, 2L).getBody()).isEqualTo(response);
        assertThat(controller.getUnread(2L).getBody()).hasSize(1);
        assertThat(controller.getUnreadCount(2L).getBody()).isEqualTo(3L);
        assertThat(controller.getByType(NotificationType.ASSIGNMENT, 2L).getBody()).hasSize(1);
        assertThat(controller.getAll("PLATFORM_ADMIN").getBody()).hasSize(1);

        assertThatThrownBy(() -> controller.getMyNotifications(null, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> controller.getAll("MEMBER"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("streamBoardEvents delegates to realtime stream service")
    void streamBoardEvents_delegatesToRealtimeService() {
        SseEmitter emitter = new SseEmitter();
        when(boardRealtimeStreamService.subscribe(7L, 2L)).thenReturn(emitter);

        SseEmitter result = controller.streamBoardEvents(7L, 2L);

        assertThat(result).isSameAs(emitter);
    }

    @Test
    @DisplayName("read and delete endpoints delegate to service")
    void readAndDeleteEndpoints_delegateToService() {
        when(notificationService.markAsRead(1L, 2L)).thenReturn(response);

        assertThat(controller.markAsRead(1L, 2L).getBody()).isEqualTo(response);
        assertThat(controller.patchMarkAsRead(1L, 2L).getBody()).isEqualTo(response);

        assertThat(controller.markAllAsRead(2L).getBody()).isEqualTo("All notifications marked as read");
        assertThat(controller.patchMarkAllAsRead(2L).getBody()).isEqualTo("All notifications marked as read");
        assertThat(controller.delete(1L, 2L).getBody()).isEqualTo("Notification deleted");
        assertThat(controller.deleteRead(2L).getBody()).isEqualTo("Read notifications deleted");
        assertThat(controller.deleteByRelated(7L, "CARD").getBody()).isEqualTo("Related notifications deleted");
        assertThat(controller.deleteWorkspaceInviteForRecipient(2L, 11L).getBody())
                .isEqualTo("Workspace invite notifications deleted");

        verify(notificationService, org.mockito.Mockito.times(2)).markAsRead(1L, 2L);
        verify(notificationService, org.mockito.Mockito.times(2)).markAllAsRead(2L);
        verify(notificationService).deleteNotification(1L, 2L);
        verify(notificationService).deleteReadNotifications(2L);
        verify(notificationService).deleteByRelated(7L, "CARD");
        verify(notificationService).deleteWorkspaceInviteForRecipient(2L, 11L);
    }
}
