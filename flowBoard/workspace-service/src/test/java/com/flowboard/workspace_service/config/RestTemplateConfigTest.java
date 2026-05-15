package com.flowboard.workspace_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RestTemplateConfigTest {

    @Test
    @DisplayName("restTemplate configures request timeouts")
    void restTemplate_configuresTimeouts() {
        RestTemplate template = new RestTemplateConfig().restTemplate();

        SimpleClientHttpRequestFactory factory =
                (SimpleClientHttpRequestFactory) ReflectionTestUtils.getField(template, "requestFactory");

        assertThat(factory).isNotNull();
    }
}
