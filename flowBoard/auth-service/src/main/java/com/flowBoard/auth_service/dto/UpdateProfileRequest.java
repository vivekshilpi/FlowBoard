package com.flowBoard.auth_service.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 80, message = "Full name must be at most 80 characters")
    private String fullname;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    @Pattern(
            regexp = "^[A-Za-z0-9._-]+$",
            message = "Username may only contain letters, numbers, dots, underscores, and hyphens"
    )
    private String username;

    @Size(max = 2048, message = "Avatar URL is too long")
    private String avatarUrl;

    @Size(max = 280, message = "Bio must be at most 280 characters")
    private String bio;
}
