# 🧭 Transaction Insight

A production-grade lab for mastering transaction management from the storage engine to distributed systems. The project is organised as a multi-module Spring Boot workspace so that every topic can be explored in isolation while still sharing common infrastructure such as datasource, Kafka and Redis configuration.

---

## 🏗️ Architecture Overview

```
transaction-insight/
├── common-infrastructure/      # Shared datasource, Redis, Kafka & test fixtures
├── tx-foundation/              # MySQL isolation, MVCC visualisation and deadlock labs
├── tx-spring-core/             # Spring @Transactional behaviour and performance experiments
├── tx-distributed-patterns/    # Transactional outbox, Saga notes and custom TCC framework
├── tx-monitoring/              # Transaction metrics & slow transaction alerting
└── tx-chaos-engineering/       # Chaos experiments (network partitions, failure injection)
```

Each module is a Spring Boot application (or library) with its own domain model and tests. The layout mirrors the interview-ready curriculum described in the project brief so you can jump directly to the scenario that interests you.

---

## 🛠️ Core Technology Stack

| Layer | Technology | Notes |
|-------|------------|-------|
| Language | Java 17 | Allows usage of records, switch pattern matching and virtual threads in experiments |
| Framework | Spring Boot 3.3.x | Dependency management for all modules |
| Database | MySQL 8 (Tested with H2 compatibility) | Isolation, MVCC and deadlock reproductions |
| Messaging | Apache Kafka 4 ready configuration | Used by the transactional outbox relay |
| Cache | Redis (Lettuce) | Shared caching and idempotency storage |
| Distributed Tx | Custom TCC manager + transactional outbox | Demonstrates compensating transactions |
| Observability | Micrometer + Prometheus | AOP aspect records transaction timings |
| Testing | JUnit 5, Spring Boot Test (H2) | Foundation for reproducible experiments without Docker |

---

## 📦 Module Highlights

### `common-infrastructure`
Shared configuration for datasource, Redis and Kafka that matches the reference YAML from the prompt. All services import this module to ensure consistent connectivity settings.

### `tx-foundation`
Hands-on experiments for InnoDB internals:
- Dirty read verification using two physical connections under `READ_UNCOMMITTED`.
- MVCC snapshot capture via a simple visualiser service.
- Deadlock reproduction utility that intentionally cross-locks resources.

### `tx-spring-core`
Deep dives into Spring transaction semantics:
- `PropagationLabService` benchmarks `REQUIRED` vs `REQUIRES_NEW` batches.
- Self-invocation experiment showing proxy-based transaction loss.
- Tests rely on H2 to keep feedback fast.

### `tx-distributed-patterns`
Modern microservice patterns:
- Transactional outbox implementation with scheduled relay to Kafka.
- Lightweight TCC transaction manager with an in-memory account example.
- Spring Boot tests cover message persistence and TCC compensation.

### `tx-monitoring`
An AspectJ-based Micrometer aspect that records execution time for every `@Transactional` boundary, ready to export to Prometheus / Grafana.

### `tx-chaos-engineering`
Chaos experiment scaffolding for the TCC confirm phase. The disabled sample test explains how to pair the services with external fault-injection tools (tc, Chaos Mesh, etc.).

---

## 🚀 Getting Started

```bash
# Compile all modules
mvn clean verify

# Run a specific module's tests (e.g. foundation)
mvn -pl tx-foundation test

# Launch the distributed patterns module for manual exploration
mvn -pl tx-distributed-patterns spring-boot:run
```

The default `application.yml` in `common-infrastructure` mirrors the interview-grade configuration provided in the prompt. Override credentials or hosts via standard Spring Boot property overrides when running locally.

---

## 🧪 Featured Experiment: Dirty Read Demonstration

```java
try (Connection writer = dataSource.getConnection();
     Connection reader = dataSource.getConnection()) {
    writer.setAutoCommit(false);
    reader.setAutoCommit(false);

    writer.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
    reader.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);

    updateBalance(writer, 1L, new BigDecimal("1000"));
    BigDecimal dirtyBalance = queryBalance(reader, 1L); // -> 1000 (dirty)

    writer.rollback();
    BigDecimal cleanBalance = queryBalance(reader, 1L); // -> 500 (snapshot)
}
```

This test-backed snippet is part of the `tx-foundation` module and can be executed with `mvn -pl tx-foundation test`.

---

## 📈 Observability

The monitoring module ships with `TransactionMetricsAspect`, automatically pushing Micrometer timers tagged by method signature. Hook it into Prometheus by adding the registry dependency at the application level.

---

## 🧭 Roadmap

- [x] MySQL isolation level validation suite
- [x] Spring propagation performance harness
- [x] Transactional outbox implementation
- [x] In-memory TCC framework prototype
- [ ] Full Saga choreography sample
- [ ] Seata AT/TCC integration module
- [ ] Automated chaos scenario suite with Docker orchestration

---

## 📄 License

MIT License © 2024 Transaction Insight Team
