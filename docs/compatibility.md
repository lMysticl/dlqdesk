# Compatibility contract

| Component | Supported and tested scope |
|---|---|
| Runtime | Java 21 and 25; Java 21 bytecode |
| Spring Boot | 4.1.1 |
| Kafka client | 4.2.1 from the Boot BOM |
| Replay database | H2 2.4.240 in this release |
| UI | React 19.3.1, TypeScript 5.9.3 |
| Build | Maven Wrapper 3.9.11; Node 24.15+ / 24.x |
| CI OS | Ubuntu and Windows, Java 21/25 |
| Broker integration | Apache Kafka 4.1.1 in Docker on Linux / Java 21 |

The frontend build uses a pinned lockfile. The Maven BOM owns dependency versions.
The Maven distribution checksum and GitHub Action commit SHAs are pinned.
Spotless uses google-java-format 1.36.0: 1.37.0 changed the formatter Style API
incompatibly with the current Spotless adapter.

The browser UI is verified with Chromium on desktop and mobile viewports.
Firefox, Safari, other Spring versions, databases and broker versions
have not been qualified by this release. A dependency's wider compatibility is
not presented as our test coverage.
