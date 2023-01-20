// Copyright 2023 Google LLC
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

import com.google.firebase.components.Preconditions;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * An {@link Executor}that limits the number of concurrent tasks executing at a given time.
 *
 * <p>Delegates actual execution to the {@code delegate} executor and does not create threads of it
 * own.
 *
 * <p>The executor is fair: has FIFO semantics for submitted tasks.
 */
class LimitedConcurrencyExecutor implements Executor {

  private final Executor delegate;

  private final Semaphore semaphore;

  private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

  LimitedConcurrencyExecutor(Executor delegate, int concurrency) {
    Preconditions.checkArgument(concurrency > 0, "concurrency must be positive.");
    this.delegate = delegate;
    semaphore = new Semaphore(concurrency, true);
  }

  @Override
  public void execute(Runnable command) {
    queue.offer(command);
    maybeEnqueueNext();
  }

  private void maybeEnqueueNext() {
    while (semaphore.tryAcquire()) {
      Runnable next = queue.poll();
      if (next != null) {
        delegate.execute(decorate(next));
      } else {
        semaphore.release();
        break;
      }
    }
  }

  private Runnable decorate(Runnable command) {
    return () -> {
      try {
        command.run();
      } finally {
        semaphore.release();
        maybeEnqueueNext();
      }
    };
  }
}
