package com.fcstreamtube.interfaces.advice;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fcstreamtube.domain.exceptions.EmailAlreadyExistsException;
import com.fcstreamtube.domain.exceptions.InvalidCredentialsException;
import com.fcstreamtube.domain.exceptions.InvalidTokenException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser
@Import(GlobalExceptionHandlerIT.StubController.class)
class GlobalExceptionHandlerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    MockMvc mockMvc;

    private Logger handlerLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUpLogCapture() {
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        handlerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDownLogCapture() {
        handlerLogger.detachAppender(logAppender);
    }

    @Test
    void givenInvalidBody_whenPosted_thenReturns400WithInvalidFields() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.detail").value("Request body validation failed"))
                .andExpect(jsonPath("$.invalidFields.name").value("must not be blank"));
    }

    @Test
    void givenDuplicateEmail_whenThrown_thenReturns409WithDetail() throws Exception {
        mockMvc.perform(post("/test/email-exists"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Email Already Exists"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void givenInvalidCredentials_whenThrown_thenReturns401WithDetail() throws Exception {
        mockMvc.perform(post("/test/invalid-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Invalid Credentials"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void givenInvalidToken_whenThrown_thenReturns410WithDetail() throws Exception {
        mockMvc.perform(post("/test/invalid-token"))
                .andExpect(status().isGone())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.title").value("Invalid Token"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void givenUnhandledException_whenThrown_thenReturns500WithProblemDetail() throws Exception {
        mockMvc.perform(post("/test/generic-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void givenUnhandledException_whenThrown_thenLogsErrorWithRequestUri() throws Exception {
        mockMvc.perform(post("/test/generic-error"));

        assertThat(logAppender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage())
                    .contains("uri=/test/generic-error")
                    .contains("Unexpected internal error");
        });
    }

    @Test
    void givenAccessDeniedException_whenThrown_thenReturns403WithProblemDetail() throws Exception {
        mockMvc.perform(post("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("Access denied"));
    }

    @Test
    void givenAccessDeniedException_whenThrown_thenLogsWarnWithRequestUri() throws Exception {
        mockMvc.perform(post("/test/access-denied"));

        assertThat(logAppender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains("uri=/test/access-denied");
        });
    }

    @RestController
    static class StubController {

        record TestRequest(@NotBlank String name) {}

        @PostMapping("/test/validation")
        void validateBody(@Valid @RequestBody TestRequest body) {}

        @PostMapping("/test/email-exists")
        void throwEmailAlreadyExists() {
            throw new EmailAlreadyExistsException("test@example.com already exists");
        }

        @PostMapping("/test/invalid-credentials")
        void throwInvalidCredentials() {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        @PostMapping("/test/invalid-token")
        void throwInvalidToken() {
            throw new InvalidTokenException("Token not found or expired");
        }

        @PostMapping("/test/generic-error")
        void throwGenericException() {
            throw new RuntimeException("Unexpected internal error");
        }

        @PostMapping("/test/access-denied")
        void throwAccessDenied() {
            throw new AccessDeniedException("Access denied");
        }
    }
}
