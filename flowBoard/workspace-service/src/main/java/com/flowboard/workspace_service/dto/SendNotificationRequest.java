package com.flowboard.workspace_service.dto;

import lombok.Data;

@Data
public class SendNotificationRequest {
    private Long recipientId;
    private Long actorId;
    private NotificationType type;
    private String title;
    private String message;
    private Long relatedId;
    private String relatedType;
    private String deepLinkUrl;
    private boolean sendEmail;
    private String recipientEmail;
}
