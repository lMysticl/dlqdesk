package io.github.lmysticl.dlqdesk.replay;

import io.github.lmysticl.dlqdesk.inbox.InboxRecords;
import java.util.List;

public final class ReplayRecords {
  private ReplayRecords() {}

  public enum RunState {
    DRAFT,
    RUNNING,
    PAUSE_REQUESTED,
    PAUSED,
    COMPLETED,
    INTERRUPTED,
    FAILED
  }

  public enum EntryState {
    PENDING,
    SENDING,
    SENT,
    UNKNOWN,
    SKIPPED
  }

  public record Selection(int partition, String offset, String expectedHash, String editedJson) {
    public InboxRecords.Reference reference() {
      return new InboxRecords.Reference(partition, offset);
    }
  }

  public record Frozen(
      InboxRecords.Source original, String payloadBase64, String payloadHash, boolean edited) {}

  public record StoredEntry(
      String runId,
      int index,
      String sourceTopic,
      InboxRecords.Reference source,
      String keyBase64,
      String originalPayloadBase64,
      String payloadBase64,
      List<InboxRecords.Header> headers) {}

  public record Delivery(int partition, String offset) {}

  public record EntryView(
      int index,
      InboxRecords.Reference source,
      String key,
      String sourceHash,
      String payloadHash,
      boolean edited,
      EntryState state,
      Integer targetPartition,
      String targetOffset,
      String error) {}

  public record Audit(String timestamp, String action, String detail) {}

  public record RunSummary(
      String id,
      RunState state,
      String createdAt,
      String updatedAt,
      String sourceTopic,
      String targetTopic,
      int rate,
      int total,
      int sent,
      int pending,
      int inFlight,
      int unknown,
      int skipped) {}

  public record RunView(RunSummary run, List<EntryView> entries, List<Audit> audit) {}

  public record FrozenView(
      String key,
      String originalPayload,
      String outputPayload,
      List<InboxRecords.Header> headers) {}
}
