package com.fcstreamtube.infrastructure.email;

import com.fcstreamtube.application.ports.out.EmailGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from:noreply@fcstreamtube.com}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Async
    @Override
    public void sendConfirmationEmail(String to, String confirmationUrl) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("Confirm your StreamTube account");
            message.setText("Click the link below to confirm your account:\n\n" + confirmationUrl);
            mailSender.send(message);
            log.info("Email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send confirmation email to {}: {}", to, e.getMessage(), e);
        }
    }

    @Async
    @Override
    public void sendPasswordResetEmail(String to, String resetUrl) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("Reset your StreamTube password");
            message.setText("Click the link below to reset your password:\n\n" + resetUrl);
            mailSender.send(message);
            log.info("Email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", to, e.getMessage(), e);
        }
    }
}
