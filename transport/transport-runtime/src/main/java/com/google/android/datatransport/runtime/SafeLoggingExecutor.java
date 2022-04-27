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

package com.google.android.datatransport.runtime;

import com.google.android.datatransport.runtime.logging.Logging;
import java.util.concurrent.Executor;

/**
 * Protects underlying threads from crashing.
 *
 * <p>Exceptions thrown by executed {@link Runnable}s are logged and swallowed.
 */
class SafeLoggingExecutor implements Executor {
  private final Executor delegate;

  SafeLoggingExecutor(Executor delegate) {
    this.delegate = delegate;
  }

  @Override
  public void execute(Runnable command) {
    delegate.execute(new SafeLoggingRunnable(command));
  }

  static class SafeLoggingRunnable implements Runnable {
    private final Runnable delegate;

    SafeLoggingRunnable(Runnable delegate) {
      this.delegate = delegate;
    }

    @Override
    public void run() {
      try {
        delegate.run();
      } catch (Exception e) {
        Logging.e("Executor", "Background execution failure.", e);
      }
    }
  }
}
