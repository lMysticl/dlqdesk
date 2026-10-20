package io.github.lmysticl.dlqdesk.demo;

import io.github.lmysticl.dlqdesk.config.DeskProperties;
import io.github.lmysticl.dlqdesk.kafka.KafkaClients;
import io.github.lmysticl.dlqdesk.web.DeskEndpoint;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;

@DeskEndpoint
@Profile("demo")
@ConditionalOnProperty(name = "dlqdesk.demo", havingValue = "true")
public final class DemoFixture {
  private final KafkaClients clients;
  private final DeskProperties settings;

  public DemoFixture(KafkaClients clients, DeskProperties settings) {
    this.clients = clients;
    this.settings = settings;
  }

  @PostMapping("/api/demo/seed")
  public Map<String, Integer> seed() throws Exception {
    try (var admin = Admin.create(clients.producer())) {
      for (String topic : List.of(settings.dltTopic(), settings.targetTopic())) {
        try {
          admin
              .createTopics(List.of(new NewTopic(topic, 2, (short) 1)))
              .all()
              .get(10, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
          if (!(failure.getCause() instanceof TopicExistsException)) throw failure;
        }
      }
    }
    var producer = new KafkaProducer<byte[], byte[]>(clients.producer());
    try {
      for (int i = 0; i < 12; i++) {
        var record =
            new ProducerRecord<byte[], byte[]>(
                settings.dltTopic(),
                i % 2,
                bytes("order-" + (1040 + i)),
                bytes(
                    i == 5
                        ? "{\"orderId\":1045,\"amount\":}"
                        : "{\"orderId\":"
                            + (1040 + i)
                            + ",\"amount\":49.90,\"currency\":\"USD\"}"));
        record
            .headers()
            .add(
                "kafka_dlt-exception-message",
                bytes(
                    i == 5
                        ? "Invalid amount: JSON decoding failed"
                        : "Payment provider timed out after 3 attempts"));
        record.headers().add("kafka_dlt-original-topic", bytes(settings.targetTopic()));
        record.headers().add("trace-id", bytes("demo-trace-" + i));
        producer.send(record).get(10, TimeUnit.SECONDS);
      }
    } finally {
      producer.close(Duration.ofSeconds(3));
    }
    return Map.of("added", 12);
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
