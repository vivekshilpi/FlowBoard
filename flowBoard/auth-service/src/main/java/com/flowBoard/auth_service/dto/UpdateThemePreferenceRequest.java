package com.flowBoard.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateThemePreferenceRequest {

    @NotBlank(message = "Theme preference is required")
    @Pattern(regexp = "^(light|dark)$", message = "Theme preference must be either light or dark")
    private String themePreference;
}
