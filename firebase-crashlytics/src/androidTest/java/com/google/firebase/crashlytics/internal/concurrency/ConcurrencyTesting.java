/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.internal.concurrency;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Convenience methods for use in Crashlytics concurrency tests. */
class ConcurrencyTesting {

  /** Returns the current thread's name. */
  static String getThreadName() {
    return Thread.currentThread().getName();
  }

  /** Creates a simple executor that runs on a single named thread. */
  static ExecutorService newNamedSingleThreadExecutor(String name) {
    return Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, name));
  }

  /** Convenient sleep method that propagates the interruption, but does not throw. */
  static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private ConcurrencyTesting() {}
}
