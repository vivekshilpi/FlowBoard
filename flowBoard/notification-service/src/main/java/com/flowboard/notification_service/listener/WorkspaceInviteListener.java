package com.flowboard.notification_service.listener;

import com.flowboard.notification_service.config.RabbitMQConfig;
import com.flowboard.notification_service.dto.SendNotificationRequest;
import com.flowboard.notification_service.enums.NotificationType;
import com.flowboard.notification_service.event.WorkspaceInviteEvent;
import com.flowboard.notification_service.service.EmailNotificationService;
import com.flowboard.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkspaceInviteListener {

    private final EmailNotificationService emailService;
    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.INVITE_QUEUE)
    public void handleWorkspaceInvite(WorkspaceInviteEvent event) {
        log.info("Received WorkspaceInviteEvent: workspaceId={} invitee={}",
                event.getWorkspaceId(), event.getInviteeEmail());

        try {
            emailService.sendWorkspaceInviteEmail(
                    event.getInviteeEmail(),
                    event.getWorkspaceName(),
                    event.getRole(),
                    event.getAcceptUrl(),
                    event.getInviterName(),
                    event.getInviterEmail()
            );

            log.info("Invite email sent to {}", event.getInviteeEmail());
            createInAppNotification(event);

        } catch (Exception e) {
            log.error("Failed to send invite email to {}: {}",
                    event.getInviteeEmail(), e.getMessage());
        }
    }

    private void createInAppNotification(WorkspaceInviteEvent event) {
        if (event.getInviteeUserId() == null) {
            log.info("Invitee {} has no FlowBoard account yet; skipping in-app notification",
                    event.getInviteeEmail());
            return;
        }

        SendNotificationRequest request = new SendNotificationRequest();
        request.setRecipientId(event.getInviteeUserId());
        request.setActorId(event.getInvitedByUserId());
        request.setType(NotificationType.WORKSPACE_INVITE);
        request.setTitle("Workspace invitation");
        request.setMessage(event.getInviterName() + " invited you to join workspace '"
                + event.getWorkspaceName() + "' as " + event.getRole() + ".");
        request.setRelatedId(event.getWorkspaceId());
        request.setRelatedType("WORKSPACE");
        request.setDeepLinkUrl("/invitations/" + event.getInvitationId());
        request.setSendEmail(false);
        notificationService.send(request);

        log.info("In-app workspace invite notification created for userId={}",
                event.getInviteeUserId());
    }
}
