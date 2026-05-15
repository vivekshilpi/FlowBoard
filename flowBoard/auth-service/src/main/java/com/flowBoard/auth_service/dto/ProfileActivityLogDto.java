package com.flowBoard.auth_service.dto;

import com.flowBoard.auth_service.entity.ProfileActivityLog;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ProfileActivityLogDto {
    private Long id;
    private String action;
    private String summary;
    private LocalDateTime createdAt;

    public static ProfileActivityLogDto from(ProfileActivityLog activityLog) {
        return new ProfileActivityLogDto(
                activityLog.getId(),
                activityLog.getAction(),
                activityLog.getSummary(),
                activityLog.getCreatedAt()
        );
    }
}
