package com.flowboard.workspace_service.dto;

import com.flowboard.workspace_service.enums.MemberRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InvitationDetailsResponse {
    private Long id;
    private Long workspaceId;
    private String workspaceName;
    private Long inviterId;
    private String inviterName;
    private String inviterEmail;
    private String inviteeEmail;
    private MemberRole role;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;
}
