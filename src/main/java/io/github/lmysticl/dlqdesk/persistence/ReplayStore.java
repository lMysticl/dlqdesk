package io.github.lmysticl.dlqdesk.persistence;

import io.github.lmysticl.dlqdesk.DeskFailure;
import io.github.lmysticl.dlqdesk.DeskFailure.Kind;
import io.github.lmysticl.dlqdesk.inbox.InboxRecords;
import io.github.lmysticl.dlqdesk.inbox.PayloadCodec;
import io.github.lmysticl.dlqdesk.replay.ReplayRecords;
import io.github.lmysticl.dlqdesk.replay.ReplayRecords.EntryState;
import io.github.lmysticl.dlqdesk.replay.ReplayRecords.RunState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
@DependsOnDatabaseInitialization
public class ReplayStore {
  private static final String SUMMARY =
      """
      SELECT r.*,
        (SELECT COUNT(*) FROM replay_entry e WHERE e.run_id=r.id) AS total,
        (SELECT COUNT(*) FROM replay_entry e WHERE e.run_id=r.id AND e.state='SENT') AS sent,
        (SELECT COUNT(*) FROM replay_entry e WHERE e.run_id=r.id AND e.state='PENDING') AS pending,
        (SELECT COUNT(*) FROM replay_entry e WHERE e.run_id=r.id AND e.state='SENDING') AS in_flight,
        (SELECT COUNT(*) FROM replay_entry e WHERE e.run_id=r.id AND e.state='UNKNOWN') AS unknown_count,
        (SELECT COUNT(*) FROM replay_entry e WHERE e.run_id=r.id AND e.state='SKIPPED') AS skipped
      FROM replay_run r
      """;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;
  private final JsonMapper json;

  public ReplayStore(JdbcTemplate jdbc, PlatformTransactionManager transactions, JsonMapper json) {
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(transactions);
    this.json = json;
  }

  public ReplayRecords.RunView create(
      String source, String target, int rate, List<ReplayRecords.Frozen> records) {
    String id = UUID.randomUUID().toString();
    transaction.executeWithoutResult(
        status -> {
          String now = Instant.now().toString();
          jdbc.update(
              "INSERT INTO replay_run (id,state,created_at,updated_at,source_topic,target_topic,replay_rate) VALUES (?,?,?,?,?,?,?)",
              id,
              RunState.DRAFT.name(),
              now,
              now,
              source,
              target,
              rate);
          int index = 0;
          for (var frozen : records) {
            var original = frozen.original();
            jdbc.update(
                "INSERT INTO replay_entry (run_id,entry_index,source_partition,source_offset,key_bytes,original_payload,output_payload,headers_json,source_hash,payload_hash,edited,state) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                index++,
                original.reference().partition(),
                original.reference().offset(),
                original.keyBase64(),
                original.payloadBase64(),
                frozen.payloadBase64(),
                json.writeValueAsString(original.headers()),
                original.sourceHash(),
                frozen.payloadHash(),
                frozen.edited(),
                EntryState.PENDING.name());
          }
          audit(
              id,
              "PREVIEW_CREATED",
              records.size() + " records frozen; no Kafka publish performed");
        });
    return view(id);
  }

  public List<ReplayRecords.RunSummary> recent() {
    return jdbc.query(SUMMARY + " ORDER BY r.created_at DESC LIMIT 50", this::mapSummary);
  }

  public ReplayRecords.RunSummary summary(String id) {
    var matches = jdbc.query(SUMMARY + " WHERE r.id=?", this::mapSummary, id);
    if (matches.isEmpty()) throw new DeskFailure(Kind.NOT_FOUND, "Replay plan does not exist");
    return matches.getFirst();
  }

  public ReplayRecords.RunView view(String id) {
    var run = summary(id);
    var entries =
        jdbc.query(
            "SELECT * FROM replay_entry WHERE run_id=? ORDER BY entry_index",
            (row, number) ->
                new ReplayRecords.EntryView(
                    row.getInt("entry_index"),
                    reference(row),
                    PayloadCodec.utf8(PayloadCodec.decode(row.getString("key_bytes"))),
                    row.getString("source_hash"),
                    row.getString("payload_hash"),
                    row.getBoolean("edited"),
                    EntryState.valueOf(row.getString("state")),
                    (Integer) row.getObject("target_partition"),
                    row.getString("target_offset"),
                    row.getString("error_detail")),
            id);
    var audit =
        jdbc.query(
            "SELECT * FROM replay_audit WHERE run_id=? ORDER BY id DESC LIMIT 30",
            (row, number) ->
                new ReplayRecords.Audit(
                    row.getString("created_at"), row.getString("action"), row.getString("detail")),
            id);
    return new ReplayRecords.RunView(run, entries, audit);
  }

