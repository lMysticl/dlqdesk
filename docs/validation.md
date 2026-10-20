# Validation map

From `ui/`: `npm ci`, `npm run check`, `npm test`, `npm run build`.
From the root: `./mvnw -B verify -Pintegration` with Docker available.
Use `mvnw.cmd` on Windows. Without `-Pintegration`, the nine policy/persistence
tests run, but the real broker contract is not qualified by that invocation.

| Gate | Evidence |
|---|---|
| Replay policy, 5 JVM tests | Acknowledgement-aware pause, resume without resending SENT, uncertain delivery blocks retry, rate pacing, conflicting/routing commands |
| Record codec, 3 JVM tests | Binary/null headers, source fingerprint changes, exact 64-bit offsets, oversize rejection, JSON validation |
| File ledger, 1 JVM test | A new database/store instance reopens persistent H2 and retains SENT while recovering SENDING as UNKNOWN |
| Real Kafka + HTTP, 2 JVM tests | Actual edited replay, byte-preserved headers, unchanged production-group offsets, preview performs no publish, validation and resolved-endpoint authentication |
| React, 3 tests | Stale polling cancellation/recovery; repaired JSON keeps original fingerprint and exact source offset |
| Build quality | TypeScript strict, Prettier, Vite, compiler deprecation errors, Spotless |
| Browser journey | Packaged app, two partitions, JSON repair, frozen preview, confirmation, actual acknowledgements, desktop/mobile layout |

CI has Ubuntu/Windows and Java 21/25 jobs. The Ubuntu Java 21 job includes a real
Apache Kafka 4.1.1 Testcontainer. A missing Docker daemon fails that gate; the
test is not marked optional or silently skipped. Counts here describe unique
test cases rather than the sum of matrix executions.

Release notes bind the commit, CI and downloadable SHA256 checksums. Public
assets are smoke-tested in fresh data directories. These checks do not establish
multi-node safety, downstream processing success, production scale or support
for untested brokers, databases and browser engines.

Run the committed desktop/mobile browser scenarios with `npm run test:browser` from
`ui/` after building the JAR. Install the matching Chromium with `npx playwright install chromium`.
Start the local broker with `docker compose up -d --wait` first.
