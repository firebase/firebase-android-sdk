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

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/** Provides commonly useful executors. */
public class FirebaseExecutors {
  private FirebaseExecutors() {}

  /**
   * Creates a sequential executor.
   *
   * <p>Executes tasks sequentially and provides memory synchronization guarantees for any mutations
   * of shared state.
   *
   * <p>For details see:
   * https://guava.dev/releases/31.1-jre/api/docs/com/google/common/util/concurrent/MoreExecutors.html#newSequentialExecutor(java.util.concurrent.Executor)
   */
  public static Executor newSequentialExecutor(Executor delegate) {
    return new SequentialExecutor(delegate);
  }

  /**
   * Returns a {@link PausableExecutor} that limits the number of running tasks at a given time.
   *
   * <p>The executor uses delegate in order to {@link Executor#execute(Runnable) execute} each task,
   * and does not create any threads of its own.
   *
   * @param delegate {@link Executor} used to execute tasks
   * @param concurrency max number of tasks executing concurrently
   */
  public static PausableExecutor newLimitedExecutor(Executor delegate, int concurrency) {
    return new LimitedConcurrencyExecutor(delegate, concurrency);
  }

  /**
   * Returns a {@link PausableExecutorService} that limits the number of running tasks at a given
   * time.
   *
   * <p>The executor uses delegate in order to {@link Executor#execute(Runnable) execute} each task,
   * and does not create any threads of its own.
   *
   * @param delegate {@link ExecutorService} used to execute tasks
   * @param concurrency max number of tasks executing concurrently
   */
  public static PausableExecutorService newLimitedExecutorService(
      ExecutorService delegate, int concurrency) {
    return new LimitedConcurrencyExecutorService(delegate, concurrency);
  }

  /**
   * Returns a {@link PausableScheduledExecutorService} that limits the number of running tasks at a
   * given time.
   *
   * <p>The executor uses delegate in order to {@link Executor#execute(Runnable) execute} each task,
   * and does not create any threads of its own.
   *
   * @param delegate {@link ExecutorService} used to execute tasks
   * @param concurrency max number of tasks executing concurrently
   */
  public static PausableScheduledExecutorService newLimitedScheduledExecutorService(
      ExecutorService delegate, int concurrency) {
    return new DelegatingPausableScheduledExecutorService(
        newLimitedExecutorService(delegate, concurrency), ExecutorsRegistrar.SCHEDULER.get());
  }

  /** Returns a direct executor. */
  public static Executor directExecutor() {
    return DirectExecutor.INSTANCE;
  }

  private enum DirectExecutor implements Executor {
    INSTANCE;

    @Override
    public void execute(Runnable command) {
      command.run();
    }
  }
}
