package com.flowBoard.auth_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigTest {

    @Test
    void openApi_containsExpectedMetadata() {
        OpenAPI openAPI = new SwaggerConfig().openAPI();

        assertThat(openAPI.getInfo().getTitle()).contains("Auth Service");
        assertThat(openAPI.getInfo().getContact().getEmail()).isEqualTo("vivekshilpi1234@gmail.com");
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("Bearer Authentication");
        assertThat(openAPI.getSecurity()).hasSize(1);
    }
}
