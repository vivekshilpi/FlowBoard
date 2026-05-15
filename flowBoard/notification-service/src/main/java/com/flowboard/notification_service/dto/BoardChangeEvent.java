package com.flowboard.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardChangeEvent {
    private Long boardId;
    private Long workspaceId;
    private String entityType;
    private String action;
    private Long entityId;
    private Long actorUserId;
    private LocalDateTime occurredAt;
}
