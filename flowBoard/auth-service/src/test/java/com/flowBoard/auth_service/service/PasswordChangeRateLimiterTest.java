package com.flowBoard.auth_service.service;

import com.flowBoard.auth_service.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordChangeRateLimiterTest {

    @Test
    void allowsFiveAttemptsButBlocksSixth() {
        PasswordChangeRateLimiter limiter = new PasswordChangeRateLimiter();

        for (int i = 0; i < 5; i++) {
            limiter.checkAllowed(1L);
        }

        assertThatThrownBy(() -> limiter.checkAllowed(1L))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
