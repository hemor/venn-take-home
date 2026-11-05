package com.emmanuel.venn.loads;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

/** Not scoped to this package, so it is also the app-wide fallback until a shared web handler exists. */
@RestControllerAdvice
class LoadExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LoadExceptionHandler.class);

    @ExceptionHandler(InvalidLoadException.class)
    ProblemDetail handleInvalidLoad(InvalidLoadException e) {
        log.warn("Invalid load request: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid load request");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Throwable cause = e.getMostSpecificCause();
        if (cause instanceof JacksonException jackson && jackson.getLocation() != null) {
            log.warn("Unreadable load request: {} at line {}, column {}", cause.getClass().getSimpleName(),
                    jackson.getLocation().getLineNr(), jackson.getLocation().getColumnNr());
        } else {
            log.warn("Unreadable load request: {}", cause.getClass().getSimpleName());
        }
        return super.handleHttpMessageNotReadable(e, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Unexpected error while processing a load", e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error while processing the load");
        problem.setTitle("Internal error");
        return problem;
    }
}
