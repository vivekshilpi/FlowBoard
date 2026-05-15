package com.flowboard.api_gateway.filter;

import com.flowboard.api_gateway.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.time.Instant;

@Component
@Slf4j
public class AuthFilter extends AbstractGatewayFilterFactory<AuthFilter.Config> {

    @Autowired
    private JwtUtil jwtUtil;

    public AuthFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // Allow CORS preflight requests to pass through untouched.
            if (request.getMethod().name().equals("OPTIONS")) {
                return chain.filter(exchange);
            }

            String path = request.getURI().getPath();

            // Skip JWT enforcement for routes that are intentionally public.
            if (isExcluded(path, config.getExcludedPaths())) {
                log.debug("Public routes - skipping JWT: {}", path);
                return chain.filter(exchange);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            String token;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            } else {
                token = request.getQueryParams().getFirst("access_token");
            }

            if (token == null || token.isBlank()) {
                log.warn("No bearer token found - blocking request to: {}", path);
                return reject(exchange, "Authorization header is missing", HttpStatus.UNAUTHORIZED);
            }

            if (!jwtUtil.isTokenValid(token)) {
                log.warn("Invalid or expired JWT - blocking request to: {}", path);
                return reject(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
            }

            // ---- extract claims and forward as headers ----
            String email = jwtUtil.extractEmail(token);
            Long userId = jwtUtil.extractUserId(token);
            String role = jwtUtil.extractRole(token);

            log.debug("JWT valid -> email={} userId={} role={} path={}",
                    email, userId, role, path);


            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Email", email)
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-User-Role", role)
                    //.headers(h->h.remove(HttpHeaders.AUTHORIZATION))
                    .build();

            log.debug("JWT valid: email={} userId={} path={}", email, userId, path);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    private boolean isExcluded(String path, String excludedPaths) {
        if (excludedPaths == null || excludedPaths.isBlank()) return false;
        List<String> excluded = Arrays.stream(excludedPaths.split(","))
                .map(String::trim).toList();
        return excluded.stream().anyMatch(pattern -> matchesExcludedPattern(path, pattern));
    }

    private boolean matchesExcludedPattern(String path, String pattern) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        return path.equals(pattern);
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"timestamp":"%s","status":%d,"error":"%s","message":"%s"}
                """.formatted(Instant.now(), status.value(), status.getReasonPhrase(), message);
        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(body.getBytes()))
        );
    }

    public static class Config {
        private String excludedPaths;
        public String getExcludedPaths() { return excludedPaths; }
        public void setExcludedPaths(String excludedPaths) { this.excludedPaths = excludedPaths; }
    }
}
