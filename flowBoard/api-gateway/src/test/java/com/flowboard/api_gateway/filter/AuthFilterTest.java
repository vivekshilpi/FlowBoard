package com.flowboard.api_gateway.filter;

import com.flowboard.api_gateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFilterTest {

    private final StubJwtUtil jwtUtil = new StubJwtUtil();
    private AuthFilter authFilter;

    @BeforeEach
    void setUp() {
        authFilter = new AuthFilter();
        ReflectionTestUtils.setField(authFilter, "jwtUtil", jwtUtil);
        jwtUtil.reset();
    }

    @Test
    void shouldAllowOptionsRequestsWithoutJwtValidation() {
        GatewayFilter filter = authFilter.apply(configWithExcludedPaths(""));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/v1/cards")
                        .build()
        );
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isTrue();
        assertThat(jwtUtil.validatedToken).isNull();
    }

    @Test
    void shouldAllowExcludedPathWithoutToken() {
        GatewayFilter filter = authFilter.apply(configWithExcludedPaths("/api/v1/auth/login,/api/v1/boards/public/**"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/boards/public/123")
                        .build()
        );
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isTrue();
        assertThat(jwtUtil.validatedToken).isNull();
    }

    @Test
    void shouldRejectRequestWhenTokenIsMissing() {
        GatewayFilter filter = authFilter.apply(configWithExcludedPaths(""));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cards")
                        .build()
        );
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString()).isEqualTo("application/json");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("Authorization header is missing");
    }

    @Test
    void shouldRejectRequestWhenTokenIsInvalid() {
        jwtUtil.valid = false;

        GatewayFilter filter = authFilter.apply(configWithExcludedPaths(""));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cards")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                        .build()
        );
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("Invalid or expired token");
    }

    @Test
    void shouldForwardUserHeadersWhenBearerTokenIsValid() {
        jwtUtil.valid = true;
        jwtUtil.email = "user@example.com";
        jwtUtil.userId = 42L;
        jwtUtil.role = "ADMIN";

        GatewayFilter filter = authFilter.apply(configWithExcludedPaths(""));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cards")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                        .build()
        );
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isTrue();
        ServerWebExchange forwardedExchange = chain.getCapturedExchange();
        assertThat(forwardedExchange.getRequest().getHeaders().getFirst("X-User-Email"))
                .isEqualTo("user@example.com");
        assertThat(forwardedExchange.getRequest().getHeaders().getFirst("X-User-Id"))
                .isEqualTo("42");
        assertThat(forwardedExchange.getRequest().getHeaders().getFirst("X-User-Role"))
                .isEqualTo("ADMIN");
    }

    @Test
    void shouldAcceptQueryParamTokenWhenAuthorizationHeaderIsMissing() {
        jwtUtil.valid = true;
        jwtUtil.email = "member@example.com";
        jwtUtil.userId = 99L;
        jwtUtil.role = "MEMBER";

        GatewayFilter filter = authFilter.apply(configWithExcludedPaths(""));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/payments/plans?access_token=query-token")
                        .build()
        );
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isTrue();
        assertThat(chain.getCapturedExchange().getRequest().getHeaders().getFirst("X-User-Email"))
                .isEqualTo("member@example.com");
    }

    private AuthFilter.Config configWithExcludedPaths(String excludedPaths) {
        AuthFilter.Config config = new AuthFilter.Config();
        config.setExcludedPaths(excludedPaths);
        return config;
    }

    private static final class CapturingGatewayFilterChain implements GatewayFilterChain {

        private ServerWebExchange capturedExchange;
        private boolean called;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.called = true;
            this.capturedExchange = exchange;
            return Mono.empty();
        }

        boolean wasCalled() {
            return called;
        }

        ServerWebExchange getCapturedExchange() {
            return capturedExchange;
        }
    }

    private static final class StubJwtUtil extends JwtUtil {
        private boolean valid;
        private String email = "user@example.com";
        private Long userId = 1L;
        private String role = "MEMBER";
        private String validatedToken;

        @Override
        public boolean isTokenValid(String token) {
            this.validatedToken = token;
            return valid;
        }

        @Override
        public String extractEmail(String token) {
            return email;
        }

        @Override
        public Long extractUserId(String token) {
            return userId;
        }

        @Override
        public String extractRole(String token) {
            return role;
        }

        void reset() {
            valid = false;
            email = "user@example.com";
            userId = 1L;
            role = "MEMBER";
            validatedToken = null;
        }
    }
}
