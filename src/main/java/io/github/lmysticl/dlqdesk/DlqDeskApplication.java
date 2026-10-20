package io.github.lmysticl.dlqdesk;

import io.github.lmysticl.dlqdesk.config.DeskProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DeskProperties.class)
public class DlqDeskApplication {
  public static void main(String[] args) {
    SpringApplication.run(DlqDeskApplication.class, args);
  }
}
