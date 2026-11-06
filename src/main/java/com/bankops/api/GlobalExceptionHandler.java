package com.bankops.api;

import com.bankops.application.ResourceNotFoundException;
import com.bankops.application.ai.AiAnalysisFailedException;
import com.bankops.application.ai.AiUnavailableException;
import com.bankops.application.ai.RateLimitExceededException;
import com.bankops.application.auth.InvalidCredentialsException;
import com.bankops.domain.ForbiddenOperationException;
import com.bankops.domain.FourEyesViolationException;
import com.bankops.domain.InvalidTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tüm hataları ProblemDetail (RFC 9457) biçiminde döner (CLAUDE.md §11). Bean Validation hataları
 * (400) üst sınıf tarafından zaten bu biçime çevrilir.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler({ForbiddenOperationException.class, FourEyesViolationException.class})
    ProblemDetail forbidden(RuntimeException e) {
        return problem(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidTransitionException.class)
    ProblemDetail invalidTransition(InvalidTransitionException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ProblemDetail rateLimited(RateLimitExceededException e) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(AiUnavailableException.class)
    ProblemDetail aiUnavailable(AiUnavailableException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(AiAnalysisFailedException.class)
    ProblemDetail aiFailed(AiAnalysisFailedException e) {
        return problem(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
