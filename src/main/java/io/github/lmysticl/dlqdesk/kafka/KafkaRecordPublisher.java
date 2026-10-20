package io.github.lmysticl.dlqdesk.kafka;

import io.github.lmysticl.dlqdesk.inbox.PayloadCodec;
import io.github.lmysticl.dlqdesk.replay.RecordPublisher;
import io.github.lmysticl.dlqdesk.replay.ReplayRecords;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.stereotype.Component;

@Component
public final class KafkaRecordPublisher implements RecordPublisher, AutoCloseable {
  private final KafkaProducer<byte[], byte[]> producer;

  public KafkaRecordPublisher(KafkaClients clients) {
    producer = new KafkaProducer<>(clients.producer());
  }

  @Override
  public ReplayRecords.Delivery publish(String target, ReplayRecords.StoredEntry entry)
      throws Exception {
    var headers = new RecordHeaders();
    entry
        .headers()
        .forEach(header -> headers.add(header.name(), PayloadCodec.decode(header.base64())));
    headers.add("dlqdesk.run-id", entry.runId().getBytes(StandardCharsets.UTF_8));
    headers.add("dlqdesk.source", entry.source().id().getBytes(StandardCharsets.UTF_8));
    headers.add("dlqdesk.source-topic", entry.sourceTopic().getBytes(StandardCharsets.UTF_8));
    var record =
        new ProducerRecord<byte[], byte[]>(
            target,
            null,
            null,
            PayloadCodec.decode(entry.keyBase64()),
            PayloadCodec.decode(entry.payloadBase64()),
            headers);
    var acknowledged = producer.send(record).get(20, TimeUnit.SECONDS);
    return new ReplayRecords.Delivery(
        acknowledged.partition(), Long.toString(acknowledged.offset()));
  }

  @Override
  public void close() {
    producer.close(Duration.ofSeconds(3));
  }
}
