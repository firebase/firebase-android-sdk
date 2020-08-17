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

package com.google.firebase.messaging.threads;

import androidx.annotation.NonNull;
import com.google.errorprone.annotations.CompileTimeConstant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Provider of the {@link ExecutorFactory} that should be used to create thread executors.
 *
 * <p>If a factory is not provided during initialization, then it provides a default implementation.
 */
// TODO(b/144941134): Remove nullness suppression.
@SuppressWarnings("nullness")
public class PoolableExecutors {

  private static final ExecutorFactory DEFAULT_INSTANCE = new DefaultExecutorFactory();
  private static volatile ExecutorFactory instance = DEFAULT_INSTANCE;

  private PoolableExecutors() {}

  public static ExecutorFactory factory() {
    return instance;
  }

  /** A {@link ExecutorFactory} that creates the default thread executors. */
  private static class DefaultExecutorFactory implements ExecutorFactory {

    private static final long CORE_THREAD_TIMEOUT_SECS = 60L;

    /** {@inheritDoc} */
    @NonNull
    @Override
    public ExecutorService newThreadPool(ThreadPriority priority) {
      // NOTE: Cached threadpools automatically time out all threads.  They have no concept of core
      // threads; the queue blocks until a thread is started.
      return Executors.unconfigurableExecutorService(Executors.newCachedThreadPool());
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public ExecutorService newThreadPool(ThreadFactory threadFactory, ThreadPriority priority) {
      return Executors.unconfigurableExecutorService(Executors.newCachedThreadPool(threadFactory));
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public ExecutorService newThreadPool(int maxConcurrency, ThreadPriority priority) {
      // TODO(gboyer): Honor the thread priority even when no factory is provided, by creating
      // a factory that actually sets priority.
      // TODO(gboyer): Add a @CompileTimeConstant String name argument; this will replace almost
      // all uses of the ThreadFactory version, and could later help with tracing.
      return newThreadPool(maxConcurrency, Executors.defaultThreadFactory(), priority);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public ExecutorService newThreadPool(
        int maxConcurrency, ThreadFactory threadFactory, ThreadPriority priority) {
      ThreadPoolExecutor executor =
          new ThreadPoolExecutor(
              maxConcurrency,
              maxConcurrency,
              CORE_THREAD_TIMEOUT_SECS,
              TimeUnit.SECONDS,
              new LinkedBlockingQueue<Runnable>(),
              threadFactory);
      // This allows core threads to be terminated if they are not used; otherwise, these threads
      // will forever suck up memory until the executor is shutdown or finalized. Generally, it is
      // fairly fast in Android to start and stop threads, especially if this is limited to only
      // happening once per minute.
      executor.allowCoreThreadTimeOut(true);
      return Executors.unconfigurableExecutorService(executor);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public ExecutorService newSingleThreadExecutor(ThreadPriority priority) {
      return newThreadPool(1, priority);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public ExecutorService newSingleThreadExecutor(
        ThreadFactory threadFactory, ThreadPriority priority) {
      return newThreadPool(1, threadFactory, priority);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public ScheduledExecutorService newScheduledThreadPool(
        int maxConcurrency, ThreadPriority priority) {
      // NOTE: There's no way to make a scheduled executor stop threads automatically, because
      // at least one thread is needed to waiting for future tasks.
      // TODO(b/63802200): Consider wrapping this in a finalizable decorator that prevents runaway
      // memory leaks from non-shutdown pools.
      return Executors.unconfigurableScheduledExecutorService(
          Executors.newScheduledThreadPool(maxConcurrency));
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public ScheduledExecutorService newScheduledThreadPool(
        int maxConcurrency, ThreadFactory threadFactory, ThreadPriority priority) {
      return Executors.unconfigurableScheduledExecutorService(
          Executors.newScheduledThreadPool(maxConcurrency, threadFactory));
    }

    @Override
    @NonNull
    public void executeOneOff(
        @CompileTimeConstant final String moduleName,
        @CompileTimeConstant final String name,
        ThreadPriority priority,
        Runnable runnable) {
      new Thread(runnable, name).start();
    }

    @Override
    @NonNull
    public Future<?> submitOneOff(
        @CompileTimeConstant final String moduleName,
        @CompileTimeConstant final String name,
        ThreadPriority priority,
        Runnable runnable) {
      FutureTask<?> task = new FutureTask<>(runnable, null);
      new Thread(task, name).start();
      return task;
    }
  }

  /**
   * Installs the {@link ExecutorFactory} implementation; INTERNAL USE ONLY.
   *
   * <p>May only be called once.
   *
   * <p>Call this only via build-visibility-restricted PunchClockThreadsImplementationApi.
   */
  static void installExecutorFactory(ExecutorFactory instance) {
    if (PoolableExecutors.instance != DEFAULT_INSTANCE) {
      throw new IllegalStateException("Trying to install an ExecutorFactory twice!");
    }
    PoolableExecutors.instance = instance;
  }
}
