package com.google.firebase.concurrent;

import androidx.annotation.VisibleForTesting;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

class TestExecutor implements Executor {
  @VisibleForTesting final Queue<Runnable> queue = new LinkedBlockingQueue<>();

  @Override
  public void execute(Runnable command) {
    queue.add(command);
  }

  void step() {
    Runnable next = queue.poll();
    if (next != null) {
      next.run();
    }
  }

  void stepAll() {
    Runnable next = queue.poll();
    while (next != null) {
      next.run();
      next = queue.poll();
    }
  }
}
