package io.github.lmysticl.dlqdesk.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dlqdesk")
public record DeskProperties(
    String apiToken,
    String bootstrapServers,
    String dltTopic,
    String targetTopic,
    boolean demo,
    Map<String, String> kafkaProperties) {
  public DeskProperties {
    if (apiToken == null || apiToken.length() < 32) {
      throw new IllegalArgumentException("dlqdesk.api-token must contain at least 32 characters");
    }
    if (bootstrapServers == null || bootstrapServers.isBlank()) {
      throw new IllegalArgumentException("Kafka bootstrap servers are required");
    }
    if (!validTopic(dltTopic) || !validTopic(targetTopic) || dltTopic.equals(targetTopic)) {
      throw new IllegalArgumentException("Configure distinct, valid DLT and target topics");
    }
    kafkaProperties = kafkaProperties == null ? Map.of() : Map.copyOf(kafkaProperties);
  }

  private static boolean validTopic(String topic) {
    return topic != null && topic.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,248}");
  }

  @Override
  public String toString() {
    return "DeskProperties[credentials redacted]";
  }
}
