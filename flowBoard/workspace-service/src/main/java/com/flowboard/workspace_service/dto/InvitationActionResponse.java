package com.flowboard.workspace_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvitationActionResponse {
    private Long invitationId;
    private Long workspaceId;
    private String workspaceName;
    private String status;
    private String message;
}
