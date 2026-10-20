package io.github.lmysticl.dlqdesk.web;

import io.github.lmysticl.dlqdesk.config.DeskProperties;
import io.github.lmysticl.dlqdesk.inbox.InboxReader;
import io.github.lmysticl.dlqdesk.inbox.InboxRecords;
import io.github.lmysticl.dlqdesk.persistence.ReplayStore;
import io.github.lmysticl.dlqdesk.replay.PreviewService;
import io.github.lmysticl.dlqdesk.replay.ReplayCoordinator;
import io.github.lmysticl.dlqdesk.replay.ReplayRecords;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@DeskEndpoint
@RequestMapping("/api")
public final class DeskApi {
  private final InboxReader inbox;
  private final PreviewService previews;
  private final ReplayStore store;
  private final ReplayCoordinator coordinator;
  private final DeskProperties settings;

  public DeskApi(
      InboxReader inbox,
      PreviewService previews,
      ReplayStore store,
      ReplayCoordinator coordinator,
      DeskProperties settings) {
    this.inbox = inbox;
    this.previews = previews;
    this.store = store;
    this.coordinator = coordinator;
    this.settings = settings;
  }

  public record Info(
      String version,
      String sourceTopic,
      String targetTopic,
      boolean demo,
      int maxSelection,
      int maxRate) {}

  public record Preview(int rate, List<ReplayRecords.Selection> selections) {}

  public record Review(String reason) {}

  @GetMapping("/info")
  public Info info() {
    return new Info(
        "0.2.0", settings.dltTopic(), settings.targetTopic(), settings.demo(), 100, 100);
  }

  @GetMapping("/partitions")
  public List<Integer> partitions() {
    return inbox.partitions();
  }

  @GetMapping("/inbox")
  public InboxRecords.Page inbox(
      @RequestParam(defaultValue = "0") int partition,
      @RequestParam(required = false) String offset,
      @RequestParam(defaultValue = "25") int limit) {
    return inbox.browse(partition, offset, limit);
  }

  @GetMapping("/inbox/{partition}/{offset}")
  public InboxRecords.Source record(@PathVariable int partition, @PathVariable String offset) {
    var reference = new InboxRecords.Reference(partition, offset);
    return inbox.load(List.of(reference)).get(reference);
  }

  @PostMapping("/previews")
  @ResponseStatus(HttpStatus.CREATED)
  public ReplayRecords.RunView preview(@RequestBody Preview request) {
    return previews.create(request.rate(), request.selections());
  }

  @GetMapping("/replays")
  public List<ReplayRecords.RunSummary> replays() {
    return store.recent();
  }

  @GetMapping("/replays/{id}")
  public ReplayRecords.RunView replay(@PathVariable String id) {
    return store.view(id);
  }

  @GetMapping("/replays/{id}/entries/{index}")
  public ReplayRecords.FrozenView frozen(@PathVariable String id, @PathVariable int index) {
    return store.frozen(id, index);
  }

  @PostMapping("/replays/{id}/start")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ReplayRecords.RunView start(@PathVariable String id) {
    return coordinator.start(id, false);
  }

  @PostMapping("/replays/{id}/resume")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ReplayRecords.RunView resume(@PathVariable String id) {
    return coordinator.start(id, true);
  }

  @PostMapping("/replays/{id}/pause")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ReplayRecords.RunView pause(@PathVariable String id) {
    return coordinator.pause(id);
  }

  @PostMapping("/replays/{id}/entries/{index}/review")
  public ReplayRecords.RunView review(
      @PathVariable String id, @PathVariable int index, @RequestBody Review review) {
    store.reviewAndSkip(id, index, review.reason());
    return store.view(id);
  }
}
