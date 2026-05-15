package com.flowBoard.auth_service.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "fromEmail", "from@example.com");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void sendVerificationOtp_sendsEmail() {
        emailService.sendVerificationOtp("to@example.com", "123456");
        verify(mailSender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    @Test
    void sendForgotPasswordOtp_sendsEmail() {
        emailService.sendForgotPasswordOtp("to@example.com", "123456");
        verify(mailSender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    @Test
    void sendAccountStatusEmail_sendsEmail() {
        emailService.sendAccountStatusEmail("to@example.com", "User", true);
        emailService.sendAccountStatusEmail("to@example.com", "User", false);
        verify(mailSender, org.mockito.Mockito.times(2)).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    @Test
    void sendReactivationOtp_sendsEmail() {
        emailService.sendReactivationOtp("to@example.com", "User", "654321");
        verify(mailSender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }
}
