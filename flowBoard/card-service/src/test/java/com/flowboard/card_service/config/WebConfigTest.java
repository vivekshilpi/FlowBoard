package com.flowboard.card_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.assertj.core.api.Assertions.assertThat;

class WebConfigTest {

    @Test
    void addsCorsMappingForCardApi() {
        WebConfig config = new WebConfig();
        CorsRegistry registry = new CorsRegistry();

        config.addCorsMappings(registry);
        CorsRegistration registration = registry.addMapping("/test");

        assertThat(registration).isNotNull();
    }
}
