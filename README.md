# Venn take-home: velocity limits

A Spring Boot service that accepts or declines fund-load attempts per customer. It runs as a REST API and as a batch tool that reads JSON lines and writes one decision per line.

## Where to start

| To understand | Start at |
| --- | --- |
| Startup, and how batch mode is chosen | `VennTakeHomeApplication` |
| The REST entry point | `loads/LoadController` |
| The batch entry point | `loads/batch/BatchRunner` |
| How a load is decided | `loads/LoadService` |
| The limits | `velocity/VelocityLimitPolicy` and `velocity.rules` in `application.yaml` |
| Storage | `loads/attempts/JpaAttemptHistory` and `db/migration/V1__create_load_attempt.sql` |
| Logging and metrics | `observability/CorrelationIdFilter` and the counters in `LoadService` |

Every package has a `package-info.java` saying what it is for.

## Rules

Per customer: at most $5,000 per day, $20,000 per week and 3 loads per day. A day is a UTC date and a week starts on Monday 00:00 UTC.

The rules are configuration, not code. They live under `velocity.rules` in `application.yaml`:

```yaml
velocity:
  rules:
    - name: DAILY_COUNT_LIMIT   # also stored as the rejection reason
      window: DAY                  # DAY or WEEK
      metric: COUNT                # AMOUNT or COUNT (number of loads)
      max: 3
    - name: DAILY_AMOUNT_LIMIT
      window: DAY
      metric: AMOUNT
      max-in-cents: 500_000
      # currency: CAD             # optional, AMOUNT only; limits this rule to loads in that currency
```

A COUNT rule is capped with `max` and an AMOUNT rule with `max-in-cents`. Using the other key for a rule type fails startup. Underscores such as `500_000` are allowed in YAML files only. Command-line arguments and environment variables need plain digits.

Rules are checked in the order listed and the first one broken is the reason reported. Add, remove or change entries to change the limits, for example a weekly cap of 10 loads is one more entry. To change them without rebuilding, put the `velocity` block in a separate file and start the app with `--spring.config.additional-location=file:./rules.yaml`. A list in the external file replaces the built-in one. Startup fails if there are no rules, a rule is invalid (blank name, a non-positive limit, the wrong key for its metric) or two rules share a name.

A new window type (for example a month) is one enum constant in `WindowType`.

## Money and currency

Amounts are stored as an integer count of the smallest unit (cents for CAD) in `amount_minor BIGINT`, next to a `currency` column holding the ISO 4217 code. Nothing in the path is floating point: the request string is parsed into exact minor units (`Money`), summed in the database as integers, and compared with `Math.addExact` so an overflow fails loudly. The decimal places come from `java.util.Currency` (CAD 2, JPY 0, KWD 3), and an amount finer than its currency allows is rejected.

The task's input has no currency, so a `$` amount is read as **CAD**. That is an assumption of mine, since `$` is also USD, AUD and others, and it lives in one constant in `LoadHandler`.

An amount rule with a `currency` applies only to loads in that currency. Without one, which is how the default rules are written, it applies to every load and compares plain numbers in the currency of the load being checked, with no conversion. That is exact while everything is CAD and not meaningful with several currencies. A count rule counts loads in every currency. The totals query sums one currency and counts all of them in a single statement.

The open question is the policy for several currencies: one limit with every load converted to CAD first, separate limits per currency, or both. Converting needs exchange rates, which I have not built, and I would not pick between them without the product owner. A count rule cannot be limited to a currency yet.

How much work another currency is, as tested:

- **Database, money arithmetic, per-currency limits:** no code. `AdditionalCurrencyTest` adds USD and JPY rules in configuration only and checks that each has its own limit, that JPY is limited in whole units, that the count limit spans currencies, and that minor units and currency are stored correctly. `JpaAttemptHistoryTest` checks the per-currency totals against the real schema for CAD, USD, JPY and KWD.
- **Accepting it over the API:** a few lines, not yet done: an optional `currency` field in `LoadRequest` and its mapping in `LoadHandler`, defaulting to CAD. Until then no non-CAD load can enter.
- **Not done, on purpose:** exchange rates, and an allow-list of accepted currencies in the handler before one is exposed. A load in a currency that only has currency-specific limits for other currencies has no amount limit.

Interpretations checked against the expected output in the task:

- Only **accepted** loads count toward the limits. A declined load does not use up allowance.
- A repeated load ID is scoped **per customer**. Only the first attempt is evaluated and later ones get no response. A declined load's ID still counts as seen, so a retry is ignored and not re-evaluated.
- The limits are **inclusive**: loading exactly $5,000 in a day is allowed. The task data has no boundary case, so this is an assumption, covered by unit tests.

Other choices of mine, not confirmed by the task data:

- Timestamps with a UTC offset are converted to UTC before the day and week are chosen.
- Input is validated against an allow-list, and every field is trimmed first. `id` and `customer_id` are 1 to 64 characters of letters, digits, `.`, `_` and `-`. `load_amount` is `$` plus up to 15 digits and at most 2 decimals. `time` is an ISO-8601 timestamp with an offset, in the years 1970 to 9999. Anything else is a 400 over REST and a skipped line in batch. The ids are opaque strings, never numbers, so `015` and `15` stay different.

## Run

Requires Java 25 (the Maven wrapper is included).

Batch mode reads a file and writes the decisions. It does not start the web server:

```
./mvnw -q package
java -jar target/venn-take-home-0.0.1-SNAPSHOT.jar --input=input.txt --output=output.txt
```

