package com.flowBoard.auth_service.security;

import com.flowBoard.auth_service.exception.CustomException;
import com.flowBoard.auth_service.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpStore otpStore;

    @Mock
    private EmailService emailService;

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(otpStore, emailService);
        ReflectionTestUtils.setField(otpService, "expiryMinutes", 10);
    }

    @Test
    void sendVerificationOtp_savesAndEmails() {
        otpService.sendVerificationOtp("user@example.com");

        verify(otpStore).save(anyString(), anyString(), anyInt());
        verify(emailService).sendVerificationOtp(anyString(), anyString());
    }

    @Test
    void sendForgotPasswordOtp_savesAndEmails() {
        otpService.sendForgotPasswordOtp("user@example.com");

        verify(otpStore).save(anyString(), anyString(), anyInt());
        verify(emailService).sendForgotPasswordOtp(anyString(), anyString());
    }

    @Test
    void sendReactivationOtp_savesAndEmails() {
        otpService.sendReactivationOtp("user@example.com", "User");

        verify(otpStore).save(anyString(), anyString(), anyInt());
        verify(emailService).sendReactivationOtp(anyString(), anyString(), anyString());
    }

    @Test
    void verifyOtp_successDeletesOtp() {
        when(otpStore.verify("user@example.com", "123456")).thenReturn(true);

        otpService.verifyOtp("user@example.com", "123456");

        verify(otpStore).delete("user@example.com");
    }

    @Test
    void verifyOtp_invalidThrowsBadRequest() {
        when(otpStore.verify("user@example.com", "123456")).thenReturn(false);

        assertThatThrownBy(() -> otpService.verifyOtp("user@example.com", "123456"))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void hasActiveOtp_delegatesToStore() {
        doReturn(true).when(otpStore).exists("user@example.com");

        assertThat(otpService.hasActiveOtp("user@example.com")).isTrue();
    }
}
