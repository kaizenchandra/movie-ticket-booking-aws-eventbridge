package com.kaizenchandra.awseventbridgedemo.shared.adapter.in;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import com.kaizenchandra.awseventbridgedemo.shared.domain.Problem;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiProblem> error(Exception e) {
        String code;
        int status;
        if (bodyTooLarge(e)) {
            code = "REQUEST_TOO_LARGE";
            status = 413;
        } else if (e instanceof Problem p) {
            code = p.code;
            status = code.equals("NOT_FOUND") ? 404 : code.startsWith("INVALID") ? 400 : 409;
        } else if (e instanceof java.util.concurrent.TimeoutException || e instanceof java.util.concurrent.RejectedExecutionException) {
            code = "OVERLOADED";
            status = 503;
        } else if (e instanceof org.springframework.dao.DataIntegrityViolationException) {
            code = "CONSTRAINT_CONFLICT";
            status = 409;
        } else if (e instanceof org.springframework.dao.TransientDataAccessException || e instanceof org.springframework.transaction.TransactionTimedOutException) {
            code = "RETRY_TRANSACTION";
            status = 503;
        } else if (e instanceof org.springframework.web.server.ServerWebInputException || e instanceof org.springframework.web.method.annotation.HandlerMethodValidationException || e instanceof jakarta.validation.ConstraintViolationException || e instanceof IllegalArgumentException) {
            code = "INVALID_REQUEST";
            status = 400;
        } else {
            code = "INTERNAL_ERROR";
            status = 500;
            org.slf4j.LoggerFactory.getLogger(Errors.class).error("request failed: {}", e.getClass().getSimpleName());
        }
        var problem = new ApiProblem("urn:cinema:problem:" + code.toLowerCase(java.util.Locale.ROOT), code, status, code, code);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private boolean bodyTooLarge(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof org.springframework.core.io.buffer.DataBufferLimitException) return true;
        }
        return false;
    }

    public record ApiProblem(String type, String title, int status, String detail, String code) {
    }
}
