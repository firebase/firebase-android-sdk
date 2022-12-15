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

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Process;
import android.os.StrictMode;
import com.google.firebase.BuildConfig;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.annotations.concurrent.UiThread;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Lazy;
import com.google.firebase.components.Qualified;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

@SuppressLint("ThreadPoolCreation")
public class ExecutorsRegistrar implements ComponentRegistrar {
  static final Lazy<ScheduledExecutorService> BG_EXECUTOR =
      new Lazy<>(
          () ->
              scheduled(
                  Executors.newFixedThreadPool(
                      4,
                      factory(
                          "Firebase Background", Process.THREAD_PRIORITY_BACKGROUND, bgPolicy()))));

  static final Lazy<ScheduledExecutorService> LITE_EXECUTOR =
      new Lazy<>(
          () ->
              scheduled(
                  Executors.newFixedThreadPool(
                      Math.max(2, Runtime.getRuntime().availableProcessors()),
                      factory("Firebase Lite", Process.THREAD_PRIORITY_DEFAULT, litePolicy()))));

  static final Lazy<ScheduledExecutorService> BLOCKING_EXECUTOR =
      new Lazy<>(
          () ->
              scheduled(
                  Executors.newCachedThreadPool(
                      factory(
                          "Firebase Blocking",
                          Process.THREAD_PRIORITY_BACKGROUND
                              + Process.THREAD_PRIORITY_LESS_FAVORABLE))));

  static final Lazy<ScheduledExecutorService> SCHEDULER =
      new Lazy<>(
          () ->
              Executors.newSingleThreadScheduledExecutor(
                  factory("Firebase Scheduler", Process.THREAD_PRIORITY_DEFAULT)));

  @Override
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(
                Qualified.qualified(Background.class, ScheduledExecutorService.class),
                Qualified.qualified(Background.class, ExecutorService.class),
                Qualified.qualified(Background.class, Executor.class))
            .factory(c -> BG_EXECUTOR.get())
            .build(),
        Component.builder(
                Qualified.qualified(Blocking.class, ScheduledExecutorService.class),
                Qualified.qualified(Blocking.class, ExecutorService.class),
                Qualified.qualified(Blocking.class, Executor.class))
            .factory(c -> BLOCKING_EXECUTOR.get())
            .build(),
        Component.builder(
                Qualified.qualified(Lightweight.class, ScheduledExecutorService.class),
                Qualified.qualified(Lightweight.class, ExecutorService.class),
                Qualified.qualified(Lightweight.class, Executor.class))
            .factory(c -> LITE_EXECUTOR.get())
            .build(),
        Component.builder(Qualified.qualified(UiThread.class, Executor.class))
            .factory(c -> UiExecutor.INSTANCE)
            .build());
  }

  private static ScheduledExecutorService scheduled(ExecutorService delegate) {
    return new DelegatingScheduledExecutorService(delegate, SCHEDULER.get());
  }

  private static ThreadFactory factory(String threadPrefix, int priority) {
    return new CustomThreadFactory(threadPrefix, priority, null);
  }

  private static ThreadFactory factory(
      String threadPrefix, int priority, StrictMode.ThreadPolicy policy) {
    return new CustomThreadFactory(threadPrefix, priority, policy);
  }

  private static StrictMode.ThreadPolicy bgPolicy() {
    StrictMode.ThreadPolicy.Builder builder = new StrictMode.ThreadPolicy.Builder().detectNetwork();
    if (Build.VERSION.SDK_INT >= 23) {
      builder.detectResourceMismatches();
      if (Build.VERSION.SDK_INT >= 26) {
        builder.detectUnbufferedIo();
      }
    }
    if (BuildConfig.DEBUG) {
      builder.penaltyDeath();
    }
    return builder.penaltyLog().build();
  }

  private static StrictMode.ThreadPolicy litePolicy() {
    StrictMode.ThreadPolicy.Builder builder = new StrictMode.ThreadPolicy.Builder().detectAll();
    if (BuildConfig.DEBUG) {
      builder.penaltyDeath();
    }
    return builder.penaltyLog().build();
  }
}
