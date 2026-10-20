package io.github.lmysticl.dlqdesk.replay;

import static org.junit.jupiter.api.Assertions.*;

import io.github.lmysticl.dlqdesk.DeskFailure;
import io.github.lmysticl.dlqdesk.config.DeskProperties;
import io.github.lmysticl.dlqdesk.inbox.InboxRecords;
import io.github.lmysticl.dlqdesk.inbox.PayloadCodec;
import io.github.lmysticl.dlqdesk.persistence.ReplayStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import tools.jackson.databind.json.JsonMapper;

class ReplayRecoveryTest {
  private final EmbeddedDatabase database =
      new EmbeddedDatabaseBuilder()
          .generateUniqueName(true)
          .setType(EmbeddedDatabaseType.H2)
          .addScript("db/migration/V1__replay_ledger.sql")
          .build();
  private final JdbcTemplate jdbc = new JdbcTemplate(database);
  private final ReplayStore store =
      new ReplayStore(
          jdbc, new DataSourceTransactionManager(database), JsonMapper.builder().build());
  private final DeskProperties settings =
      new DeskProperties("a".repeat(32), "localhost:9092", "orders.DLT", "orders", false, null);
  private final List<ReplayCoordinator> workers = new ArrayList<>();

  @AfterEach
  void close() {
    workers.forEach(ReplayCoordinator::close);
    database.shutdown();
  }

  @Test
  void recoveryPreservesSentAndBlocksUncertainDeliveryUntilReviewed() {
    String id = plan(3, 10);
    store.begin(id, Set.of(ReplayRecords.RunState.DRAFT), "orders");
    var first = store.claimNext(id).orElseThrow();
    store.sent(id, first.index(), new ReplayRecords.Delivery(1, "9007199254740993"));
    store.claimNext(id).orElseThrow();
    store.recoverInterrupted();
    var recovered = store.view(id);
    assertEquals(ReplayRecords.RunState.INTERRUPTED, recovered.run().state());
    assertEquals(1, recovered.run().sent());
    assertEquals(1, recovered.run().unknown());
    assertEquals(1, recovered.run().pending());
    assertEquals("9007199254740993", recovered.entries().getFirst().targetOffset());
    assertThrows(
        DeskFailure.class,
        () -> store.begin(id, Set.of(ReplayRecords.RunState.INTERRUPTED), "orders"));
    assertThrows(IllegalArgumentException.class, () -> store.reviewAndSkip(id, 1, " "));
    store.reviewAndSkip(id, 1, "Confirmed key in destination; retain original delivery");
    store.begin(id, Set.of(ReplayRecords.RunState.INTERRUPTED), "orders");
    assertEquals(2, store.claimNext(id).orElseThrow().index());
    assertEquals(1, store.summary(id).sent());
  }

  @Test
  void pauseWaitsForAcknowledgementAndResumeDoesNotResendSentEntries() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var indexes = new CopyOnWriteArrayList<Integer>();
    var worker =
        worker(
            (target, entry) -> {
              indexes.add(entry.index());
              entered.countDown();
              assertTrue(release.await(5, TimeUnit.SECONDS));
              return new ReplayRecords.Delivery(0, Integer.toString(entry.index()));
            });
    String id = plan(3, 100);
    worker.start(id, false);
    assertTrue(entered.await(5, TimeUnit.SECONDS));
    assertEquals(ReplayRecords.RunState.PAUSE_REQUESTED, worker.pause(id).run().state());
    assertThrows(DeskFailure.class, () -> worker.start(plan(1, 10), false));
    release.countDown();
    await(id, ReplayRecords.RunState.PAUSED);
    assertEquals(List.of(0), indexes);
    assertEquals(1, store.summary(id).sent());
    worker.start(id, true);
    await(id, ReplayRecords.RunState.COMPLETED);
    assertEquals(List.of(0, 1, 2), indexes);
    assertEquals(3, store.summary(id).sent());
  }

  @Test
  void brokerFailureCreatesUnknownWithoutAutomaticRetry() throws Exception {
    var attempts = new AtomicInteger();
    var worker =
        worker(
            (target, entry) -> {
              attempts.incrementAndGet();
              throw new java.util.concurrent.TimeoutException("acknowledgement lost");
            });
    String id = plan(2, 10);
    worker.start(id, false);
    await(id, ReplayRecords.RunState.FAILED);
    assertEquals(1, attempts.get());
    assertEquals(1, store.summary(id).unknown());
    assertEquals(1, store.summary(id).pending());
    assertThrows(DeskFailure.class, () -> worker.start(id, true));
    assertEquals(1, attempts.get());
  }

  @Test
  void rateLimitSpacesStartsInsteadOfSleepingInsideTransactions() throws Exception {
    var times = new CopyOnWriteArrayList<Long>();
    var worker =
        worker(
            (target, entry) -> {
              times.add(System.nanoTime());
              return new ReplayRecords.Delivery(0, "1");
            });
    String id = plan(3, 5);
    worker.start(id, false);
    await(id, ReplayRecords.RunState.COMPLETED);
    assertEquals(3, times.size());
    assertTrue(times.get(2) - times.get(0) >= Duration.ofMillis(360).toNanos(), times.toString());
  }

  @Test
  void targetChangesAndRepeatedCommandsAreRejected() {
    String id = plan(1, 10);
    assertThrows(
        DeskFailure.class, () -> store.begin(id, Set.of(ReplayRecords.RunState.DRAFT), "other"));
    assertEquals(ReplayRecords.RunState.DRAFT, store.summary(id).state());
    store.begin(id, Set.of(ReplayRecords.RunState.DRAFT), "orders");
    assertThrows(
        DeskFailure.class, () -> store.begin(id, Set.of(ReplayRecords.RunState.DRAFT), "orders"));
    store.requestPause(id);
    store.settle(id);
    assertThrows(DeskFailure.class, () -> store.requestPause(id));
  }

  private ReplayCoordinator worker(RecordPublisher publisher) {
    var worker = new ReplayCoordinator(store, publisher, settings);
    workers.add(worker);
    return worker;
  }

  private String plan(int count, int rate) {
    var records = new ArrayList<ReplayRecords.Frozen>();
    for (int i = 0; i < count; i++) {
      String body = PayloadCodec.encode(("{\"order\":" + i + "}").getBytes(StandardCharsets.UTF_8));
      var source =
          new InboxRecords.Source(
              new InboxRecords.Reference(0, Integer.toString(i)),
              null,
              null,
              body,
              null,
              0,
              List.of(),
              "a".repeat(64),
              true,
              null);
      records.add(new ReplayRecords.Frozen(source, body, PayloadCodec.hash(body), false));
    }
    return store.create("orders.DLT", "orders", rate, records).run().id();
  }

  private void await(String id, ReplayRecords.RunState state) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(6).toNanos();
    while (System.nanoTime() < deadline) {
      if (store.summary(id).state() == state) return;
      Thread.sleep(20);
    }
    fail("Expected " + state + ", got " + store.view(id));
  }
}
