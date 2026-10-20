package io.github.lmysticl.dlqdesk;

import static org.junit.jupiter.api.Assertions.*;

import io.github.lmysticl.dlqdesk.inbox.InboxReader;
import io.github.lmysticl.dlqdesk.inbox.InboxRecords;
import io.github.lmysticl.dlqdesk.kafka.KafkaClients;
import io.github.lmysticl.dlqdesk.replay.ReplayRecords;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.CloseOptions;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:kafka-contract;DB_CLOSE_DELAY=-1",
      "dlqdesk.api-token=integration-token-with-at-least-32-characters",
      "dlqdesk.dlt-topic=contract.DLT",
      "dlqdesk.target-topic=contract-target"
    })
class KafkaReplayIT {
  private static final String TOKEN = "integration-token-with-at-least-32-characters";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  @Container
  static final KafkaContainer KAFKA =
      new KafkaContainer("apache/kafka:4.1.1")
          .withEnv("KAFKA_HEAP_OPTS", "-Xms256m -Xmx384m")
          .withCreateContainerCmdModifier(
              command ->
                  command.getHostConfig().withMemory(805306368L).withNanoCPUs(2_000_000_000L));

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("dlqdesk.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  @Value("${local.server.port}")
  int port;

  @Autowired KafkaClients clients;
  @Autowired InboxReader inbox;

  @BeforeAll
  static void topics() throws Exception {
    try (var admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
      admin
          .createTopics(
              List.of(
                  new NewTopic("contract.DLT", 2, (short) 1),
                  new NewTopic("contract-target", 2, (short) 1)))
          .all()
          .get(15, TimeUnit.SECONDS);
    }
  }

  @AfterAll
  static void close() {
    HTTP.close();
  }

  @Test
  void actualHttpReplayPreservesBytesAndLeavesProductionGroupOffsetsUntouched() throws Exception {
    var references = new ArrayList<InboxRecords.Reference>();
    var producer = new KafkaProducer<byte[], byte[]>(clients.producer());
    try {
      for (int i = 0; i < 3; i++) {
        var record =
            new ProducerRecord<byte[], byte[]>(
                "contract.DLT",
                i % 2,
                bytes("contract-key-" + i),
                bytes(i == 2 ? "{broken" : "{\"index\":" + i + "}"));
        record.headers().add("binary", new byte[] {0, -1, 17}).add("nullable", null);
        var metadata = producer.send(record).get(10, TimeUnit.SECONDS);
        references.add(
            new InboxRecords.Reference(metadata.partition(), Long.toString(metadata.offset())));
      }
    } finally {
      producer.close(Duration.ofSeconds(3));
    }
    var sourcePartition = new TopicPartition("contract.DLT", 0);
    try (var admin = Admin.create(clients.producer())) {
      admin
          .alterConsumerGroupOffsets(
              "production-orders", Map.of(sourcePartition, new OffsetAndMetadata(1)))
          .all()
          .get(10, TimeUnit.SECONDS);
      var before =
          admin
              .listConsumerGroupOffsets("production-orders")
              .partitionsToOffsetAndMetadata()
              .get(10, TimeUnit.SECONDS);
      assertFalse(clients.consumer().containsKey("group.id"));
      assertEquals(List.of(0, 1), inbox.partitions());
      assertFalse(inbox.browse(0, null, 10).messages().isEmpty());
      var originals = inbox.load(references);
      var selections =
          references.stream()
              .map(
                  reference ->
                      new ReplayRecords.Selection(
                          reference.partition(),
                          reference.offset(),
                          originals.get(reference).sourceHash(),
                          originals.get(reference).validJson()
                              ? null
                              : "{\"index\":2,\"fixed\":true}"))
              .toList();
      var preview = send("POST", "/previews", Map.of("rate", 20, "selections", selections), true);
      assertEquals(201, preview.statusCode(), preview.body());
      var frozen = JSON.readValue(preview.body(), ReplayRecords.RunView.class);
      String id = frozen.run().id();
      assertEquals(ReplayRecords.RunState.DRAFT, frozen.run().state());
      assertEquals(0, targetRecords(0).size(), "Preview must never publish");
      assertEquals(202, send("POST", "/replays/" + id + "/start", null, true).statusCode());
      var completed = await(id);
      assertEquals(3, completed.run().sent());
      assertEquals(0, completed.run().unknown());
      assertEquals(409, send("POST", "/replays/" + id + "/start", null, true).statusCode());
      var target = targetRecords(3);
      assertEquals(3, target.size());
      for (int i = 0; i < 3; i++) {
        var record = target.get("contract-key-" + i);
        assertNotNull(record);
        assertArrayEquals(new byte[] {0, -1, 17}, record.headers().lastHeader("binary").value());
        assertNull(record.headers().lastHeader("nullable").value());
        assertEquals(
            id,
            new String(
                record.headers().lastHeader("dlqdesk.run-id").value(), StandardCharsets.UTF_8));
        assertEquals(
            i == 2 ? "{\"index\":2,\"fixed\":true}" : "{\"index\":" + i + "}",
            new String(record.value(), StandardCharsets.UTF_8));
      }
      assertEquals(
          before,
          admin
              .listConsumerGroupOffsets("production-orders")
              .partitionsToOffsetAndMetadata()
              .get(10, TimeUnit.SECONDS));
      assertEquals(
          "{broken", inbox.load(List.of(references.get(2))).get(references.get(2)).payloadText());
      assertEquals(
          400,
          send("POST", "/previews", Map.of("rate", 101, "selections", selections), true)
              .statusCode());
      assertEquals(
          400,
          send(
                  "POST",
                  "/previews",
                  Map.of(
                      "rate",
                      10,
                      "selections",
                      List.of(selections.getFirst(), selections.getFirst())),
                  true)
              .statusCode());
      var stale =
          new ReplayRecords.Selection(
              references.getFirst().partition(),
              references.getFirst().offset(),
              "0".repeat(64),
              null);
      assertEquals(
          409,
          send("POST", "/previews", Map.of("rate", 10, "selections", List.of(stale)), true)
              .statusCode());
    }
  }

  @Test
  void authenticationGuardsResolvedEndpointsIncludingEncodedPaths() throws Exception {
    assertEquals(401, send("GET", "/info", null, false).statusCode());
    assertEquals(401, raw("/%61pi/info").statusCode());
    assertEquals(401, raw("/api/info;view=full").statusCode());
    var info = send("GET", "/info", null, true);
    assertEquals(200, info.statusCode());
    assertFalse(info.body().contains(TOKEN));
    assertEquals(404, send("GET", "/replays/missing", null, true).statusCode());
    assertEquals(400, send("GET", "/inbox?partition=-1", null, true).statusCode());
    assertEquals(
        404,
        send("POST", "/demo/seed", null, true).statusCode(),
        "Fixture endpoint must not exist without demo profile");
  }

  private ReplayRecords.RunView await(String id) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
    while (System.nanoTime() < deadline) {
      var view =
          JSON.readValue(
              send("GET", "/replays/" + id, null, true).body(), ReplayRecords.RunView.class);
      if (view.run().state() == ReplayRecords.RunState.COMPLETED) return view;
      if (view.run().state() == ReplayRecords.RunState.FAILED) fail(view.toString());
      Thread.sleep(50);
    }
    throw new AssertionError("Replay did not finish");
  }

  private Map<String, ConsumerRecord<byte[], byte[]>> targetRecords(int expected) {
    var consumer = new KafkaConsumer<byte[], byte[]>(clients.consumer());
    try {
      var partitions =
          List.of(
              new TopicPartition("contract-target", 0), new TopicPartition("contract-target", 1));
      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);
      var result = new HashMap<String, ConsumerRecord<byte[], byte[]>>();
      long deadline = System.nanoTime() + Duration.ofSeconds(expected == 0 ? 1 : 10).toNanos();
      do {
        for (var record : consumer.poll(Duration.ofMillis(100)))
          result.put(new String(record.key(), StandardCharsets.UTF_8), record);
      } while (System.nanoTime() < deadline && (expected == 0 || result.size() < expected));
      return result;
    } finally {
      consumer.close(CloseOptions.timeout(Duration.ofSeconds(1)));
    }
  }

  private HttpResponse<String> send(String method, String route, Object body, boolean authenticated)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + route))
            .timeout(Duration.ofSeconds(20));
    if (authenticated) builder.header("Authorization", "Bearer " + TOKEN);
    if (body != null) builder.header("Content-Type", "application/json");
    return HTTP.send(
        builder
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> raw(String path) throws Exception {
    return HTTP.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
