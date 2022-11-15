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

import android.os.Process;
import android.os.StrictMode;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

class CustomThreadFactory implements ThreadFactory {
  private static final ThreadFactory DEFAULT = Executors.defaultThreadFactory();
  private final AtomicLong threadCount = new AtomicLong();
  private final String namePrefix;
  private final int priority;
  private final StrictMode.ThreadPolicy policy;

  CustomThreadFactory(String namePrefix, int priority, @Nullable StrictMode.ThreadPolicy policy) {
    this.namePrefix = namePrefix;
    this.priority = priority;
    this.policy = policy;
  }

  @Override
  public Thread newThread(Runnable r) {
    Thread thread =
        DEFAULT.newThread(
            () -> {
              Process.setThreadPriority(priority);
              if (policy != null) {
                StrictMode.setThreadPolicy(policy);
              }
              r.run();
            });
    thread.setName(
        String.format(Locale.ROOT, "%s Thread #%d", namePrefix, threadCount.getAndIncrement()));
    return thread;
  }
}
