package com.flowboard.notification_service.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.mail.from-name:FlowBoard Notifications}")
    private String fromName;

    @Value("${app.frontend-base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    @Async
    public void sendNotificationEmail(String toEmail,
                                      String title,
                                      String message,
                                      String deepLinkUrl){
        sendNotificationEmail(toEmail, title, message, deepLinkUrl, null);
    }

    @Async
    public void sendNotificationEmail(String toEmail,
                                      String title,
                                      String message,
                                      String deepLinkUrl,
                                      String replyToEmail){
        try{
            log.info("Sending notification email to: {}", toEmail);
            MimeMessage mail = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mail, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            if (replyToEmail != null && !replyToEmail.isBlank()) {
                helper.setReplyTo(replyToEmail);
            }
            helper.setSubject("FlowBoard -- "+title);
            helper.setText(buildEmailBody(title, message, deepLinkUrl), true);

            mailSender.send(mail);
            log.info("Notification email sent to: {}",toEmail);

        } catch(MessagingException e){
                log.error("Failed to send notification email to {} : {}",
                        toEmail, e.getMessage(), e);
        } catch (Exception e) {
                log.error("Unexpected failure while sending notification email to {} : {}",
                        toEmail, e.getMessage(), e);
        }
    }

    @Async
    public void sendWorkspaceInviteEmail(String toEmail,
                                         String workspaceName,
                                         String role,
                                         String acceptUrl,
                                         String inviterName,
                                         String inviterEmail) {
        String safeInviterName = inviterName == null || inviterName.isBlank()
                ? "FlowBoard User"
                : inviterName.trim();
        String safeInviterEmail = inviterEmail == null || inviterEmail.isBlank()
                ? fromEmail
                : inviterEmail.trim();
        String title = "Workspace invitation";
        String message = safeInviterName + " (" + safeInviterEmail + ") invited you to join '"
                + workspaceName + "' as " + role + ". This invitation expires in 7 days.";

        try {
            log.info("Sending workspace invite email to: {} from inviter: {}", toEmail, safeInviterEmail);
            MimeMessage mail = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mail, true, "UTF-8");

            helper.setFrom(new InternetAddress(safeInviterEmail, safeInviterName));
            mail.setSender(new InternetAddress(fromEmail, fromName));
            helper.setTo(toEmail);
            helper.setReplyTo(safeInviterEmail);
            helper.setSubject("FlowBoard -- " + title);
            helper.setText(buildWorkspaceInviteEmailBody(
                    workspaceName,
                    role,
                    acceptUrl,
                    safeInviterName,
                    safeInviterEmail
            ), true);

            mailSender.send(mail);
            log.info("Workspace invite email sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Invite email with inviter sender failed; retrying with system sender for {}: {}",
                    toEmail, e.getMessage());
            sendNotificationEmail(toEmail, title, message, acceptUrl, safeInviterEmail);
        }
    }

    @Async
    public void sendAssignmentEmail(String toEmail,
                                    String cardTitle,
                                    String assignedBy,
                                    String deepLinkUrl){
        String title = "You have been assigned a card";
        String message = assignedBy +" assigned you to '"
                +cardTitle+ "'. Click below to view ";
        sendNotificationEmail(toEmail, title, message, deepLinkUrl);
    }

    @Async
    public void sendOverdueEmail(String toEmail,
                                 String cardTitle,
                                 String dueDate){
        String title = "Overdue card - action is required";
        String message = "The card '"+cardTitle+"' was due on "+dueDate
                +" and has not been marked as done.";

        sendNotificationEmail(toEmail, title, message, null);
    }

    private String buildEmailBody(String title, String message, String deepLinkUrl){
        String resolvedLink = resolveEmailLink(deepLinkUrl);

        String linkHtml = (resolvedLink != null && !resolvedLink.isBlank())
                ? """
      <div style="margin:24px 0;text-align:center;">
        <a href="%s"
           style="background:#4f46e5;color:#fff;padding:12px 28px;
                  border-radius:6px;text-decoration:none;
                  font-weight:500;font-size:14px;">
          View in FlowBoard
        </a>
      </div>
      """.formatted(resolvedLink)
                : "";

        return """
                <div style="font-family:Arial,sans-serif;max-width:520px;
                           margin:auto;padding:32px;
                           border:1px solid #e0e0e0;border-radius:8px;">
                 <h2 style="color:#1a1a2e;margin-bottom:8px;">
                   FlowBoard Notification
                 </h2>
                 <h3 style="color:#4f46e5;margin-bottom:16px;">%s</h3>
                 <p style="color:#444;font-size:15px;line-height:1.6;">%s</p>
                 %s
                 <hr style="border:none;border-top:1px solid #eee;
                            margin:24px 0;">
                 <p style="color:#aaa;font-size:12px;">
                   FlowBoard — Organise Work. Collaborate Seamlessly.
                 </p>
               </div>
               """.formatted(title, message, linkHtml);
    }

    private String resolveEmailLink(String deepLinkUrl) {
        if (deepLinkUrl == null || deepLinkUrl.isBlank()) {
            return null;
        }

        if (deepLinkUrl.startsWith("http://") || deepLinkUrl.startsWith("https://")) {
            return deepLinkUrl;
        }

        String normalizedBase = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        String normalizedPath = deepLinkUrl.startsWith("/") ? deepLinkUrl : "/" + deepLinkUrl;
        return normalizedBase + normalizedPath;
    }

    private String buildWorkspaceInviteEmailBody(String workspaceName,
                                                 String role,
                                                 String acceptUrl,
                                                 String inviterName,
                                                 String inviterEmail) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:560px;
                           margin:auto;padding:32px;
                           border:1px solid #e0e0e0;border-radius:8px;">
                 <h2 style="color:#1a1a2e;margin-bottom:8px;">
                   Workspace invitation
                 </h2>
                 <p style="color:#444;font-size:15px;line-height:1.7;">
                   <strong>%s</strong> (%s) invited you to join
                   <strong>%s</strong> as <strong>%s</strong>.
                 </p>
                 <div style="margin:24px 0;text-align:center;">
                   <a href="%s"
                      style="background:#4f46e5;color:#fff;padding:12px 28px;
                             border-radius:6px;text-decoration:none;
                             font-weight:500;font-size:14px;">
                     Accept invitation
                   </a>
                 </div>
                 <p style="color:#666;font-size:13px;line-height:1.6;">
                   This invitation expires in 7 days. You can reply directly to this email to contact %s.
                 </p>
                 <hr style="border:none;border-top:1px solid #eee;
                            margin:24px 0;">
                 <p style="color:#aaa;font-size:12px;">
                   Sent via FlowBoard Notifications
                 </p>
               </div>
               """.formatted(inviterName, inviterEmail, workspaceName, role, acceptUrl, inviterName);
    }
}
