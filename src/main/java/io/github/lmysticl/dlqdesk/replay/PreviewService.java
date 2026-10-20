package io.github.lmysticl.dlqdesk.replay;

import io.github.lmysticl.dlqdesk.DeskFailure;
import io.github.lmysticl.dlqdesk.DeskFailure.Kind;
import io.github.lmysticl.dlqdesk.config.DeskProperties;
import io.github.lmysticl.dlqdesk.inbox.InboxReader;
import io.github.lmysticl.dlqdesk.inbox.PayloadCodec;
import io.github.lmysticl.dlqdesk.persistence.ReplayStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class PreviewService {
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
    if (rate < 1 || rate > 100)
      throw new IllegalArgumentException("Replay rate must be 1–100 records/s");
    if (selections == null || selections.isEmpty() || selections.size() > 100)
      throw new IllegalArgumentException("Choose 1–100 records");
    var ordered = new ArrayList<>(selections);
    for (var selection : ordered) {
      if (selection == null
          || selection.expectedHash() == null
          || !selection.expectedHash().matches("[a-f0-9]{64}"))
        throw new IllegalArgumentException("Each selection needs its original source fingerprint");
      selection.reference();
    }
    ordered.sort(
        Comparator.comparingInt(ReplayRecords.Selection::partition)
            .thenComparingLong(item -> item.reference().longOffset()));
    var references = ordered.stream().map(ReplayRecords.Selection::reference).toList();
    if (new HashSet<>(references).size() != references.size())
      throw new IllegalArgumentException("A record may only appear once in a preview");
    var sources = inbox.load(references);
    var frozen =
        ordered.stream()
            .map(
                selection -> {
                  var source = sources.get(selection.reference());
                  if (!source.sourceHash().equals(selection.expectedHash()))
                    throw new DeskFailure(
                        Kind.CONFLICT,
                        "A selected source record changed. Refresh the inbox before previewing.");
                  if (source.payloadBase64() == null)
                    throw new IllegalArgumentException(
                        "This record is a tombstone or exceeds the inspection limit");
                  String value =
                      selection.editedJson() == null
                          ? source.payloadBase64()
                          : codec.checkedJson(selection.editedJson());
                  if (selection.editedJson() == null && !source.validJson())
                    throw new IllegalArgumentException(
                        "Repair the invalid JSON payload before previewing");
                  return new ReplayRecords.Frozen(
                      source,
                      value,
                      PayloadCodec.hash(value),
                      !Objects.equals(value, source.payloadBase64()));
                })
            .toList();
    return store.create(settings.dltTopic(), settings.targetTopic(), rate, frozen);
  }
}