  public ReplayRecords.FrozenView frozen(String id, int index) {
    summary(id);
    var entries =
        jdbc.query(
            "SELECT e.*,r.source_topic FROM replay_entry e JOIN replay_run r ON r.id=e.run_id WHERE e.run_id=? AND e.entry_index=?",
            this::stored,
            id,
            index);
    if (entries.isEmpty()) throw new DeskFailure(Kind.NOT_FOUND, "Replay entry does not exist");
    var entry = entries.getFirst();
    return new ReplayRecords.FrozenView(
        PayloadCodec.utf8(PayloadCodec.decode(entry.keyBase64())),
        PayloadCodec.utf8(PayloadCodec.decode(entry.originalPayloadBase64())),
        PayloadCodec.utf8(PayloadCodec.decode(entry.payloadBase64())),
        entry.headers());
  }

  public void begin(String id, Set<RunState> allowed, String expectedTarget) {
    transaction.executeWithoutResult(
        status -> {
          var state = lockedState(id);
          if (!allowed.contains(state))
            throw new DeskFailure(Kind.CONFLICT, "This replay cannot start from " + state);
          var run = summary(id);
          if (!run.targetTopic().equals(expectedTarget))
            throw new DeskFailure(
                Kind.CONFLICT, "Target configuration changed. Restore it or create a new preview.");
          if (run.unknown() > 0)
            throw new DeskFailure(
                Kind.CONFLICT,
                "Review uncertain entries before resuming. They are never automatically resent.");
          setState(id, RunState.RUNNING);
          audit(id, "STARTED", "Replay accepted at " + run.rate() + " records/s");
        });
  }

  public void requestPause(String id) {
    transaction.executeWithoutResult(
        status -> {
          var state = lockedState(id);
          if (state != RunState.RUNNING)
            throw new DeskFailure(Kind.CONFLICT, "Only a running replay can be paused");
          setState(id, RunState.PAUSE_REQUESTED);
          audit(id, "PAUSE_REQUESTED", "In-flight publish must settle before PAUSED");
        });
  }

  public Optional<ReplayRecords.StoredEntry> claimNext(String id) {
    return Objects.requireNonNull(
        transaction.execute(
            status -> {
              if (lockedState(id) != RunState.RUNNING) return Optional.empty();
              var entries =
                  jdbc.query(
                      "SELECT e.*,r.source_topic FROM replay_entry e JOIN replay_run r ON r.id=e.run_id WHERE e.run_id=? AND e.state='PENDING' ORDER BY entry_index LIMIT 1",
                      this::stored,
                      id);
              if (entries.isEmpty()) return Optional.empty();
              var entry = entries.getFirst();
              int changed =
                  jdbc.update(
                      "UPDATE replay_entry SET state='SENDING' WHERE run_id=? AND entry_index=? AND state='PENDING'",
                      id,
                      entry.index());
              if (changed != 1)
                throw new IllegalStateException("Replay entry claim lost its transaction");
              return Optional.of(entry);
            }));
  }

  public void sent(String id, int index, ReplayRecords.Delivery delivery) {
    transaction.executeWithoutResult(
        status -> {
          int changed =
              jdbc.update(
                  "UPDATE replay_entry SET state='SENT',target_partition=?,target_offset=? WHERE run_id=? AND entry_index=? AND state='SENDING'",
                  delivery.partition(),
                  delivery.offset(),
                  id,
                  index);
          if (changed != 1)
            throw new IllegalStateException("Acknowledgement did not match an in-flight entry");
          touch(id);
          audit(
              id,
              "ACKNOWLEDGED",
              "Entry "
                  + index
                  + " -> partition "
                  + delivery.partition()
                  + ", offset "
                  + delivery.offset());
        });
  }

  public void uncertain(String id, int index) {
    transaction.executeWithoutResult(
        status -> {
          jdbc.update(
              "UPDATE replay_entry SET state='UNKNOWN',error_detail=? WHERE run_id=? AND entry_index=? AND state='SENDING'",
              "A broker acknowledgement was not durably recorded. Inspect the target before continuing.",
              id,
              index);
          setState(id, RunState.FAILED);
          audit(
              id,
              "DELIVERY_UNCERTAIN",
              "Entry " + index + " requires explicit review; no automatic resend");
        });
  }

