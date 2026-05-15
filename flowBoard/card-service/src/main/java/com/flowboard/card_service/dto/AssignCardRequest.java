package com.flowboard.card_service.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class AssignCardRequest {
    @PositiveOrZero(message = "Assignee ID must be zero or greater")
    private Long assigneeId;
}
