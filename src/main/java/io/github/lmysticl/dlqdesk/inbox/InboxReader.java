package io.github.lmysticl.dlqdesk.inbox;

import java.util.List;
import java.util.Map;

public interface InboxReader {
  List<Integer> partitions();

  InboxRecords.Page browse(int partition, String offset, int limit);

  Map<InboxRecords.Reference, InboxRecords.Source> load(List<InboxRecords.Reference> references);
}
