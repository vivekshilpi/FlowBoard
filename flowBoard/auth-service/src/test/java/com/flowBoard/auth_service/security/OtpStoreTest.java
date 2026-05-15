package com.flowBoard.auth_service.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OtpStoreTest {

    @Test
    void saveVerifyExistsAndDeleteLifecycle() {
        OtpStore store = new OtpStore();

        store.save("user@example.com", "123456", 5);

        assertThat(store.exists("user@example.com")).isTrue();
        assertThat(store.verify("user@example.com", "123456")).isTrue();
        assertThat(store.verify("user@example.com", "999999")).isFalse();

        store.delete("user@example.com");
        assertThat(store.exists("user@example.com")).isFalse();
    }

    @Test
    void expiredEntriesAreRemoved() throws InterruptedException {
        OtpStore store = new OtpStore();

        store.save("expired@example.com", "111111", 0);
        Thread.sleep(5L);

        assertThat(store.exists("expired@example.com")).isFalse();
        assertThat(store.verify("expired@example.com", "111111")).isFalse();
    }
}
