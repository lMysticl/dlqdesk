# DLQDesk

Use the documented single-instance contract. Keep demo data separate from production
adapters. Java owns execution state; the React client renders API projections.

## Verify your work

From ui/: `npm ci`, `npm run check`, `npm test`, `npm run build`.
From repository root after the UI build: `./mvnw -B verify` (`mvnw.cmd` on Windows).
DLQDesk additionally requires `./mvnw -B verify -Pintegration` with Docker running.
Inspect skipped tests; a skipped Kafka integration is not proof of compatibility.
Exercise actual built JARs for end-to-end UI checks. Never replace integration
failures with mocked success. Keep secrets and local data outside Git.
