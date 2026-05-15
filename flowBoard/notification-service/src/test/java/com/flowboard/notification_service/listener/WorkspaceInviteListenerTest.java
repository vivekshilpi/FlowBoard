package com.flowboard.notification_service.listener;

import com.flowboard.notification_service.dto.SendNotificationRequest;
import com.flowboard.notification_service.enums.NotificationType;
import com.flowboard.notification_service.event.WorkspaceInviteEvent;
import com.flowboard.notification_service.service.EmailNotificationService;
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
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WorkspaceInviteListenerTest {

    @Mock
    private EmailNotificationService emailService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private WorkspaceInviteListener listener;

    @Test
    @DisplayName("handleWorkspaceInvite sends email and creates in-app notification for existing user")
    void handleWorkspaceInvite_sendsEmailAndCreatesNotification() {
        WorkspaceInviteEvent event = new WorkspaceInviteEvent(
                5L, 9L, "Dev Team", "Vivek", "vivek@test.com",
                "join@test.com", 22L, "token", "MEMBER", 1L, "/accept/5"
        );

        listener.handleWorkspaceInvite(event);

        verify(emailService).sendWorkspaceInviteEmail(
                "join@test.com", "Dev Team", "MEMBER", "/accept/5", "Vivek", "vivek@test.com");

        ArgumentCaptor<SendNotificationRequest> captor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService).send(captor.capture());

        SendNotificationRequest request = captor.getValue();
        assertThat(request.getRecipientId()).isEqualTo(22L);
        assertThat(request.getActorId()).isEqualTo(1L);
        assertThat(request.getType()).isEqualTo(NotificationType.WORKSPACE_INVITE);
        assertThat(request.getRelatedId()).isEqualTo(9L);
        assertThat(request.getRelatedType()).isEqualTo("WORKSPACE");
        assertThat(request.getDeepLinkUrl()).isEqualTo("/invitations/5");
        assertThat(request.isSendEmail()).isFalse();
    }

    @Test
    @DisplayName("handleWorkspaceInvite skips in-app notification when invitee has no account")
    void handleWorkspaceInvite_skipsInAppNotificationWithoutUserId() {
        WorkspaceInviteEvent event = new WorkspaceInviteEvent(
                5L, 9L, "Dev Team", "Vivek", "vivek@test.com",
                "join@test.com", null, "token", "MEMBER", 1L, "/accept/5"
        );

        listener.handleWorkspaceInvite(event);

        verify(emailService).sendWorkspaceInviteEmail(
                "join@test.com", "Dev Team", "MEMBER", "/accept/5", "Vivek", "vivek@test.com");
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("handleWorkspaceInvite swallows email failures")
    void handleWorkspaceInvite_swallowsFailures() {
        WorkspaceInviteEvent event = new WorkspaceInviteEvent(
                5L, 9L, "Dev Team", "Vivek", "vivek@test.com",
                "join@test.com", 22L, "token", "MEMBER", 1L, "/accept/5"
        );
        doThrow(new RuntimeException("boom")).when(emailService)
                .sendWorkspaceInviteEmail("join@test.com", "Dev Team", "MEMBER", "/accept/5", "Vivek", "vivek@test.com");

        listener.handleWorkspaceInvite(event);

        verifyNoInteractions(notificationService);
    }
}
