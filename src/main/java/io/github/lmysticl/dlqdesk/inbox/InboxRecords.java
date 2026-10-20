package io.github.lmysticl.dlqdesk.inbox;

import java.util.List;

public final class InboxRecords {
  private InboxRecords() {}

  public record Reference(int partition, String offset) {
    public Reference {
      if (partition < 0 || offset == null || !offset.matches("(0|[1-9][0-9]{0,18})")) {
        throw new IllegalArgumentException(
            "A non-negative partition and decimal offset are required");
      }
      try {
        Long.parseLong(offset);
      } catch (NumberFormatException failure) {
        throw new IllegalArgumentException("Offset exceeds Kafka's signed 64-bit range", failure);
      }
    }

    public long longOffset() {
      return Long.parseLong(offset);
    }

    public String id() {
      return partition + ":" + offset;
    }
  }

  public record Header(String name, String base64, String text) {}

  public record Source(
      Reference reference,
      String keyBase64,
      String keyText,
      String payloadBase64,
      String payloadText,
      long timestamp,
      List<Header> headers,
      String sourceHash,
      boolean validJson,
      String problem) {
    public Source {
      headers = List.copyOf(headers);
    }
  }

  public record Summary(
      Reference reference,
      String key,
      long timestamp,
      String sourceHash,
      boolean validJson,
      String problem,
      String error) {}

  public record Page(
      int partition,
      String beginningOffset,
      String endOffset,
      String nextOffset,
      List<Summary> messages) {}
}
