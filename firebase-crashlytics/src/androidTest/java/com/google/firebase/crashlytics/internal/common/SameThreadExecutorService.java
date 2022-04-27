// Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class SameThreadExecutorService extends AbstractExecutorService {

  private final AtomicBoolean shutDown = new AtomicBoolean(false);

  @Override
  public boolean awaitTermination(long duration, TimeUnit unit) throws InterruptedException {
    final boolean terminated = shutDown.get();

    if (!terminated) {
      Thread.sleep(unit.toMillis(duration));
    }

    return terminated;
  }

  @Override
  public boolean isShutdown() {
    return shutDown.get();
  }

  @Override
  public boolean isTerminated() {
    return isShutdown();
  }

  @Override
  public void shutdown() {
    shutDown.set(true);
  }

  @Override
  public List<Runnable> shutdownNow() {
    shutdown();
    return Collections.emptyList();
  }

  @Override
  public void execute(Runnable r) {
    r.run();
  }
}
