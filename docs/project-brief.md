# Engineering brief

DLQDesk is an open-source Java and React project by [Pavel Putrenkov](https://github.com/lMysticl).

**Problem.** A dead-letter record needs inspection, a controlled repair, and a replay outcome that remains understandable after interruption.

**Implementation.** Java 21, Spring Boot 4.1, Apache Kafka clients, JDBC transactions, Flyway and a persistent replay ledger, React 19, TypeScript, Vite, REST APIs and bearer-token access.

**Engineering decisions.** Network sends happen outside SQL transactions. One bounded worker owns replay. Confirmed sends survive restart; uncertain outcomes require a recorded review.

**Evidence.** Tests and reproducible demo scenarios are listed in
[validation](validation.md). No production adoption, customer count, traffic,
revenue or performance improvement is claimed for this new project.

**Portfolio wording.** Built DLQDesk, a self-hosted Kafka dead-letter recovery console with a React/TypeScript UI; implemented rate-limited replay, durable acknowledgement tracking and interruption recovery and verified the behavior with integration tests and CI on Java 21/25, Linux and Windows.

This wording describes the project. It is not a claim of commercial employment or production scale.
