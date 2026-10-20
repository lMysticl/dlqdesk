package io.github.lmysticl.dlqdesk.kafka;

import io.github.lmysticl.dlqdesk.config.DeskProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.stereotype.Component;

@Component
public final class KafkaClients {
  private final DeskProperties settings;

  public KafkaClients(DeskProperties settings) {
    this.settings = settings;
  }

  private Map<String, Object> base() {
    var properties = new HashMap<String, Object>(settings.kafkaProperties());
    properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, settings.bootstrapServers());
    properties.put(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, 5000);
    return properties;
  }

  public Map<String, Object> consumer() {
    var properties = base();
    properties.put(ConsumerConfig.CLIENT_ID_CONFIG, "dlqdesk-inbox-" + UUID.randomUUID());
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    properties.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
    properties.remove(ConsumerConfig.GROUP_ID_CONFIG);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none");
    properties.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000);
    properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
    properties.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 524288);
    properties.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 524288);
    return properties;
  }

  public Map<String, Object> producer() {
    var properties = base();
    properties.put(ProducerConfig.CLIENT_ID_CONFIG, "dlqdesk-replay");
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    properties.put(ProducerConfig.ACKS_CONFIG, "all");
    properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
    properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 15000);
    properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
    return properties;
  }
}
