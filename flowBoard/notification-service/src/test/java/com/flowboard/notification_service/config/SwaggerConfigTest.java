package com.flowboard.notification_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigTest {

    @Test
    @DisplayName("openAPI describes FlowBoard notification API")
    void openApi_describesNotificationApi() {
        OpenAPI openAPI = new SwaggerConfig().openAPI();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("FlowBoard — Notification Service API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v1.0.0");
        assertThat(openAPI.getInfo().getContact().getName()).isEqualTo("Vivek Shilpi");
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("Bearer Authentication");
        assertThat(openAPI.getSecurity()).hasSize(1);
    }
}
