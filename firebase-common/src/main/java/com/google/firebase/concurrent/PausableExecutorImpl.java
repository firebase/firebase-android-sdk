package com.google.firebase.concurrent;

import androidx.annotation.VisibleForTesting;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

final class PausableExecutorImpl implements PausableExecutor {
  private volatile boolean paused;
  private final Executor delegate;

  @VisibleForTesting final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

  PausableExecutorImpl(boolean paused, Executor delegate) {
    this.paused = paused;
    this.delegate = delegate;
  }

  @Override
  public void execute(Runnable command) {
    if (paused) {
      queue.offer(command);
    } else {
      delegate.execute(
          () -> {
            try {
              command.run();
            } finally {
              maybeEnqueueNext();
            }
          });
    }
  }

  private void maybeEnqueueNext() {
    if (paused) {
      return;
    }
    Runnable next = queue.poll();
    while (next != null) {
      execute(next);
      if (!paused) {
        next = queue.poll();
      } else {
        next = null;
      }
    }
  }

  @Override
  public void pause() {
    paused = true;
  }

  @Override
  public void resume() {
    paused = false;
    maybeEnqueueNext();
  }

  @Override
  public boolean isPaused() {
    return paused;
  }
}
