package com.emmanuel.venn.api;

import com.emmanuel.venn.domain.InvalidLoadException;
import com.emmanuel.venn.domain.LoadAmountParser;
import com.emmanuel.venn.domain.LoadAttempt;
import com.emmanuel.venn.service.LoadService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Entry point shared by the REST controller and the CLI. */
@Component
public class LoadHandler {

    private final LoadService service;
    private final Validator validator;

    public LoadHandler(LoadService service, Validator validator) {
        this.service = service;
        this.validator = validator;
    }

    /**
     * @return the response, or empty when the load id was already seen for this customer
     * @throws InvalidLoadException if the request is malformed
     */
    public Optional<LoadResponse> handle(LoadRequest request) {
        LoadAttempt attempt = toAttempt(request);
        return service.process(attempt)
                .map(decision -> new LoadResponse(request.id(), request.customerId(), decision.isAccepted()));
    }

    private LoadAttempt toAttempt(LoadRequest request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new InvalidLoadException(violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining("; ")));
        }
        return new LoadAttempt(request.id(), request.customerId(),
                LoadAmountParser.parse(request.loadAmount()), parseTime(request.time()));
    }

    private static Instant parseTime(String raw) {
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException e) {
            throw new InvalidLoadException("time must be an ISO-8601 timestamp like 2018-01-01T00:00:00Z, got: " + raw);
        }
    }
}
