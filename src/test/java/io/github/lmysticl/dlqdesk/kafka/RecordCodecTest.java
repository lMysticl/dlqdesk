package io.github.lmysticl.dlqdesk.kafka;

import static org.junit.jupiter.api.Assertions.*;

import io.github.lmysticl.dlqdesk.inbox.InboxRecords;
import io.github.lmysticl.dlqdesk.inbox.PayloadCodec;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RecordCodecTest {
  private final PayloadCodec payloads = new PayloadCodec(JsonMapper.builder().build());
  private final RecordCodec codec = new RecordCodec(payloads);

  @Test
  void binaryHeadersAndNullKeyAreLossless() {
    var record = record("{}");
    record.headers().add("binary", new byte[] {0, -1, 17}).add("nullable", null);
    var source = codec.source(record);
    assertTrue(source.validJson());
    assertNull(source.keyBase64());
    assertArrayEquals(
        new byte[] {0, -1, 17}, PayloadCodec.decode(source.headers().getFirst().base64()));
    assertNull(source.headers().getFirst().text());
    assertNull(source.headers().get(1).base64());
    String before = source.sourceHash();
    record.headers().add("trace", new byte[] {1});
    assertNotEquals(before, codec.source(record).sourceHash());
  }

  @Test
  void unsupportedRecordsCannotBeSilentlyTruncated() {
    var large = record("x".repeat(PayloadCodec.MAX_BYTES + 1));
    assertNull(codec.source(large).payloadBase64());
    var headers = record("{}");
    for (int i = 0; i < 129; i++) headers.headers().add("h", new byte[] {1});
    assertNull(codec.source(headers).payloadBase64());
    assertFalse(codec.source(record("broken")).validJson());
    assertThrows(IllegalArgumentException.class, () -> payloads.checkedJson("{bad"));
    assertFalse(payloads.isJson("{} {}"), "A payload must contain exactly one JSON value");
  }

  @Test
  void offsetsStayExactBeyondJavascriptIntegerRange() {
    var reference = new InboxRecords.Reference(1, "9007199254740993");
    assertEquals(9007199254740993L, reference.longOffset());
    assertThrows(IllegalArgumentException.class, () -> new InboxRecords.Reference(0, "01"));
    assertThrows(
        IllegalArgumentException.class, () -> new InboxRecords.Reference(0, "9223372036854775808"));
  }

  private ConsumerRecord<byte[], byte[]> record(String value) {
    return new ConsumerRecord<>("orders.DLT", 0, 12, null, value.getBytes(StandardCharsets.UTF_8));
  }
}
