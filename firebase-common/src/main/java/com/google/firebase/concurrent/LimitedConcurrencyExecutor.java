// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.concurrent;

import androidx.annotation.GuardedBy;
import com.google.firebase.components.Preconditions;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

class LimitedConcurrencyExecutor implements PausableExecutor {

  private final Executor delegate;

  @GuardedBy("queue")
  private boolean paused = false;

  @GuardedBy("queue")
  private int activeCount;

  private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

  LimitedConcurrencyExecutor(Executor delegate, int concurrency) {
    Preconditions.checkArgument(concurrency > 0, "concurrency must be positive.");
    this.delegate = delegate;
    activeCount = concurrency;
  }

  @Override
  public void pause() {
    synchronized (queue) {
      paused = true;
    }
  }

  @Override
  public void resume() {
    synchronized (queue) {
      paused = false;
      maybeEnqueueNext();
    }
  }

  @Override
  public void execute(Runnable command) {
    synchronized (queue) {
      if (!paused && activeCount > 0) {
        activeCount--;
        executeOnDelegate(command);
        return;
      }
      queue.offer(command);
    }
  }

  private void executeOnDelegate(Runnable runnable) {
    delegate.execute(
        () -> {
          try {
            runnable.run();
          } finally {
            synchronized (queue) {
              activeCount++;
              if (!paused) {
                maybeEnqueueNext();
              }
            }
          }
        });
  }

  private void maybeEnqueueNext() {
    synchronized (queue) {
      Runnable next = queue.poll();
      if (next != null) {
        execute(next);
      }
    }
  }
}