Malformed or invalid lines are logged with their line number and skipped. Infrastructure failures (for example the database going down) stop the batch with a non-zero exit code and leave a partial output file. The output path must differ from the input path.

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

- Unit tests for the rules: limit boundaries and the day and week windows.
- Handler tests for validation and for parsing amounts and timestamps.
- Service tests against an in-memory fake of the persistence port.
- Repository tests against H2 through the real Flyway migration.
- Controller tests for status codes and `ProblemDetail` bodies.
- Concurrency tests: simultaneous loads for one customer never exceed the daily count or amount, and a repeated load is decided once. A multi-instance test starts an H2 TCP server and five application contexts sharing it, and sends each one a load for the same customer at the same instant. Exactly three are accepted and two rejected, over 25 rounds. All five are accepted if the row lock is removed.
- End-to-end batch tests: a hand-written edge-case fixture, and a diff of the full task input against Venn's expected output. The second one needs the task files in `question/` (not committed) and skips itself otherwise.

## Design

```
VennTakeHomeApplication
loads/            the fund-load feature: REST controller, request/response contract, validation,
                  LoadHandler, LoadService and the error handler
  attempts/       the stored history of load attempts: the public LoadAttempt, Decision and
                  AttemptHistory contract, with the JPA entity, repository and adapter hidden inside
  batch/          the batch entry point: BatchRunner, BatchProcessor
velocity/         the configurable limit engine: VelocityRule, VelocityLimitPolicy, WindowType and
                  their configuration. It knows nothing about loads
observability/    cross-cutting logging, such as the correlation ID filter
money/            exact amounts in minor units with a currency, used by loads and velocity
```

- Packages are grouped by feature, in the style of Spring PetClinic, and split only where there is a distinct concern. A package sits at the top level only if it is a feature, an engine no single feature owns (`velocity`) or a cross-cutting concern (`observability`). Anything owned by one feature lives inside it.
- A sub-package exists to hide internals behind a small public surface: `attempts` hides JPA and `batch` hides `BatchProcessor`.
- Dependencies point one way: `batch` uses `loads`, `loads` uses `attempts`, `attempts` uses `velocity`, `velocity` uses `money`, and `money` and `observability` use nothing.
- The controller and the batch runner are thin adapters over one `LoadHandler`, so validation and mapping happen in one place.
- The rule classes have no Spring or database dependency (only `VelocityConfig` and `VelocityProperties` wire them in), so they are tested without a context.
- Every attempt is stored with its decision and rejection reason, which doubles as an audit trail. `customer_lock` holds one row per customer, locked while a load is decided. A unique `(customer_id, load_id)` constraint backs duplicate detection and a check constraint ties status to rejection reason.
- The schema is plain SQL under Flyway, and the service depends on a repository interface, so moving from H2 to Postgres means changing the datasource and adding the Postgres driver and Flyway's `flyway-database-postgresql` module.
- Rules are data (`VelocityRule`) evaluated in order by `VelocityLimitPolicy`. The policy asks for a customer's accepted totals once per distinct window, and stops at the first broken rule.

## Observability

- Logs carry a correlation ID: `X-Correlation-Id` from the caller (or a generated one) for requests, and `batch-<uuid>` for batch runs. Unsafe header values are replaced.
- Micrometer counter `loads.processed`, tagged by `outcome` (accepted, rejected, duplicate) and, for rejections, `reason`.
- `/actuator/health`, `/actuator/metrics` and `/actuator/prometheus` are exposed.

## Database

`application.yaml` configures an in-memory H2 database named `venn` (`jdbc:h2:mem:venn`). The login is `user`/`password`. Override it with the standard Spring Boot environment variables `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`. The data lives as long as the JVM and is private to it: each instance gets its own empty database.

To browse it, start the app with the `dev` profile, which turns on the H2 console. The console is off otherwise:

```
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Then open http://localhost:8080/h2-console and log in with the JDBC URL above. `spring-boot-h2console` is kept for this and may be removed later.

For another database, set `SPRING_DATASOURCE_URL` along with the username and password variables, and add its driver.

## Known limitations and what I would do next

- **Concurrency.** Decisions for one customer are serialized twice: by an in-process queue per customer, so waiting requests are parked virtual threads and not pooled connections or Tomcat workers, and by a row lock in `customer_lock`, so the check and the insert are atomic across instances. The cross-instance guarantee needs a database the instances share, which the default in-memory H2 is not. Point `SPRING_DATASOURCE_URL` at an H2 server (`jdbc:h2:tcp://host:9092/mem:venn;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000`) or Postgres. The row lock waits at most 5 seconds on H2 (`LOCK_TIMEOUT` in the URL); on Postgres I would set `lock_timeout`, which otherwise waits forever. The lock relies on read committed isolation, which the transaction sets explicitly. I would add a distributed lock such as Redis only if contention is measured.
- **Retries.** A repeated load ID gets a `204`, so the original decision is not replayed. I would return the stored decision, or a `409` when the payload differs.
- **Authentication.** None. I would add an OAuth2 resource server, rate limiting and TLS.
- **Secrets.** The database login is a placeholder in `application.yaml`. I would read it from a secrets store.
- **Database.** In-memory H2 is fine for the task. In production I would use Postgres and run the repository tests against it with Testcontainers.
- **Backups.** The database is in memory, so there are none. Production needs point-in-time recovery and a restore that has been tried.
- **Retention.** Stored attempts only matter for the current week and for duplicate detection, so old rows could be archived.
- **Deployment.** I would add Kubernetes manifests with the actuator liveness and readiness probes.

## AI usage

I built this with Claude Code. The commits are co-authored accordingly. I reviewed the code and can explain every decision.
