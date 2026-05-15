package com.flowboard.api_gateway.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "gtdrkguiopdfasdfghjkklzxcvbnmqwertyuil";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
    }

    @Test
    void shouldValidateAndExtractClaimsFromToken() {
        String token = createToken(Map.of("userId", 42, "role", "ADMIN"), "user@example.com");

        assertThat(jwtUtil.isTokenValid(token)).isTrue();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("user@example.com");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void shouldReturnFalseForInvalidToken() {
        assertThat(jwtUtil.isTokenValid("not-a-token")).isFalse();
    }

    @Test
    void shouldSupportLongUserIdClaims() {
        String token = createToken(Map.of("userId", 3_000_000_000L, "role", "MEMBER"), "long@example.com");

        assertThat(jwtUtil.extractUserId(token)).isEqualTo(3_000_000_000L);
    }

    @Test
    void shouldDefaultRoleToMemberWhenMissing() {
        String token = createToken(Map.of("userId", 11), "member@example.com");

        assertThat(jwtUtil.extractRole(token)).isEqualTo("MEMBER");
    }

    @Test
    void shouldThrowWhenUserIdClaimIsMissing() {
        String token = createToken(new HashMap<>(), "user@example.com");

        assertThatThrownBy(() -> jwtUtil.extractUserId(token))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("userId claim missing from token");
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }
}
