package com.flowBoard.auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {
    private long totalUsers;
    private long totalWorkspaces;
    private long totalBoards;
    private long activeUsersToday;
    private long activeUsers;
    private long suspendedUsers;
    private long platformAdmins;
    private long members;
    private long publicWorkspaces;
    private long privateWorkspaces;
    private long publicBoards;
    private long privateBoards;
    private long openBoards;
    private long closedBoards;
}
