package io.flagship.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import org.springframework.security.access.AccessDeniedException;

import io.flagship.common.exception.BusinessRuleException;
import io.flagship.common.exception.ConflictException;
import io.flagship.common.exception.GlobalExceptionHandler;
import io.flagship.common.exception.ResourceNotFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundIsMappedTo404() {
        ProblemDetail pd = handler.handleNotFound(new ResourceNotFoundException("Flag", UUID.randomUUID()));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getTitle()).isEqualTo("Resource not found");
        assertThat(pd.getProperties()).containsKey("timestamp");
    }

    @Test
    void businessRuleIsMappedTo422() {
        ProblemDetail pd = handler.handleBusinessRule(new BusinessRuleException("Rollout must be between 0 and 100"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(pd.getDetail()).contains("Rollout");
    }

    @Test
    void unexpectedErrorDoesNotLeakInternals() {
        ProblemDetail pd = handler.handleUnexpected(new RuntimeException("secret stack detail"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getDetail()).doesNotContain("secret");
    }

    @Test
    void conflictIsMappedTo409() {
        ProblemDetail pd = handler.handleConflictException(new ConflictException("duplicate"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void accessDeniedIsMappedTo403() {
        ProblemDetail pd = handler.handleAccessDenied(new AccessDeniedException("nope"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }
}
