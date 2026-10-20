package io.github.lmysticl.dlqdesk.replay;

import io.github.lmysticl.dlqdesk.DeskFailure;
import io.github.lmysticl.dlqdesk.DeskFailure.Kind;
import io.github.lmysticl.dlqdesk.config.DeskProperties;
import io.github.lmysticl.dlqdesk.persistence.ReplayStore;
import io.github.lmysticl.dlqdesk.replay.ReplayRecords.RunState;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public final class ReplayCoordinator implements ApplicationRunner, AutoCloseable {
  private final ReplayStore store;
  private final RecordPublisher publisher;
  private final DeskProperties settings;
  private final Semaphore active = new Semaphore(1);
  private final ReentrantLock waitLock = new ReentrantLock();
  private final Condition changed = waitLock.newCondition();
  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(
          1,
          1,
          0,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(1),
          Thread.ofPlatform().name("dlqdesk-replay-", 0).factory(),
          new ThreadPoolExecutor.AbortPolicy());

  public ReplayCoordinator(ReplayStore store, RecordPublisher publisher, DeskProperties settings) {
    this.store = store;
    this.publisher = publisher;
    this.settings = settings;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    store.recoverInterrupted();
  }

  public ReplayRecords.RunView start(String id, boolean resume) {
    if (!active.tryAcquire())
      throw new DeskFailure(
          Kind.CONFLICT, "Another replay is active. Pause it before starting another plan.");
    try {
      store.begin(
          id,
          resume
              ? Set.of(RunState.PAUSED, RunState.INTERRUPTED, RunState.FAILED)
              : Set.of(RunState.DRAFT),
          settings.targetTopic());
      try {
        executor.execute(() -> execute(id));
      } catch (RejectedExecutionException failure) {
        store.interrupt(id);
        throw failure;
      }
    } catch (RuntimeException failure) {
      active.release();
      throw failure;
    }
    return store.view(id);
  }

  public ReplayRecords.RunView pause(String id) {
    store.requestPause(id);
    wakeUp();
    return store.view(id);
  }

  private void execute(String id) {
    try {
      var run = store.summary(id);
      long period = TimeUnit.SECONDS.toNanos(1) / run.rate();
      long nextSend = System.nanoTime();
      while (!Thread.currentThread().isInterrupted()) {
        awaitTurn(id, nextSend);
        var next = store.claimNext(id);
        if (next.isEmpty()) {
          store.settle(id);
          return;
        }
        var entry = next.get();
        try {
          var delivery = publisher.publish(run.targetTopic(), entry);
          store.sent(id, entry.index(), delivery);
        } catch (Exception failure) {
          boolean interrupted = Thread.interrupted();
          try {
            store.uncertain(id, entry.index());
          } finally {
            if (interrupted || failure instanceof InterruptedException)
              Thread.currentThread().interrupt();
          }
          return;
        }
        nextSend = Math.max(nextSend + period, System.nanoTime());
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } catch (RuntimeException failure) {
      store.interrupt(id);
      throw failure;
    } finally {
      active.release();
    }
  }

  private void awaitTurn(String id, long deadline) throws InterruptedException {
    while (true) {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) return;
      waitLock.lockInterruptibly();
      try {
        changed.awaitNanos(remaining);
      } finally {
        waitLock.unlock();
      }
      if (store.summary(id).state() != RunState.RUNNING) return;
    }
  }

  private void wakeUp() {
    waitLock.lock();
    try {
      changed.signalAll();
    } finally {
      waitLock.unlock();
    }
  }

  @Override
  public void close() {
    executor.shutdownNow();
    wakeUp();
    try {
      executor.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
