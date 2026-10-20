package io.github.lmysticl.dlqdesk.kafka;

import io.github.lmysticl.dlqdesk.DeskFailure;
import io.github.lmysticl.dlqdesk.DeskFailure.Kind;
import io.github.lmysticl.dlqdesk.config.DeskProperties;
import io.github.lmysticl.dlqdesk.inbox.InboxReader;
import io.github.lmysticl.dlqdesk.inbox.InboxRecords;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.CloseOptions;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

@Component
public final class KafkaInbox implements InboxReader {
  private static final Duration POLL = Duration.ofMillis(250);
  private static final Duration BROWSE_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration LOAD_TIMEOUT = Duration.ofSeconds(7);

  private final KafkaClients clients;
  private final DeskProperties settings;
  private final RecordCodec codec;
  private final Semaphore readers = new Semaphore(4);

  public KafkaInbox(KafkaClients clients, DeskProperties settings, RecordCodec codec) {
    this.clients = clients;
    this.settings = settings;
    this.codec = codec;
  }

  @Override
  public List<Integer> partitions() {
    return read(
        consumer ->
            consumer.partitionsFor(settings.dltTopic(), Duration.ofSeconds(5)).stream()
                .map(partition -> partition.partition())
                .sorted()
                .toList());
  }

  @Override
  public InboxRecords.Page browse(int partition, String offset, int limit) {
    if (partition < 0 || limit < 1 || limit > 100)
      throw new IllegalArgumentException("Partition must be non-negative; page size must be 1–100");
    return read(
        consumer -> {
          var topic = partitionOf(partition);
          consumer.assign(List.of(topic));
          long beginning = consumer.beginningOffsets(List.of(topic)).get(topic);
          long end = consumer.endOffsets(List.of(topic)).get(topic);
          long start =
              offset == null
                  ? beginning
                  : new InboxRecords.Reference(partition, offset).longOffset();
          if (start < beginning || start > end)
            throw new IllegalArgumentException(
                "Offset is outside the retained range " + beginning + "–" + end);
          consumer.seek(topic, start);
          var window = readWindow(consumer, topic, start, end, limit);
          return new InboxRecords.Page(
              partition,
              Long.toString(beginning),
              Long.toString(end),
              Long.toString(window.next()),
              window.messages());
        });
  }

  @Override
  public Map<InboxRecords.Reference, InboxRecords.Source> load(
      List<InboxRecords.Reference> references) {
    if (references.isEmpty() || references.size() > 100)
      throw new IllegalArgumentException("Choose 1–100 records");
    return read(
        consumer -> {
          var pending = pendingByPartition(references);
          consumer.assign(pending.keySet());
          var result = fetch(consumer, references, pending);
          if (result.size() != references.size())
            throw new DeskFailure(
                Kind.NOT_FOUND,
                "Some selected offsets are no longer available. Refresh the inbox.");
          return Map.copyOf(result);
        });
  }

  /** A page of summaries and the offset to continue from. */
  private record Window(List<InboxRecords.Summary> messages, long next) {}

  private Window readWindow(
      KafkaConsumer<byte[], byte[]> consumer,
      TopicPartition topic,
      long start,
      long end,
      int limit) {
    var messages = new ArrayList<InboxRecords.Summary>();
    long next = start;
    long deadline = System.nanoTime() + BROWSE_TIMEOUT.toNanos();
    while (messages.size() < limit && next < end && System.nanoTime() < deadline) {
      for (var record : consumer.poll(POLL)) {
        if (messages.size() >= limit) break;
        messages.add(codec.summary(codec.source(record)));
        next = record.offset() + 1;
      }
      // Nothing returned yet: follow the position past compacted offsets and markers.
      if (messages.isEmpty()) next = consumer.position(topic);
    }
    return new Window(List.copyOf(messages), next);
  }

  /** The requested offsets of each partition, lowest first. */
  private Map<TopicPartition, List<InboxRecords.Reference>> pendingByPartition(
      List<InboxRecords.Reference> references) {
    var pending = new HashMap<TopicPartition, List<InboxRecords.Reference>>();
    for (var reference : references)
      pending
          .computeIfAbsent(partitionOf(reference.partition()), ignored -> new ArrayList<>())
          .add(reference);
    pending
        .values()
        .forEach(list -> list.sort(Comparator.comparingLong(InboxRecords.Reference::longOffset)));
    return pending;
  }

  /** Seeks each partition to its lowest pending offset and polls until all are found. */
  private Map<InboxRecords.Reference, InboxRecords.Source> fetch(
      KafkaConsumer<byte[], byte[]> consumer,
      List<InboxRecords.Reference> references,
      Map<TopicPartition, List<InboxRecords.Reference>> pending) {
    var found = new LinkedHashMap<InboxRecords.Reference, InboxRecords.Source>();
    long deadline = System.nanoTime() + LOAD_TIMEOUT.toNanos();
    while (found.size() < references.size() && System.nanoTime() < deadline) {
      pending.forEach(
          (partition, offsets) -> {
            if (!offsets.isEmpty()) consumer.seek(partition, offsets.getFirst().longOffset());
          });
      for (var record : consumer.poll(POLL)) {
        var source = codec.source(record);
        if (references.contains(source.reference())) {
          found.put(source.reference(), source);
          pending.get(partitionOf(record.partition())).remove(source.reference());
        }
      }
    }
    return found;
  }

  private TopicPartition partitionOf(int partition) {
    return new TopicPartition(settings.dltTopic(), partition);
  }

  private <T> T read(Function<KafkaConsumer<byte[], byte[]>, T> operation) {
    if (!readers.tryAcquire())
      throw new DeskFailure(Kind.UNAVAILABLE, "Inbox is busy. Retry in a moment.");
    KafkaConsumer<byte[], byte[]> consumer = null;
    try {
      consumer = new KafkaConsumer<>(clients.consumer());
      return operation.apply(consumer);
    } catch (KafkaException failure) {
      throw new DeskFailure(
          Kind.UNAVAILABLE,
          "Kafka could not complete this read. Check broker connectivity and permissions.",
          failure);
    } finally {
      try {
        if (consumer != null) consumer.close(CloseOptions.timeout(Duration.ofMillis(500)));
      } finally {
        readers.release();
      }
    }
  }
}
