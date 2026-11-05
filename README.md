# Venn take-home: velocity limits

A Spring Boot service that accepts or declines fund-load attempts per customer. Each customer may load at most $5,000 per day, $20,000 per week and 3 loads per day. A day is the UTC date and a week starts Monday 00:00 UTC. Input is JSON lines, and the output is one `{id, customer_id, accepted}` line per decision.

Assumptions:

- Only accepted loads count toward the limits.
- A repeated load ID for the same customer is ignored with no output, even if the first attempt was declined.
- Limits are inclusive: exactly $5,000 is allowed. The task data has no case that tests this.
- `$` is read as CAD.
- Input arrives in chronological order.

## Build and run

Requires Java 25. The Maven wrapper is included.

```
./mvnw test                  # unit, integration and end-to-end tests
./mvnw -q package            # builds the jar (add -DskipTests to skip the tests)
```

Batch mode reads a file and writes the decisions, without starting the web server:

```
java -jar target/venn-take-home-0.0.1-SNAPSHOT.jar --input=input.txt --output=output.txt
diff <(tr -d '\r' < "question/Venn - Back-End - Output .txt") output.txt
```

Invalid lines are logged and skipped. The diff strips carriage returns because Venn's expected output uses CRLF.

API mode:

```
./mvnw spring-boot:run
curl -X POST localhost:8080/v1/loads -H 'Content-Type: application/json' \
  -d '{"id":"1234","customer_id":"1234","load_amount":"$123.45","time":"2018-01-01T00:00:00Z"}'
```

It returns 200 with the decision, or 204 for a repeated load ID. Invalid input gets a 400 ProblemDetail. Swagger UI is at `/swagger-ui.html`.

## Design

- The REST controller and the batch runner are thin adapters over one `LoadHandler`, which validates the input and calls `LoadService`. Start reading at `LoadService`.
- The limits are data under `velocity.rules` in `application.yaml`. They are checked in order, and the first broken rule's name is stored as the rejection reason. The `velocity` package has no Spring, database or loads dependency.
- Money is exact: `long` minor units (cents) plus a currency code, never floating point. An amount rule can be limited to one currency.
- Every decided attempt is stored with its status, using JPA, Flyway SQL and in-memory H2. A unique `(customer_id, load_id)` constraint backs duplicate detection.
- Concurrency: loads for one customer are serialized by an in-process queue and a `customer_lock` row lock, so simultaneous requests cannot exceed a limit. `MultiInstanceTest` runs five application instances on a shared H2 server and checks that exactly three of five simultaneous loads are accepted. The default in-memory H2 is private to one instance, so running several instances needs a shared database.
- Logs carry a correlation ID, and Micrometer metrics are exposed at `/actuator/prometheus`.

Not covered: authentication, and currencies other than CAD (a rule with a currency is supported, but no input produces one yet).

Built with Claude Code.
