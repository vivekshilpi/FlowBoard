package com.flowBoard.auth_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import static org.assertj.core.api.Assertions.assertThat;

class WebConfigTest {

    @Test
    void addResourceHandlers_registersAvatarLocation() {
        WebConfig webConfig = new WebConfig("uploads/avatars");
        ResourceHandlerRegistry registry = new ResourceHandlerRegistry(new StaticWebApplicationContext(), null);

        webConfig.addResourceHandlers(registry);

        assertThat(registry.hasMappingForPattern("/uploads/avatars/**")).isTrue();
    }
}
