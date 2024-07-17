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

import android.annotation.SuppressLint;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class ExecutorUtils {
  private ExecutorUtils() {}

  public static ExecutorService buildSingleThreadExecutorService(String name) {
    final ThreadFactory threadFactory = ExecutorUtils.getNamedThreadFactory(name);
    return newSingleThreadExecutor(threadFactory, new ThreadPoolExecutor.DiscardPolicy());
  }

  public static ScheduledExecutorService buildSingleThreadScheduledExecutorService(String name) {
    final ThreadFactory threadFactory = ExecutorUtils.getNamedThreadFactory(name);
    // TODO(b/258263226): Migrate to go/firebase-android-executors
    @SuppressLint("ThreadPoolCreation")
    final ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(threadFactory);
    return executor;
  }

  public static ThreadFactory getNamedThreadFactory(final String threadNameTemplate) {
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

  // TODO(b/258263226): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  private static ExecutorService newSingleThreadExecutor(
      ThreadFactory threadFactory, RejectedExecutionHandler rejectedExecutionHandler) {
    return Executors.unconfigurableExecutorService(
        new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(),
            threadFactory,
            rejectedExecutionHandler));
  }
}
