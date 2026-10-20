package io.github.lmysticl.dlqdesk.replay;

import io.github.lmysticl.dlqdesk.DeskFailure;
import io.github.lmysticl.dlqdesk.DeskFailure.Kind;
import io.github.lmysticl.dlqdesk.config.DeskProperties;
import io.github.lmysticl.dlqdesk.inbox.InboxReader;
import io.github.lmysticl.dlqdesk.inbox.InboxRecords;
import io.github.lmysticl.dlqdesk.inbox.PayloadCodec;
import io.github.lmysticl.dlqdesk.persistence.ReplayStore;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class PreviewService {
  private static final Comparator<ReplayRecords.Selection> SOURCE_ORDER =
      Comparator.comparingInt(ReplayRecords.Selection::partition)
          .thenComparingLong(selection -> selection.reference().longOffset());

  private final InboxReader inbox;
  private final ReplayStore store;
  private final PayloadCodec codec;
  private final DeskProperties settings;

  public PreviewService(
      InboxReader inbox, ReplayStore store, PayloadCodec codec, DeskProperties settings) {
    this.inbox = inbox;
    this.store = store;
    this.codec = codec;
    this.settings = settings;
  }

  public ReplayRecords.RunView create(int rate, List<ReplayRecords.Selection> selections) {
    validate(rate, selections);
    var ordered = selections.stream().sorted(SOURCE_ORDER).toList();
    var references = ordered.stream().map(ReplayRecords.Selection::reference).toList();
    if (new HashSet<>(references).size() != references.size())
      throw new IllegalArgumentException("A record may only appear once in a preview");
    var sources = inbox.load(references);
    var frozen =
        ordered.stream()
            .map(selection -> freeze(selection, sources.get(selection.reference())))
            .toList();
    return store.create(settings.dltTopic(), settings.targetTopic(), rate, frozen);
  }

  private static void validate(int rate, List<ReplayRecords.Selection> selections) {
    if (rate < 1 || rate > 100)
      throw new IllegalArgumentException("Replay rate must be 1–100 records/s");
    if (selections == null || selections.isEmpty() || selections.size() > 100)
      throw new IllegalArgumentException("Choose 1–100 records");
    selections.forEach(PreviewService::requireFingerprintedReference);
  }

  private static void requireFingerprintedReference(ReplayRecords.Selection selection) {
    if (selection == null
        || selection.expectedHash() == null
        || !selection.expectedHash().matches("[a-f0-9]{64}"))
      throw new IllegalArgumentException("Each selection needs its original source fingerprint");
    selection.reference(); // rejects an invalid partition or offset before Kafka is read
  }

  /** Pins the payload to replay: the original bytes, or the operator's checked JSON edit. */
  private ReplayRecords.Frozen freeze(
      ReplayRecords.Selection selection, InboxRecords.Source source) {
    if (!source.sourceHash().equals(selection.expectedHash()))
      throw new DeskFailure(
          Kind.CONFLICT, "A selected source record changed. Refresh the inbox before previewing.");
    if (source.payloadBase64() == null)
      throw new IllegalArgumentException(
          "This record is a tombstone or exceeds the inspection limit");
    if (selection.editedJson() == null && !source.validJson())
      throw new IllegalArgumentException("Repair the invalid JSON payload before previewing");
    String value =
        selection.editedJson() == null
            ? source.payloadBase64()
            : codec.checkedJson(selection.editedJson());
    return new ReplayRecords.Frozen(
        source, value, PayloadCodec.hash(value), !Objects.equals(value, source.payloadBase64()));
  }
}
