# Venn take-home: velocity limits

Spring Boot service that accepts or declines fund-load attempts per customer. Input is JSON lines, output is `{id, customer_id, accepted}`.

## Rules (verified against Venn's expected output)
- Per customer: max $5,000/day, $20,000/week, 3 loads/day. Limits are inclusive: exactly $5,000 is allowed.
- Rules are data under `velocity.rules` in application.yaml (name, window DAY|WEEK, metric AMOUNT|COUNT, max). The rule name is stored as the rejection reason. Order matters: the first broken rule is reported.
- Day = UTC date. Week starts Monday 00:00 UTC.
- Only accepted loads count toward the limits. Declined loads do not.
- Duplicate load ID for the same customer: ignore all but the first, with no response line. A declined load's ID still counts as seen.
- Input arrives in chronological order.

## Architecture
- The service layer holds the logic. The REST controller and the batch runner are thin adapters over one `LoadHandler` that call it in-process.
- Batch mode (`loads/batch`) is an `ApplicationRunner` active only with `--input` and `--output`. A malformed line is logged and skipped, never fatal.
- Every attempt is stored with status (accepted/rejected) and a reason. Limit sums query accepted rows only.
- Persistence: Spring Data JPA behind a repository interface, portable Flyway SQL, H2 in-memory by default.
- Errors: `ProblemDetail` via `@RestControllerAdvice`. API is under `/v1/`.
- Logging: SLF4J with a correlation ID in the MDC. Metrics: Micrometer `MeterRegistry` injected directly, no wrapper.

## Stack
Java 25, Spring Boot 4.x, Maven, Flyway, H2, Actuator, springdoc-openapi. No Lombok.

## Testing
- Unit tests for the rules and windows, including the boundaries and the Sunday-to-Monday rollover.
- Integration tests on committed fixtures in `src/test/resources`.
- A full-data comparison test reads `question/` and skips itself when the files are absent. `question/` is gitignored.

## Commands
- `./mvnw test` runs everything. `./mvnw -q package` builds the jar.
- Batch: `java -jar target/venn-take-home-0.0.1-SNAPSHOT.jar --input=<file> --output=<file>`.
- Venn's expected output uses CRLF. Compare ignoring carriage returns.

## Conventions
- Small commits, one task each. Commit messages end with the `Co-Authored-By` trailer.
- Comments only when the code can't explain itself. Rename instead of commenting.
- Package is `com.emmanuel.venn`, grouped by feature. `loads` (controller, DTOs, handler, service, error handler) contains `attempts` (stored attempt history: public LoadAttempt/Decision/AttemptHistory contract, package-private JPA internals) and `batch` (the batch entry point). `velocity` is the configurable limit engine and knows nothing about loads. `observability` holds cross-cutting logging.
- Dependencies point one way: batch -> loads -> attempts -> velocity. Never import a feature from `velocity` or `observability`.
- Cross-cutting code goes in a package named for its concern (`web`, `security`, `observability`), never `common`, `util` or `shared`.
