package com.fcstreamtube.infrastructure.email;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, "noreply@fcstreamtube.com");
        Logger logger = (Logger) LoggerFactory.getLogger(EmailService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(EmailService.class);
        logger.detachAppender(listAppender);
    }

    @Test
    void givenConfirmationEmail_whenSendConfirmationEmail_thenMessageSentToCorrectRecipient() {
        String to = "user@example.com";
        String confirmationUrl = "http://localhost:8080/api/auth/confirm?token=abc123";

        emailService.sendConfirmationEmail(to, confirmationUrl);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly(to);
    }

    @Test
    void givenConfirmationEmail_whenSendConfirmationEmail_thenFromAddressIsSet() {
        emailService.sendConfirmationEmail("user@example.com",
                "http://localhost:8080/api/auth/confirm?token=abc123");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("noreply@fcstreamtube.com");
    }

    @Test
    void givenConfirmationEmail_whenSendConfirmationEmail_thenSubjectIsSet() {
        emailService.sendConfirmationEmail("user@example.com",
                "http://localhost:8080/api/auth/confirm?token=abc123");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Confirm your StreamTube account");
    }

    @Test
    void givenConfirmationEmail_whenSendConfirmationEmail_thenBodyContainsConfirmationUrl() {
        String to = "user@example.com";
        String confirmationUrl = "http://localhost:8080/api/auth/confirm?token=abc123";

        emailService.sendConfirmationEmail(to, confirmationUrl);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains(confirmationUrl);
    }

    @Test
    void givenSmtpFailure_whenSendConfirmationEmail_thenExceptionNotPropagated() {
        doThrow(new MailSendException("SMTP connection refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> emailService.sendConfirmationEmail("user@example.com",
                "http://localhost:8080/api/auth/confirm?token=abc123"))
                .doesNotThrowAnyException();
    }

    @Test
    void givenSmtpFailure_whenSendConfirmationEmail_thenErrorIsLogged() {
        doThrow(new MailSendException("SMTP connection refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        emailService.sendConfirmationEmail("user@example.com",
                "http://localhost:8080/api/auth/confirm?token=abc123");

        assertThat(listAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).contains("user@example.com");
                    assertThat(event.getFormattedMessage()).contains("confirmation email");
                });
    }

    @Test
    void givenPasswordResetEmail_whenSendPasswordResetEmail_thenMessageSentToCorrectRecipient() {
        String to = "user@example.com";
        String resetUrl = "http://localhost:4200/reset-password?token=xyz789";

        emailService.sendPasswordResetEmail(to, resetUrl);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly(to);
    }

    @Test
    void givenPasswordResetEmail_whenSendPasswordResetEmail_thenFromAddressIsSet() {
        emailService.sendPasswordResetEmail("user@example.com",
                "http://localhost:4200/reset-password?token=xyz789");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("noreply@fcstreamtube.com");
    }

    @Test
    void givenPasswordResetEmail_whenSendPasswordResetEmail_thenSubjectIsSet() {
        emailService.sendPasswordResetEmail("user@example.com",
                "http://localhost:4200/reset-password?token=xyz789");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Reset your StreamTube password");
    }

    @Test
    void givenPasswordResetEmail_whenSendPasswordResetEmail_thenBodyContainsResetUrl() {
        String to = "user@example.com";
        String resetUrl = "http://localhost:4200/reset-password?token=xyz789";

        emailService.sendPasswordResetEmail(to, resetUrl);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains(resetUrl);
    }

    @Test
    void givenSmtpFailure_whenSendPasswordResetEmail_thenExceptionNotPropagated() {
        doThrow(new MailSendException("SMTP connection refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> emailService.sendPasswordResetEmail("user@example.com",
                "http://localhost:4200/reset-password?token=xyz789"))
                .doesNotThrowAnyException();
    }

    @Test
    void givenSmtpFailure_whenSendPasswordResetEmail_thenErrorIsLogged() {
        doThrow(new MailSendException("SMTP connection refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        emailService.sendPasswordResetEmail("user@example.com",
                "http://localhost:4200/reset-password?token=xyz789");

        assertThat(listAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).contains("user@example.com");
                    assertThat(event.getFormattedMessage()).contains("password reset email");
                });
    }
}
