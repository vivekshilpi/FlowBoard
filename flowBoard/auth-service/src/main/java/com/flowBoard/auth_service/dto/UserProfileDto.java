package com.flowBoard.auth_service.dto;

import com.flowBoard.auth_service.entity.ROLE;
import com.flowBoard.auth_service.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class UserProfileDto {
    private Long id;
    private String fullName;
    private String username;
    private String email;
    private String avatarUrl;
    private String bio;
    private String themePreference;
    private ROLE role;
    private boolean isActive;
    private boolean emailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserProfileDto from(User user) {
        return new UserProfileDto(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getThemePreference(),
                user.getRole(),
                user.isActive(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
