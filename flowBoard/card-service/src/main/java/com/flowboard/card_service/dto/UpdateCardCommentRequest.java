package com.flowboard.card_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateCardCommentRequest {
    @NotBlank(message = "Comment content is required")
    private String content;
}
