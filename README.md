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

Then run `OrderServiceApplication` from IntelliJ. The application connects to
`jdbc:postgresql://localhost:5432/order_db` with username `order_user` and password
`order_password`. These credentials are for local development. Override the application
connection with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` when needed.

Database data persists in a Docker volume. Stop PostgreSQL while keeping the data:

```sh
docker compose -f infrastructure/docker-compose.yml down
```

This setup creates the database only; application tables require migrations or a
separate Hibernate schema-generation configuration.
