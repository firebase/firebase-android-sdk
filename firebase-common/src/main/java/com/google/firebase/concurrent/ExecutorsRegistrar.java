package com.google.firebase.concurrent;

import android.annotation.SuppressLint;
import android.os.Process;
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

/** @hide */
@SuppressLint("ThreadPoolCreation")
public class ExecutorsRegistrar implements ComponentRegistrar {
  private static final Lazy<ScheduledExecutorService> BG_EXECUTOR =
      new Lazy<>(
          () ->
              scheduled(
                  Executors.newFixedThreadPool(
                      4, factory("Firebase Background", Process.THREAD_PRIORITY_BACKGROUND))));

  private static final Lazy<ScheduledExecutorService> LITE_EXECUTOR =
      new Lazy<>(
          () ->
              scheduled(
                  Executors.newFixedThreadPool(
                      Math.max(2, Runtime.getRuntime().availableProcessors()),
                      factory("Firebase Lite", Process.THREAD_PRIORITY_DEFAULT))));

  private static final Lazy<ScheduledExecutorService> BLOCKING_EXECUTOR =
      new Lazy<>(
          () ->
              scheduled(
                  Executors.newCachedThreadPool(
                      factory(
                          "Firebase Blocking",
                          Process.THREAD_PRIORITY_BACKGROUND
                              + Process.THREAD_PRIORITY_LESS_FAVORABLE))));

  private static final Lazy<ScheduledExecutorService> SCHEDULER =
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
    return new CustomThreadFactory(threadPrefix, priority);
  }
}
