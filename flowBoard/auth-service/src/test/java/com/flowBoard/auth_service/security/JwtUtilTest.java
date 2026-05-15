package com.flowBoard.auth_service.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "12345678901234567890123456789012");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 60_000L);
    }

    @Test
    void generateAndExtractTokenData() {
        String token = jwtUtil.generateToken("user@example.com", 7L, "MEMBER");

        Claims claims = jwtUtil.extractAllClaims(token);

        assertThat(claims.getSubject()).isEqualTo("user@example.com");
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("user@example.com");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(7L);
        assertThat(claims.get("role", String.class)).isEqualTo("MEMBER");
        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    void invalidTokenReturnsFalse() {
        assertThat(jwtUtil.isTokenValid("not-a-token")).isFalse();
    }
}
