package com.flowboard.card_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardAttachmentDto {
    private String id;
    private String fileName;
    private String contentType;
    private long size;
    private String storedPath;
    private String downloadUrl;
    private String uploadedAt;
}
