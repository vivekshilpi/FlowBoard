package com.flowboard.card_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AddCardCommentRequest {
    @NotBlank(message = "Comment content is required")
    private String content;

    private Long parentActivityId;

    private List<Long> mentionedUserIds;
}