  public void settle(String id) {
    transaction.executeWithoutResult(
        status -> {
          var state = lockedState(id);
          if (state == RunState.PAUSE_REQUESTED) {
            setState(id, RunState.PAUSED);
            audit(id, "PAUSED", "No in-flight send remains");
          } else if (state == RunState.RUNNING && summary(id).pending() == 0) {
            setState(id, RunState.COMPLETED);
            audit(id, "COMPLETED", "All entries have a recorded outcome");
          }
        });
  }

  public void reviewAndSkip(String id, int index, String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 500)
      throw new IllegalArgumentException("Supply a review reason of 1–500 characters");
    transaction.executeWithoutResult(
        status -> {
          lockedState(id);
          int changed =
              jdbc.update(
                  "UPDATE replay_entry SET state='SKIPPED' WHERE run_id=? AND entry_index=? AND state='UNKNOWN'",
                  id,
                  index);
          if (changed != 1)
            throw new DeskFailure(
                Kind.CONFLICT, "Only UNKNOWN entries can be explicitly reviewed and skipped");
          touch(id);
          audit(id, "REVIEWED_AND_SKIPPED", "Entry " + index + ": " + reason.strip());
        });
  }

  public void recoverInterrupted() {
    var ids =
        jdbc.queryForList(
            "SELECT id FROM replay_run WHERE state IN ('RUNNING','PAUSE_REQUESTED')", String.class);
    ids.forEach(this::interrupt);
  }

  public void interrupt(String id) {
    transaction.executeWithoutResult(
        status -> {
          var state = lockedState(id);
          if (state == RunState.RUNNING || state == RunState.PAUSE_REQUESTED) {
            jdbc.update(
                "UPDATE replay_entry SET state='UNKNOWN',error_detail=? WHERE run_id=? AND state='SENDING'",
                "Process stopped while this entry was in flight. Inspect the target; delivery may have happened.",
                id);
            setState(id, RunState.INTERRUPTED);
            audit(
                id,
                "PROCESS_RECOVERED",
                "Confirmed SENT entries preserved; in-flight entries require review");
          }
        });
  }

  private RunState lockedState(String id) {
    var states =
        jdbc.queryForList("SELECT state FROM replay_run WHERE id=? FOR UPDATE", String.class, id);
    if (states.isEmpty()) throw new DeskFailure(Kind.NOT_FOUND, "Replay plan does not exist");
    return RunState.valueOf(states.getFirst());
  }

  private void setState(String id, RunState state) {
    jdbc.update(
        "UPDATE replay_run SET state=?,updated_at=? WHERE id=?",
        state.name(),
        Instant.now().toString(),
        id);
  }

  private void touch(String id) {
    jdbc.update("UPDATE replay_run SET updated_at=? WHERE id=?", Instant.now().toString(), id);
  }

  private void audit(String id, String action, String detail) {
    jdbc.update(
        "INSERT INTO replay_audit (run_id,created_at,action,detail) VALUES (?,?,?,?)",
        id,
        Instant.now().toString(),
        action,
        detail);
  }

  private ReplayRecords.RunSummary mapSummary(ResultSet row, int number) throws SQLException {
    return new ReplayRecords.RunSummary(
        row.getString("id"),
        RunState.valueOf(row.getString("state")),
        row.getString("created_at"),
        row.getString("updated_at"),
        row.getString("source_topic"),
        row.getString("target_topic"),
        row.getInt("replay_rate"),
        row.getInt("total"),
        row.getInt("sent"),
        row.getInt("pending"),
        row.getInt("in_flight"),
        row.getInt("unknown_count"),
        row.getInt("skipped"));
  }

  private ReplayRecords.StoredEntry stored(ResultSet row, int number) throws SQLException {
    List<InboxRecords.Header> headers =
        json.readValue(row.getString("headers_json"), new TypeReference<>() {});
    return new ReplayRecords.StoredEntry(
        row.getString("run_id"),
        row.getInt("entry_index"),
        row.getString("source_topic"),
        reference(row),
        row.getString("key_bytes"),
        row.getString("original_payload"),
        row.getString("output_payload"),
        headers);
  }

  private static InboxRecords.Reference reference(ResultSet row) throws SQLException {
    return new InboxRecords.Reference(
        row.getInt("source_partition"), row.getString("source_offset"));
  }
}
