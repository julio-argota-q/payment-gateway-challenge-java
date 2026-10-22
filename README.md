# Payment Gateway — Java 17 / Spring Boot

A small HTTP payment gateway built for the [Checkout.com engineering assessment](https://github.com/cko-recruitment). It validates merchant payment requests, forwards valid requests to the provided acquiring-bank simulator, records authorized or declined payments in memory, and allows previously processed payments to be retrieved by ID.

The implementation favors a clear API contract, explicit boundaries, deterministic tests, and modest operational safeguards over production-scale infrastructure. It follows the assessment's constraints, including an in-memory repository and communication with the supplied external bank simulator.

## Assessment constraints versus implementation choices

It is important to distinguish what the exercise requires from design decisions made for this implementation.

**Provided or constrained by the exercise:**

- The gateway must communicate with the supplied external acquiring-bank simulator over its HTTP API. The existence and request/response contract of that integration are **not architectural choices made by the candidate**.
- Persistence is represented by an in-memory repository for the exercise; a real database is outside the required scope. Using a map is therefore a consequence of the exercise's storage constraint, not a claim that in-memory storage is a preferred design for real payments.
- The requested behavior includes payment validation, authorized/declined/rejected outcomes, and retrieval by an internal payment identifier.

**Implementation choices within those constraints:**

- Spring Boot MVC with a synchronous `RestClient` to implement the required HTTP bank communication.
- Separate controller, service, mapper, bank-client, and repository responsibilities, using constructor injection.
- A `ConcurrentHashMap` as the thread-safe backing structure for the constrained in-memory repository.
- Jakarta Bean Validation and a combined `YearMonth` check for the request contract; consistent error responses for invalid JSON and field values.
- Internal UUIDs independent of bank authorization codes; retention of only the card's last four digits.
- Connection/read timeouts, explicit upstream-error translation, and **no automatic payment authorization retries** without idempotency guarantees.

## Requirements

- JDK 17
- Docker and Docker Compose (for the provided bank simulator)
- Network access for the initial Gradle dependency download

The Gradle wrapper is included; a separate Gradle installation is not required.

## Run locally

Start the supplied acquiring-bank simulator from the project root:

```bash
docker compose up -d
```

Then start the gateway:

```bash
./gradlew bootRun
```

Default addresses:

| Component | Address |
| --- | --- |
| Payment gateway | `http://localhost:8090` |
| Bank simulator | `http://localhost:8080` |
| Swagger UI | `http://localhost:8090/swagger-ui/index.html` |
| OpenAPI document | `http://localhost:8090/v3/api-docs` |
| Health check | `http://localhost:8090/actuator/health` |

The current API routes are **`/payment`** and **`/payment/{id}`** (no `/api` prefix).

The bank simulator is the external dependency. The gateway does not require a database or message broker.

## API

### Process a payment

`POST /payment` with JSON:

```json
{
  "card_number": "2222405343248877",
  "expiry_month": 12,
  "expiry_year": 2035,
  "currency": "GBP",
  "amount": 1050,
  "cvv": "123"
}
```

The amount is supplied **in the currency's minor unit**, as an integer: `1050` means GBP 10.50, not GBP 1,050.00. The application does not perform floating-point currency conversion.

An authorized payment returns HTTP `201 Created`:

```json
{
  "id": "c9bc8974-6ac5-442d-a1b8-546577a85e6b",
  "status": "Authorized",
  "currency": "GBP",
  "amount": 1050,
  "expiryMonth": 12,
  "expiryYear": 2035,
  "cardNumberLastFour": "8877"
}
```

The ID shown above is illustrative. A bank-declined payment also returns HTTP `201 Created`, with `"status": "Declined"`, because the gateway created and stored a completed payment attempt. Neither the full card number nor CVV appears in the response.

Example:

```bash
curl -i http://localhost:8090/payment \
  -H 'Content-Type: application/json' \
  -d '{"card_number":"2222405343248877","expiry_month":12,"expiry_year":2035,"currency":"GBP","amount":1050,"cvv":"123"}'
```

### Retrieve a payment

`GET /payment/{id}` returns HTTP `200 OK` and the same public payment fields for an existing ID; an unknown, syntactically valid UUID returns `404 Not Found`.

```bash
curl -i http://localhost:8090/payment/c9bc8974-6ac5-442d-a1b8-546577a85e6b
```

## Validation and responses

| Field | Rule |
| --- | --- |
| `card_number` | Required string containing 14–19 decimal digits |
| `expiry_month` | Required integer from 1 to 12 |
| `expiry_year` | Required integer from 1 to 9999; combined expiry month/year must not precede the current month |
| `currency` | Required; supported ISO codes are `GBP`, `EUR`, `MXN` |
| `amount` | Required positive 64-bit integer in minor currency units |
| `cvv` | Required string containing 3 or 4 decimal digits, including possible leading zeros |

The expiry date is checked using `YearMonth`. A card expiring during the current calendar month is still valid for that month. Request DTO constraints are handled by Jakarta Bean Validation. Jackson is configured not to truncate decimal JSON numbers into integer values.

For rejected requests, the acquiring bank is **not called** and no payment is stored. Validation and malformed-JSON failures return HTTP `400 Bad Request` with a consistent public response shape, for example:

```json
{
  "status": "Rejected",
  "message": "Invalid payment request",
  "errors": {
    "amount": "must not be null"
  }
}
```

For malformed JSON or incompatible JSON field types, `errors` contains a generic `request` entry rather than echoing the incoming body.

| Situation | HTTP response | Persisted? |
| --- | --- | --- |
| Bank authorizes | `201 Created`, `Authorized` | Yes |
| Bank declines | `201 Created`, `Declined` | Yes |
| Gateway rejects invalid input | `400 Bad Request`, `Rejected` | No |
| Bank responds with any HTTP 5xx status, or connection/read fails | `503 Service Unavailable` | No |
| Bank returns malformed/incomplete response, or another unexpected REST error | `502 Bad Gateway` | No |
| Payment ID does not exist | `404 Not Found` | N/A |

All bank HTTP **5xx** responses are translated to `BankUnavailableException` and gateway HTTP 503. Bank communication failures (including connection and read timeouts) also map to 503. The bank's own HTTP 400 is treated as an upstream contract/integration error and translated to gateway HTTP 502, not confused with merchant request validation by the gateway.

## Design and tradeoffs

### Separation of responsibilities

- **Controller:** exposes HTTP endpoints and triggers request validation.
- **Payment service:** coordinates authorization, status selection, persistence, and retrieval.
- **Bank client:** translates the gateway's bank request into the simulator's HTTP contract and classifies errors.
- **Mapper:** keeps public API DTOs, bank DTOs, and stored payment representations separate.
- **Repository:** isolates in-memory storage behind a small API.
- **Exception advice:** translates validation and downstream failures into stable HTTP responses.

Constructor injection makes the service and bank client independently testable without a running simulator.

### In-memory persistence

The exercise constrains persistence to a simulated in-memory repository rather than requiring a real database. Within that constraint, the implementation uses `ConcurrentHashMap<UUID, PaymentEntity>` to support concurrent access. The absence of database transactions, migrations, or ORM integration follows the exercise scope; it is not a recommendation to store real payment records only in memory.

**Tradeoff:** data disappears on restart, is local to one application instance, and is not durable or shared across replicas. A production implementation would use persistent storage and a defined transactional state model.

### Payment identifiers and data minimization

The gateway generates its own UUID for each successfully processed payment. The bank's `authorization_code` is stored separately as `bankTransactionId` for authorized payments; external references are not used as the gateway's public IDs.

The repository retains only the **last four digits** of the card number, not the full PAN or CVV. The full card number and CVV are needed only transiently when forwarding the current request to the bank. Neither appears in the public response or application-authored log messages. This limits retained sensitive information; it does **not**, by itself, constitute full PCI DSS compliance.

### Synchronous REST and timeouts

Calling the external acquiring-bank API is required by the exercise. The **implementation choice** is to use Spring's synchronous `RestClient` with Java's HTTP client, a configurable connection timeout, and a configurable read timeout. A synchronous call keeps the assessment's API and control flow simple.

Current defaults in `application.properties`:

```properties
bank.api.base-url=http://localhost:8080
bank.api.connection-timeout=1000
bank.api.read-timeout=1000
```

Spring Boot binds these settings to a validated `BankClientProperties` record with `Duration` values. Timeout values are milliseconds in the current properties file. Configuration validation requires nonzero positive timeout durations and an HTTP/HTTPS bank URL with a host.

**Tradeoff:** each in-flight request occupies resources while the gateway waits for the bank. For higher throughput, additional connection-pool tuning, asynchronous processing, capacity limits, and observability would be considered based on measurements.

### No automatic retries

There are deliberately **no automatic retries** of authorization requests. A timeout or dropped response does not prove that the bank did not process a charge. Blindly retrying could create a second authorization.

A production retry policy would first require a bank-supported **idempotency key**, reconciliation/retrieval of uncertain outcomes, and explicit retryable/non-retryable failure classification. The current application returns an error without saving an authorized or declined payment when the bank outcome is unknown.

### Status modeling

`Authorized` and `Declined` are payment states returned by the bank and persisted by the gateway. `Rejected` represents a request refused by the gateway's own validation before a bank call; it is returned as an error response rather than recorded as a payment. These are deliberately distinct from transient infrastructure failures.

### Validation boundaries

The public DTO enforces the merchant-facing contract before the service calls the bank. The bank client also checks that an upstream authorization response is complete, including a nonblank authorization code when `authorized` is `true`.

An invalid request does not get an internal payment record. Likewise, an unavailable bank or invalid bank response does not lead to a stored `Authorized`/`Declined` outcome.

## Testing

Run all tests:

```bash
./gradlew clean test
```

The test suite uses JUnit 5, Mockito, Spring MockMvc, and Spring's mock HTTP server:

| Test class | Scope |
| --- | --- |
| `ProcessPaymentRequestValidationTest` | Parameterized validation cases and expiry-date boundaries |
| `PaymentGatewayControllerTest` | HTTP status and response contract, malformed JSON, decimal amounts, POST followed by GET, and no bank call for rejected input |
| `PaymentGatewayServiceTest` | Authorization/decline persistence, separate bank reference, failures without persistence, and lookup behavior |
| `BankClientTest` | Outbound JSON mapping, bank authorization/decline, HTTP errors, connection failures, and malformed/incomplete bank responses |

Controller tests mock the `BankClient`, so they do not depend on Docker or live bank connectivity. Bank-client tests stub HTTP responses, allowing both positive and negative paths to be checked deterministically. A manual end-to-end demonstration with the supplied bank simulator is still useful.

**Note:** this README describes the checked-in tests and configuration; it does not claim the test suite has been executed in this environment.

## Logging and observability

SLF4J logging records payment IDs and outcomes, with bank failures logged as warnings. Debug-level messages include currency and amount, but application-authored logs should never include CVVs, complete card numbers, or full payment request bodies. Avoid enabling verbose HTTP wire logging of sensitive payment payloads.

Spring Boot Actuator provides `/actuator/health`, `/actuator/info`, and `/actuator/metrics`. A production gateway would additionally measure bank authorization latency, downstream error rates, request correlation, and alerts without putting card data in logs or metric labels.

## Production considerations deliberately left out of scope

- Persistent storage, recovery across restarts, reconciliation, and audit trails.
- Merchant authentication and authorization, rate limits, HTTPS termination, secrets management, and production PCI controls.
- Idempotency enforcement and safe handling of uncertain upstream outcomes.
- Distributed tracing, richer metrics/alerts, and operational dashboards.
- Load testing, multi-instance deployment, and high-availability infrastructure.

These omissions reflect the assessment's scope and constraints rather than a claim that the current service is production-ready. In particular, neither the simulated repository nor the supplied bank API is a discretionary substitute for a production payment ledger or real bank integration.
