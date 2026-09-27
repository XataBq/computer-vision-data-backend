package com.example.media.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.ErrorResponse;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    public record ErrorBody(Instant timestamp, int status, String code, String message, String path) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> domain(ApiException ex, HttpServletRequest request) {
        return response(ex.status(), ex.code(), ex.getMessage(), request);
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorBody> tooLarge(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "Upload exceeds the configured limit", request);
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, MultipartException.class, TypeMismatchException.class})
    public ResponseEntity<ErrorBody> invalid(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed", request);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> other(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse error) {
            HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
            return response(status, "HTTP_" + status.value(), status.getReasonPhrase(), request);
        }
        // Exception messages, rejected values and causes may contain authenticated source URLs.
        log.error("Request failed: exceptionType={}", ex.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error", request);
    }
    private ResponseEntity<ErrorBody> response(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorBody(Instant.now(), status.value(), code, message, request.getRequestURI()));
    }
}
