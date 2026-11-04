# Venn take-home: velocity limits

A Spring Boot service that accepts or declines fund-load attempts per customer. It runs as a REST API and as a batch tool that reads JSON lines and writes one decision per line.

## Rules

Per customer: at most $5,000 per day, $20,000 per week and 3 loads per day. A day is a UTC date and a week starts on Monday 00:00 UTC.

Interpretations I verified against the expected output in the task:

- Only **accepted** loads count toward the limits. A declined load does not use up allowance.
- A repeated load ID is scoped **per customer**. Only the first attempt is evaluated and later ones get no response. A declined load's ID still counts as seen, so a retry is ignored and not re-evaluated.
- The limits are **inclusive**: loading exactly $5,000 in a day is allowed. The task data has no boundary case, so this is an assumption, covered by unit tests.
- Timestamps with a UTC offset are converted to UTC before the day and week are chosen.

## Run

Requires Java 25 (the Maven wrapper is included).

Batch mode reads a file and writes the decisions. It does not start the web server:

```
./mvnw -q package
java -jar target/venn-take-home-0.0.1-SNAPSHOT.jar --input=input.txt --output=output.txt
```

Malformed or invalid lines are logged with their line number and skipped. Infrastructure failures (for example the database going down) stop the batch with a non-zero exit code.

API mode:

```
./mvnw spring-boot:run
curl -X POST localhost:8080/v1/loads -H 'Content-Type: application/json' \
  -d '{"id":"1234","customer_id":"1234","load_amount":"$123.45","time":"2018-01-01T00:00:00Z"}'
```

| Response | Meaning |
| --- | --- |
| `200` `{"id","customer_id","accepted"}` | Decision |
| `204` | Load ID already seen for this customer, ignored |
| `400` ProblemDetail | Malformed or invalid request |
| `500` ProblemDetail | Unexpected error, details are logged and not returned |

Swagger UI is at `/swagger-ui.html` and the OpenAPI spec at `/v3/api-docs`.

Docker files are included (`Dockerfile`, `compose.yaml` with Prometheus). They have not been built or run yet.

## Tests

```
./mvnw test
```

- Unit tests for the rules: limit boundaries, the day and week windows, amount parsing.
- Service tests against an in-memory fake of the persistence port.
- Repository tests against H2 through the real Flyway migration.
- Controller tests for status codes and `ProblemDetail` bodies.
- End-to-end batch tests: a hand-written edge-case fixture, and a diff of the full task input against Venn's expected output. The second one needs the task files in `question/` (not committed) and skips itself otherwise.

## Design

```
api      REST controller, request/response DTOs, LoadHandler, correlation ID filter
cli      BatchRunner and BatchProcessor
service  LoadService: duplicate check, policy, persistence, metrics
domain   VelocityLimitPolicy, LimitWindows, LoadAmountParser, the LoadAttempts port
persistence  JPA adapter for the port
```

- The controller and the CLI are thin adapters over one `LoadHandler`, so validation and mapping happen in one place.
- The rules are a pure class with no Spring or database dependency, so they are tested without a context.
- Every attempt is stored with its decision and rejection reason, which doubles as an audit trail. A unique `(customer_id, load_id)` constraint backs duplicate detection and a check constraint ties status to rejection reason.
- The schema is plain SQL under Flyway, and the service depends on a repository interface, so moving from H2 to Postgres means changing the datasource and the driver.
- Limits are configuration (`velocity.limits.*` in `application.yaml`).

## Observability

- Logs carry a correlation ID: `X-Correlation-Id` from the caller (or a generated one) for requests, and `batch-<uuid>` for batch runs. Unsafe header values are replaced.
- Micrometer counter `loads.processed`, tagged by `outcome` (accepted, rejected, duplicate) and, for rejections, `reason`.
- `/actuator/health`, `/actuator/metrics` and `/actuator/prometheus` are exposed.

`spring-boot-h2console` is kept for local development and may be removed. The console is off unless `spring.h2.console.enabled=true`.

## Known limitations and what I would do next

- **Concurrency.** The service checks the limits and then inserts, without locking per customer. Two simultaneous requests for the same customer could both pass. Batch mode is sequential, so it is not affected. In production I would serialize per customer with a row lock or serializable transactions and a retry.
- **Authentication.** None. I would add an OAuth2 resource server.
- **Database.** In-memory H2 is fine for the task. In production I would use Postgres and run the repository tests against it with Testcontainers.
- **Retention.** Stored attempts only matter for the current week and for duplicate detection, so old rows could be archived.
- **Deployment.** I would add Kubernetes manifests with the actuator liveness and readiness probes.

## AI usage

I built this with Claude Code. The commits are co-authored accordingly. I reviewed the code and can explain every decision.
