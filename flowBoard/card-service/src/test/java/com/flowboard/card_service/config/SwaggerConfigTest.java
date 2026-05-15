package com.flowboard.card_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigTest {

    @Test
    void buildsOpenApiDefinition() {
        OpenAPI openAPI = new SwaggerConfig().openAPI();

        assertThat(openAPI.getInfo().getTitle()).contains("Card Service");
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("Bearer Authentication");
        assertThat(openAPI.getSecurity()).isNotEmpty();
    }
}
