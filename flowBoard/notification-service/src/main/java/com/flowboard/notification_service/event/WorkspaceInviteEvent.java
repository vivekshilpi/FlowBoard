package com.flowboard.notification_service.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceInviteEvent implements Serializable {
    private Long invitationId;
    private Long workspaceId;
    private String workspaceName;
    private String inviterName;
    private String inviterEmail;
    private String inviteeEmail;
    private Long inviteeUserId;
    private String token;
    private String role;
    private Long invitedByUserId;
    private String acceptUrl;
}
