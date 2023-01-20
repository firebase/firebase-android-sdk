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
    queue.offer(command);
    maybeEnqueueNext();
  }

  private void maybeEnqueueNext() {
    if (paused) {
      return;
    }
    Runnable next = queue.poll();
    while (next != null) {
      delegate.execute(next);
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
