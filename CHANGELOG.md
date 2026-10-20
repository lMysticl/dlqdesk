# Changelog

## 0.3.0 — 2026-10-20

- Run the whole demo with one command: `docker compose --profile app up -d --build --wait`
  builds DLQDesk from source and starts it next to the local Kafka broker.
- Publish a container image to `ghcr.io/lmysticl/dlqdesk` with each release.
- Release assets are built and checksummed by a tag-driven workflow.
- Documentation no longer contains launch drafts or process notes.

## 0.2.0 — 2026-10-20

- Porcelain & Vermilion: a new editorial workspace with horizontal navigation, distinct product identity and locally bundled OFL-licensed Geist typography.
- A source-message index and source/review/target route, a plum JSON editor, horizontal recent plans and a delivery ledger beside its audit trail.
- Responsive keyboard-accessible layouts, visible focus, a skip link and product favicons.
- New repository covers, actual application captures and a documented design contract.
- Existing API and recovery semantics are preserved; v0.1.0 remains available.

## 0.1.0 — 2026-10-20

- Inspect a configured Kafka dead-letter topic using manually assigned consumers.
- Preserve exact source offsets, binary keys and headers within documented limits.
- Repair JSON and freeze a fingerprint-checked preview before any publish.
- Replay with bounded concurrency, rate pacing and an H2 acknowledgement ledger.
- Pause/resume pending entries; retain uncertain delivery for explicit review.
- Include an authenticated synthetic-data demo, React workspace, MIT license,
  reproducible builds and desktop/mobile browser scenarios.

See the release verification record and CI for the tested commit and platforms.
