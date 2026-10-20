package io.github.lmysticl.dlqdesk.replay;

public interface RecordPublisher {
  ReplayRecords.Delivery publish(String target, ReplayRecords.StoredEntry entry) throws Exception;
}
