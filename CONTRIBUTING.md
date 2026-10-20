# Contributing to DLQDesk

Start with a reproducible issue or a small change. For a design change, explain
the operator's problem and the behavior you want to preserve.

Build with JDK 21 or 25 and Node 24.15+ (24.x). From `ui/`, run `npm ci`,
`npm run check`, `npm test`, and `npm run build`. From the root, run
`./mvnw -B verify` (`mvnw.cmd -B verify` in PowerShell).
Run `./mvnw -B verify -Pintegration` with Docker for the real Kafka contract.

Java formatting: `./mvnw spotless:apply`. Frontend formatting: `npm run format`
from `ui/`. Keep transport adapters, execution policy, persistence and React state
owners separate. Prefer behavior tests for failure and recovery over mocks of
implementation details. Never include tokens, real customer records or local databases.

By contributing, you agree that your contribution is available under the MIT license.
