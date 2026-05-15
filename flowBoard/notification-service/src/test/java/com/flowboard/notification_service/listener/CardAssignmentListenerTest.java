package com.flowboard.notification_service.listener;

import com.flowboard.notification_service.dto.SendNotificationRequest;
import com.flowboard.notification_service.enums.NotificationType;
import com.flowboard.notification_service.event.CardAssignedEvent;
import com.flowboard.notification_service.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CardAssignmentListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CardAssignmentListener listener;

    @Test
    @DisplayName("handleCardAssigned builds assignment notification with email")
    void handleCardAssigned_buildsRequestWithEmail() {
        CardAssignedEvent event = new CardAssignedEvent(9L, 7L, "Card", 2L, 1L, "user@test.com", "Admin");

        listener.handleCardAssigned(event);

        ArgumentCaptor<SendNotificationRequest> captor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService).send(captor.capture());

        SendNotificationRequest request = captor.getValue();
        assertThat(request.getRecipientId()).isEqualTo(2L);
        assertThat(request.getActorId()).isEqualTo(1L);
        assertThat(request.getType()).isEqualTo(NotificationType.ASSIGNMENT);
        assertThat(request.getDeepLinkUrl()).isEqualTo("/board/7?cardId=9");
        assertThat(request.isSendEmail()).isTrue();
        assertThat(request.getRecipientEmail()).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("handleCardAssigned still sends notification without email")
    void handleCardAssigned_buildsRequestWithoutEmail() {
        CardAssignedEvent event = new CardAssignedEvent(9L, 7L, "Card", 2L, 1L, null, "Admin");

        listener.handleCardAssigned(event);

        ArgumentCaptor<SendNotificationRequest> captor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService).send(captor.capture());
        assertThat(captor.getValue().isSendEmail()).isFalse();
        assertThat(captor.getValue().getRecipientEmail()).isNull();
    }

    @Test
    @DisplayName("handleCardAssigned swallows downstream failures")
    void handleCardAssigned_swallowsFailures() {
        CardAssignedEvent event = new CardAssignedEvent(9L, 7L, "Card", 2L, 1L, null, "Admin");
        doThrow(new RuntimeException("boom")).when(notificationService).send(org.mockito.ArgumentMatchers.any());

        listener.handleCardAssigned(event);

        verify(notificationService).send(org.mockito.ArgumentMatchers.any());
    }
}
