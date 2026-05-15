package com.flowBoard.auth_service.service;

import com.flowBoard.auth_service.exception.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PasswordChangeRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<Long, Deque<Instant>> attemptsByUser = new ConcurrentHashMap<>();

    public void checkAllowed(Long userId) {
        Deque<Instant> attempts = attemptsByUser.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        Instant cutoff = Instant.now().minus(WINDOW);

        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
                attempts.removeFirst();
            }

            if (attempts.size() >= MAX_ATTEMPTS) {
                throw new CustomException(
                        "Too many password change attempts. Please wait a few minutes and try again.",
                        HttpStatus.TOO_MANY_REQUESTS
                );
            }

            attempts.addLast(Instant.now());
        }
    }
}
