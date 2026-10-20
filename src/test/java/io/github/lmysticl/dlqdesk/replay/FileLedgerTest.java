package io.github.lmysticl.dlqdesk.replay;

import static org.junit.jupiter.api.Assertions.*;

import io.github.lmysticl.dlqdesk.inbox.InboxRecords;
import io.github.lmysticl.dlqdesk.inbox.PayloadCodec;
import io.github.lmysticl.dlqdesk.persistence.ReplayStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import tools.jackson.databind.json.JsonMapper;

class FileLedgerTest {
  @TempDir Path directory;

  @Test
  void reopeningTheDatabaseRetainsConfirmedDeliveryAndRecoversAnInflightRecord() {
    String url = "jdbc:h2:file:" + directory.resolve("ledger").toString().replace('\\', '/');
    var source = new DriverManagerDataSource(url, "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__replay_ledger.sql"))
        .execute(source);
    var first = store(source);
    String payload = PayloadCodec.encode(new byte[] {123, 125});
    var records =
        List.of(0, 1).stream()
            .map(
                index ->
                    new ReplayRecords.Frozen(
                        new InboxRecords.Source(
                            new InboxRecords.Reference(0, index.toString()),
                            null,
                            null,
                            payload,
                            "{}",
                            1,
                            List.of(),
                            "a".repeat(64),
                            true,
                            null),
                        payload,
                        PayloadCodec.hash(payload),
                        false))
            .toList();
    String id = first.create("source.DLT", "target", 10, records).run().id();
    first.begin(id, Set.of(ReplayRecords.RunState.DRAFT), "target");
    first.claimNext(id);
    first.sent(id, 0, new ReplayRecords.Delivery(0, "8"));
    first.claimNext(id);
    // DriverManagerDataSource closes every connection; the next instance reopens the file.
    var reopened = store(new DriverManagerDataSource(url, "sa", ""));
    reopened.recoverInterrupted();
    var result = reopened.view(id);
    assertEquals(ReplayRecords.RunState.INTERRUPTED, result.run().state());
    assertEquals(1, result.run().sent());
    assertEquals(1, result.run().unknown());
    assertEquals("8", result.entries().getFirst().targetOffset());
    assertEquals("{}", reopened.frozen(id, 0).outputPayload());
    assertTrue(
        result.audit().stream().anyMatch(event -> event.action().equals("PROCESS_RECOVERED")));
  }

  private ReplayStore store(DriverManagerDataSource source) {
    return new ReplayStore(
        new JdbcTemplate(source),
        new DataSourceTransactionManager(source),
        JsonMapper.builder().build());
  }
}
