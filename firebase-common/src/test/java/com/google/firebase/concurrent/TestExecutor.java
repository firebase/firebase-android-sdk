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
