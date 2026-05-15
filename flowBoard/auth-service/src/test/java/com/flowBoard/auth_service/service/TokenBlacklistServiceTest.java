package com.flowBoard.auth_service.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void blacklist_storesTokenWithPrefixAndTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        TokenBlacklistService service = new TokenBlacklistService(redisTemplate);

        service.blacklist("abc", 120L);

        verify(valueOperations).set("blacklist:abc", "revoked", 120L, TimeUnit.SECONDS);
    }

    @Test
    void isBlacklisted_checksRedisKey() {
        when(redisTemplate.hasKey("blacklist:abc")).thenReturn(true);
        TokenBlacklistService service = new TokenBlacklistService(redisTemplate);

        assertThat(service.isBlacklisted("abc")).isTrue();
    }
}
