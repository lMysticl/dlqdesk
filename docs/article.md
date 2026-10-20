# A Kafka replay button needs a ledger

Inspecting a dead letter is straightforward. Publishing it again becomes harder when the payload needs repair, the process stops between a broker acknowledgement and a database update, or an operator wants to resume only the records that remain pending.

DLQDesk is a small self-hosted Java and React workspace for that sequence: inspect → repair → freeze a preview → explicitly publish → inspect acknowledgements. It is not a general Kafka administration console.

![Actual inbox and payload editor](assets/workspace.png)

## Run the exercise

Clone [the source](https://github.com/lMysticl/dlqdesk), start the included local broker with `docker compose up -d --wait`, and download [dlqdesk-0.2.0.jar](https://github.com/lMysticl/dlqdesk/releases/tag/v0.2.0). With Java 21 or 25:

```sh
java -jar dlqdesk-0.2.0.jar --spring.profiles.active=demo --dlqdesk.bootstrap-servers=localhost:59092
```

Open `http://localhost:8092/` and use the bundled local demo token. Add the 12 synthetic records, inspect partition 1 and repair its malformed JSON. Select records and create a preview. Nothing has been published at that point. Review the frozen payload, target topic and rate, then confirm publication. At one record per second the pause and resume behavior is easy to observe.

Each click on the seed action adds records; it does not reset an existing topic. Production operation uses your own token, an explicitly configured source topic and an allowlisted destination.

## Inspection should not move production offsets

The inbox uses manual partition assignment and does not join a consumer group or commit offsets. It pages through exact decimal-string offsets, preserving values beyond JavaScript's safe integer range. An integration test compares source data and committed group offsets before and after the workflow. Browsing a dead-letter topic should not quietly change how another application consumes it.

The source record remains immutable. A repair is an edited output payload associated with its original fingerprint, key and headers. Before saving a preview, the server reloads the selected source coordinates and checks their fingerprints. The preview freezes the output bytes and routing so the later publish action has a reviewable input.

## Acknowledged, pending and uncertain are different states

The worker records `SENDING` before attempting publication and `SENT` only after the broker acknowledges it. The Kafka operation happens outside the short database transaction. This avoids holding a database transaction through broker waits, but it also makes the acknowledgement/database boundary explicit.

If the process disappears in that interval, a restart cannot infer whether the destination accepted the record. The ledger marks that entry `UNKNOWN`. Resume preserves confirmed `SENT` entries and sends only pending work; it is blocked while uncertain entries remain. An operator must inspect the destination and record an explicit reviewed decision. There is no automatic resend of an uncertain entry and no claim of exactly-once end-to-end processing.

`SENT` means the broker acknowledged publication. It does not mean a downstream service processed the record successfully. That distinction remains visible beside the action controls.

![Actual replay ledger and audit trail](assets/replay.png)

## A bounded recovery tool

The current limit is 100 selected records and a configured rate of 1–100 records per second, with one replay worker per application instance. Pause waits for the current in-flight acknowledgement. Durable H2/Flyway storage preserves the run and audit trail. The tests include real Kafka publication, original and edited JSON, binary/null headers, source fingerprint changes, persistent recovery and uncertain delivery review.

The January 2026 [community question about DLQ repair and replay](https://www.reddit.com/r/apachekafka/comments/1q1hg5l/how_do_you_handle_dlq_fix_replay/) describes the practical workflow this tool addresses. Existing Kafka tools also offer replay features. The narrower contribution here is the reviewable preview and explicit persistent acknowledgement ledger, not a claim that replay tools do not exist.

One adjacent [header-filtering request in Kafbat](https://github.com/kafbat/kafka-ui/issues/1810) is not solved by this release: DLQDesk preserves original headers and adds its provenance headers. Deployments that need to remove replication headers need a separate policy; this article should not be posted as a solution to that request.

Source, demo and MIT license: [github.com/lMysticl/dlqdesk](https://github.com/lMysticl/dlqdesk). Feedback on interruption recovery and reproducible broker integration problems is more useful than a generic feature wishlist. The [validation map](validation.md) states the tested versions and boundaries.

*Publication disclosure: AI assistance was used in implementation and drafting. Behavior claims above have executable checks and retained release verification; this article should receive the author's final review before external publication.*
