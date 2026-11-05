package com.emmanuel.venn.loads;

import com.emmanuel.venn.loads.attempts.LoadAttempt;
import com.emmanuel.venn.money.Money;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Entry point shared by the REST controller and the batch runner. */
@Component
public class LoadHandler {

    private static final Pattern DOLLARS = Pattern.compile("\\$(\\d{1,15})(\\.\\d{1,2})?");

    private static final Currency DOLLAR_SIGN_CURRENCY = Currency.getInstance("CAD");
    private static final int MIN_YEAR = 1970;
    private static final int MAX_YEAR = 9999;

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
    public Optional<LoadResponse> handle(LoadRequest raw) {
        LoadRequest request = raw.trimmed();
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
                parseAmount(request.loadAmount()), parseTime(request.time()));
    }

    private static Money parseAmount(String raw) {
        if (!DOLLARS.matcher(raw).matches()) {
            throw new InvalidLoadException("load_amount must look like $123.45 and be under 1 quadrillion, got: " + shown(raw));
        }
        Money amount = Money.ofMajor(new BigDecimal(raw.substring(1)), DOLLAR_SIGN_CURRENCY);
        if (amount.minorUnits() <= 0) {
            throw new InvalidLoadException("load_amount must be positive, got: " + shown(raw));
        }
        return amount;
    }

    private static Instant parseTime(String raw) {
        try {
            Instant time = OffsetDateTime.parse(raw).toInstant();
            int year = time.atOffset(ZoneOffset.UTC).getYear();
            if (year < MIN_YEAR || year > MAX_YEAR) {
                throw new InvalidLoadException("time must be in the years " + MIN_YEAR + " to " + MAX_YEAR);
            }
            return time;
        } catch (DateTimeParseException e) {
            throw new InvalidLoadException(
                    "time must be an ISO-8601 timestamp like 2018-01-01T00:00:00Z, got: " + shown(raw));
        }
    }

    private static String shown(String raw) {
        String clean = raw.replaceAll("\\p{Cntrl}", "?");
        return clean.length() <= 40 ? clean : clean.substring(0, 40) + "...";
    }
}
