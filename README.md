# Reliable Commerce

Reliable Commerce is a production-oriented e-commerce backend built with Java and Spring Boot. The project focuses on reliability, consistency, concurrency, distributed communication, and failure handling rather than only CRUD operations and happy-path flows.

The system covers the full order lifecycle, including product management, inventory, order creation, payment processing, asynchronous events, retries, and recovery from partial failures.

## Tech Stack

- Java 21
- Spring Boot
- Spring Data JPA / Hibernate
- PostgreSQL
- Liquibase
- Spring Security
- RabbitMQ
- Redis
- Testcontainers
- Docker / Docker Compose
- Micrometer / Actuator

## Main Engineering Topics

The project implements and explores:

- Database transactions and isolation levels
- Optimistic and pessimistic locking
- Concurrent inventory updates
- Idempotent HTTP operations
- Idempotent payment processing
- Transactional Outbox Pattern
- Inbox Pattern for duplicate message handling
- RabbitMQ event-driven communication
- Consumer acknowledgements and message redelivery
- Retry strategies
- Exponential backoff and jitter
- Dead-letter queues
- Circuit breakers
- Timeouts
- Payment reconciliation
- Saga-style compensation
- Distributed failure handling
- Redis caching and cache invalidation
- Database indexing and query optimization
- `EXPLAIN ANALYZE`
- Java concurrency and bounded thread pools
- Integration and concurrency testing
- Structured logging and correlation IDs
- Metrics and health checks

## Core Scenarios

The project is designed around real production scenarios such as:

```text
Two customers try to buy the last available item.
```

```text
The same checkout request is submitted multiple times.
```

```text
An event is delivered more than once by RabbitMQ.
```

```text
An order is saved successfully but event publishing fails.
```

```text
A payment succeeds at the provider but the network times out
before the application receives the response.
```

```text
A consumer crashes after processing a message but before acknowledging it.
```

```text
A downstream service becomes unavailable or slow.
```

The system must remain consistent and recover correctly from these situations.

## Architecture

The project starts as a modular Spring Boot application and gradually introduces distributed components where they solve a real problem.

```text
Client
   |
   v
Order Service
   |
   | OrderPlaced
   v
RabbitMQ
   |
   v
Payment Service
   |
   v
Payment Provider
```

Each service owns its own database and communicates through APIs or asynchronous events.

The architecture is introduced incrementally, with correctness and reliability taking priority over unnecessary complexity.

## Project scaffold

The Spring Boot application lives in `order-service/`. The controller and DTO are empty scaffolds; business logic will be added later.

Compile from the repository root:

```sh
./mvnw -f order-service/pom.xml compile
```

## Local database

Start PostgreSQL with Docker Desktop running:

```sh
docker compose -f infrastructure/docker-compose.yml up -d --wait
```

Run with the `local` profile:

```sh
./mvnw -f order-service/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

In IntelliJ, set **Active profiles** to `local` in the `OrderServiceApplication`
run configuration (or set `SPRING_PROFILES_ACTIVE=local`). This profile supplies a
public JWT key for local testing, so no secret setup is needed. Request validation
and authentication remain enabled. Use this profile only for local testing.

The application connects to
`jdbc:postgresql://localhost:5432/order_db` with username `order_user` and password
`order_password`. These credentials are for local development. Override the application
connection with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` when needed.

Database data persists in a Docker volume. Stop PostgreSQL while keeping the data:

```sh
docker compose -f infrastructure/docker-compose.yml down
```

This setup creates the database only; application tables require migrations or a
separate Hibernate schema-generation configuration.


## Authentication configuration

Set `JWT_SECRET` to a newly generated Base64-encoded random key of at least
32 bytes when running without the `local` profile. Only `local` supplies a public
test-key fallback; `JWT_SECRET` overrides it. The old committed key must not be reused.
For local development, `.env.jwt` is ignored by Git. If it already exists, load it:

```sh
source .env.jwt
./mvnw -f order-service/pom.xml spring-boot:run
```

To create that file for a new checkout, run once:

```sh
(umask 077; printf 'export JWT_SECRET=%s\n' "$(openssl rand -base64 32)" > .env.jwt)
```

Spring Boot does not automatically load this file. For IntelliJ, supply
`JWT_SECRET` in the run configuration's environment variables. In deployment,
generate a fresh key in your secret manager and restart every instance with it.
This invalidates all access and refresh tokens signed with the previous key;
users must log in again. Never commit the generated key.

Migration 010 supports existing users without assigning a shared password.
Accounts that predate the password column receive `!`, a non-loginable marker.
Provision a BCrypt hash through a trusted, identity-verified recovery process
before those accounts can log in. A self-service reset endpoint is not implemented.
The password column remains `NOT NULL`, with no default for new rows. Databases
that already applied migration 010 retain their existing credentials.

Registration requires a valid email and a nonblank password
of 12–72 Java characters and at most 72 UTF-8 bytes (BCrypt's input limit).
Login checks a nonblank, valid email, a nonblank password, and the same maximum byte length without applying
the new minimum to existing passwords.

## Product listing

`GET /api/products` accepts these optional query parameters:

- `name`: case-insensitive name substring.
- `minPrice`, `maxPrice`: inclusive decimal price bounds; nonnegative, with at most two decimal places. The minimum cannot exceed the maximum.
- `page`: zero-based page index, default `0`.
- `size`: `1`–`100`, default `20`.
- `sortBy`: `id`, `name`, or `price`, optionally followed by `,asc` or `,desc`. Default: `id,asc`. Name and price sorting use ascending ID as a tie-breaker.

For example:

```text
/api/products?name=coffee&minPrice=25.50&maxPrice=100.00&size=20&sortBy=price,asc
```

The response is a slice envelope rather than a bare array:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "hasNext": false
}
```

Products in `content` use the same fields as product details. No total count is calculated;
`hasNext` indicates whether another page is available. Invalid filter parameters return `400`.
The previous `productName`, `greaterThan`, and `lessThen` query parameters are replaced by
`name`, `minPrice`, and `maxPrice`.

### Refresh-token sessions

A successful login invalidates all previous refresh tokens for that account and
issues a new pair. Failed logins leave existing sessions intact. Refresh rotates
the supplied active, unexpired token; logout revokes the supplied refresh token.
Existing access tokens remain valid until their expiry (15 minutes by default).
Refresh tokens have unique IDs, are stored as SHA-256 hashes, and use the JWT
expiration as their database expiry. Login, refresh, and logout acquire an account
row lock to serialize session changes.

### Order idempotency

Send a nonblank `Idempotency-Key` header (at most 255 characters) on `POST /orders`.
Generate a key once per checkout and reuse that key and the same request body
after a timeout or connection failure. Use a new key for a new checkout.
Keys are scoped to the authenticated user. A successful retry returns the original
response without creating an order or reducing stock again. Reusing a key with a
different body returns HTTP 409. Concurrent requests with the same key share one
result; an unsuccessful transaction rolls back its claim and may be retried.

Successful keys are currently retained indefinitely; there is no automatic expiry
or cleanup. Do not reuse them for later checkouts. Any future retention limit must
be documented because deleting a key ends its duplicate-request protection.
