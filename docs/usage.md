# Using DLQDesk

## Demo

Start the broker with `docker compose up -d --wait`, then start the release JAR
with `--spring.profiles.active=demo --dlqdesk.bootstrap-servers=localhost:59092`.
The broker port is bound to localhost. Connect at `http://localhost:8092/` using
the public demo token shortcut.

Each **Add 12 demo records** action creates the configured topics if absent and
adds twelve synthetic records. Both topics have two partitions when newly created.
One record in partition 1 has invalid JSON; the others contain provider-timeout
headers. Seeding adds records; it does not reset a broker or delete topics.

Select an offset to inspect its payload and original headers. Repair malformed
JSON and add it to the selection. A selection spans partitions and holds at most
100 records. Choose 1–100 records/s, then **Create preview**. Preview rereads the
source coordinates, rejects changed fingerprints and freezes output bytes.
It does not publish anything.

Review a plan and inspect any entry to compare output/original bytes and hashes.
**Start replay** opens a confirmation. **Pause replay** waits for the current
send's recorded outcome. **Resume pending** sends only PENDING entries, skipping
SENT and reviewed SKIPPED entries.

## Deployment

Run without the demo profile and provide external configuration:

```properties
dlqdesk.api-token=${DLQDESK_API_TOKEN}
dlqdesk.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS}
dlqdesk.dlt-topic=${DLQDESK_DLT_TOPIC}
dlqdesk.target-topic=${DLQDESK_TARGET_TOPIC}
spring.datasource.url=jdbc:h2:file:/absolute/protected/path/ledger
```

The token must contain at least 32 characters. API handlers require bearer
authentication. The UI is public and stores a supplied token in tab-scoped session
storage. Reloading the tab prefills it; disconnect removes it.

For Kafka TLS/SASL, supply standard client properties through an external
`dlqdesk.kafka-properties` map. The adapter forces the configured bootstrap servers,
byte-array serializers, `acks=all`, idempotence, one in-flight producer request,
no consumer group, no auto commits and no automatic source-topic creation.
Keep credentials outside Git. Use Kafka ACLs and a trusted TLS proxy for remote access.

Run one application instance per ledger. Do not share the file database across
instances or edit replay tables by hand. H2's file locking is a guard, not a
distributed replay lease. Back up a stopped application and its database files.
Original and edited payloads remain in the ledger; v0.2.0 does not prune them.

## Delivery semantics and recovery

SENT records a broker acknowledgement and destination coordinates. It is not a
business-processing acknowledgement. A timeout can mean either no delivery or
delivery with a lost response. Therefore a publish failure marks the current
entry UNKNOWN and stops the run. Startup similarly turns an interrupted SENDING
entry into UNKNOWN and the run into INTERRUPTED. Confirmed SENT entries are retained.

Open the uncertain entry. Inspect the destination using `dlqdesk.run-id`,
`dlqdesk.source-topic` and `dlqdesk.source`, along with the key and output hash.
Record a reason and **Record review and skip entry**. That action never sends the
entry. Pending entries can then resume. If inspection shows the uncertain record
needs another send, create and review a new preview from the source; doing so is
an operator decision that can produce a duplicate. The original ledger remains.

Source coordinates are processed in partition/offset order. Target partitioning
uses the original key and Kafka's normal producer partitioner; the original
partition and timestamp are not forced onto the target. A record's original
headers, including binary/null header values, are preserved. Added provenance
headers identify this replay. Source-topic data and production consumer offsets
are never edited by inspection or replay.

## API

Routes are relative to `/api`. Use `Authorization: Bearer <token>`. Errors use
HTTP Problem Details. Kafka offsets are decimal strings, including values beyond
JavaScript's safe integer range.

| Method | Route | Purpose |
|---|---|---|
| GET | `/info` | Version, configured routing and limits; does not require a healthy broker |
| GET | `/partitions` | Source partitions |
| GET | `/inbox?partition=0&offset=0&limit=25` | Bounded page; end offset is exclusive |
| GET | `/inbox/{partition}/{offset}` | Payload, headers and source fingerprint |
| POST | `/previews` | Freeze `{rate, selections:[{partition,offset,expectedHash,editedJson}]}` |
| GET | `/replays` | 50 most recent plans |
| GET | `/replays/{id}` | Plan, entry outcomes and 30 most recent audit events |
| GET | `/replays/{id}/entries/{index}` | Frozen original/output payload and headers |
| POST | `/replays/{id}/start` | Start a DRAFT |
| POST | `/replays/{id}/pause` | Request a cooperative pause |
| POST | `/replays/{id}/resume` | Resume PAUSED/INTERRUPTED/FAILED with no UNKNOWN entries |
| POST | `/replays/{id}/entries/{index}/review` | Review UNKNOWN and mark SKIPPED; body `{reason}` |

401 indicates a token problem, 400 invalid input, 404 unavailable metadata,
409 a stale/conflicting command, and 503 an unavailable or busy inbox. Four
concurrent Kafka reads are allowed. A preview requires unique coordinates and
matching SHA256 fingerprints. Routing changes after preview block start.

## Record limits

At most 100 selected records, 1–100 starts/s, 256 KiB per value/key, and at most
128 headers totalling 256 KiB. Oversized records are marked unsupported and
cannot be replayed with silently truncated keys or headers. Invalid JSON and
binary values within the size limit need a valid JSON replacement. Tombstones
are unsupported. Kafka fetch limits are not a hard broker-side record-size
guarantee; configure broker record limits for your workload.
