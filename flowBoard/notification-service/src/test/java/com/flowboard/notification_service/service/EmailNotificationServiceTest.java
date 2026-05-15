package com.flowboard.notification_service.service;

import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailNotificationService service;
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        service = new EmailNotificationService(mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", "system@flowboard.test");
        ReflectionTestUtils.setField(service, "fromName", "FlowBoard Notifications");
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:4200");
        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    @DisplayName("sendNotificationEmail builds rich HTML email with normalized deep link")
    void sendNotificationEmail_buildsHtmlEmail() throws Exception {
        service.sendNotificationEmail("user@test.com", "Assigned", "Check this", "board/7?cardId=9");

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("FlowBoard -- Assigned");
        assertThat(((InternetAddress) mimeMessage.getFrom()[0]).getAddress()).isEqualTo("system@flowboard.test");
        assertThat(((InternetAddress) mimeMessage.getRecipients(Message.RecipientType.TO)[0]).getAddress())
                .isEqualTo("user@test.com");
        String content = firstHtmlBody(mimeMessage);
        assertThat(content).contains("View in FlowBoard");
        assertThat(content).contains("http://localhost:4200/board/7?cardId=9");
    }

    @Test
    @DisplayName("sendNotificationEmail supports reply-to and absolute URLs")
    void sendNotificationEmail_supportsReplyToAndAbsoluteUrl() throws Exception {
        service.sendNotificationEmail("user@test.com", "Assigned", "Check this", "https://flowboard.app/path", "reply@test.com");

        verify(mailSender).send(mimeMessage);
        assertThat(((InternetAddress[]) mimeMessage.getReplyTo())[0].getAddress()).isEqualTo("reply@test.com");
        assertThat(firstHtmlBody(mimeMessage)).contains("https://flowboard.app/path");
    }

    @Test
    @DisplayName("sendNotificationEmail swallows sender failures")
    void sendNotificationEmail_swallowsFailures() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("mail unavailable"));

        service.sendNotificationEmail("user@test.com", "Assigned", "Check this", null);
    }

    @Test
    @DisplayName("sendWorkspaceInviteEmail uses inviter as sender when possible")
    void sendWorkspaceInviteEmail_usesInviterSender() throws Exception {
        service.sendWorkspaceInviteEmail("join@test.com", "Dev Team", "MEMBER", "http://invite", "Vivek", "vivek@test.com");

        verify(mailSender).send(mimeMessage);
        assertThat(((InternetAddress) mimeMessage.getFrom()[0]).getAddress()).isEqualTo("vivek@test.com");
        assertThat(((InternetAddress[]) mimeMessage.getReplyTo())[0].getAddress()).isEqualTo("vivek@test.com");
        assertThat(firstHtmlBody(mimeMessage)).contains("Accept invitation");
    }

    @Test
    @DisplayName("sendWorkspaceInviteEmail falls back to system sender when custom sender fails")
    void sendWorkspaceInviteEmail_fallsBackToSystemSender() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage, new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new RuntimeException("smtp")).doNothing().when(mailSender).send(any(MimeMessage.class));

        service.sendWorkspaceInviteEmail("join@test.com", "Dev Team", "MEMBER", "http://invite", " ", " ");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, org.mockito.Mockito.times(2)).send(captor.capture());
    }

    @Test
    @DisplayName("assignment and overdue email helpers delegate to notification sender")
    void helperEmails_delegateToNotificationSender() throws Exception {
        MimeMessage second = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage, second);

        service.sendAssignmentEmail("user@test.com", "Card", "Admin", "/board/5");
        service.sendOverdueEmail("user@test.com", "Card", "2026-05-01");

        verify(mailSender, org.mockito.Mockito.times(2)).send(any(MimeMessage.class));
    }

    private String firstHtmlBody(MimeMessage message) throws Exception {
        return extractText(message.getContent());
    }

    private String extractText(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        Multipart multipart = (Multipart) content;
        return extractText(multipart.getBodyPart(0).getContent());
    }
}
