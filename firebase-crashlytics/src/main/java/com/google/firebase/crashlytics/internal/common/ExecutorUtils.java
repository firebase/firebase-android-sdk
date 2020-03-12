// Copyright 2019 Google LLC
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

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.firebase.crashlytics.internal.Logger;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class ExecutorUtils {
  private static final long DEFAULT_TERMINATION_TIMEOUT = 2L;

  private ExecutorUtils() {}

  public static ExecutorService buildSingleThreadExecutorService(String name) {
    final ThreadFactory threadFactory = ExecutorUtils.getNamedThreadFactory(name);
    final ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);
    ExecutorUtils.addDelayedShutdownHook(name, executor);
    return executor;
  }

  public static ScheduledExecutorService buildSingleThreadScheduledExecutorService(String name) {
    final ThreadFactory threadFactory = ExecutorUtils.getNamedThreadFactory(name);
    final ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(threadFactory);
    ExecutorUtils.addDelayedShutdownHook(name, executor);
    return executor;
  }

  public static final ThreadFactory getNamedThreadFactory(final String threadNameTemplate) {
    final AtomicLong count = new AtomicLong(1);

    return new ThreadFactory() {
      @Override
      public Thread newThread(final Runnable runnable) {
        final Thread thread =
            Executors.defaultThreadFactory()
                .newThread(
                    new BackgroundPriorityRunnable() {
                      @Override
                      public void onRun() {
                        runnable.run();
                      }
                    });
        thread.setName(threadNameTemplate + count.getAndIncrement());
        return thread;
      }
    };
  }

  private static final void addDelayedShutdownHook(String serviceName, ExecutorService service) {
    ExecutorUtils.addDelayedShutdownHook(
        serviceName, service, DEFAULT_TERMINATION_TIMEOUT, SECONDS);
  }

  public static final void addDelayedShutdownHook(
      final String serviceName,
      final ExecutorService service,
      final long terminationTimeout,
      final TimeUnit timeUnit) {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                new BackgroundPriorityRunnable() {
                  @Override
                  public void onRun() {
                    try {
                      Logger.getLogger().d("Executing shutdown hook for " + serviceName);
                      service.shutdown();
                      if (!service.awaitTermination(terminationTimeout, timeUnit)) {
                        Logger.getLogger()
                            .d(
                                serviceName
                                    + " did not shut down in the"
                                    + " allocated time. Requesting immediate shutdown.");
                        service.shutdownNow();
                      }
                    } catch (InterruptedException e) {
                      Logger.getLogger()
                          .d(
                              String.format(
                                  Locale.US,
                                  "Interrupted while waiting for %s to shut down."
                                      + " Requesting immediate shutdown.",
                                  serviceName));
                      service.shutdownNow();
                    }
                  }
                },
                "Crashlytics Shutdown Hook for " + serviceName));
  }
}
