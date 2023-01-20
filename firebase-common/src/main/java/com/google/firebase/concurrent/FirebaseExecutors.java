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
import java.util.concurrent.ScheduledExecutorService;

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
   * Returns an {@link Executor} that limits the number of running tasks at a given time.
   *
   * <p>The executor uses the {@code delegate} in order to {@link Executor#execute(Runnable)
   * execute} each task, and does not create any threads of its own.
   *
   * @param delegate {@link Executor} used to execute tasks
   * @param concurrency max number of tasks executing concurrently
   */
  public static Executor newLimitedConcurrencyExecutor(Executor delegate, int concurrency) {
    return new LimitedConcurrencyExecutor(delegate, concurrency);
  }

  /**
   * Returns a {@link ExecutorService} that limits the number of running tasks at a given time.
   *
   * <p>The executor uses delegate in order to {@link Executor#execute(Runnable) execute} each task,
   * and does not create any threads of its own.
   *
   * @param delegate {@link ExecutorService} used to execute tasks
   * @param concurrency max number of tasks executing concurrently
   */
  public static ExecutorService newLimitedConcurrencyExecutorService(
      ExecutorService delegate, int concurrency) {
    return new LimitedConcurrencyExecutorService(delegate, concurrency);
  }

  /**
   * Returns a {@link ScheduledExecutorService} that limits the number of running tasks at a given
   * time.
   *
   * <p>The executor uses delegate in order to {@link Executor#execute(Runnable) execute} each task,
   * and does not create any threads of its own.
   *
   * @param delegate {@link ExecutorService} used to execute tasks
   * @param concurrency max number of tasks executing concurrently
   */
  public static ScheduledExecutorService newLimitedConcurrencyScheduledExecutorService(
      ExecutorService delegate, int concurrency) {
    return new DelegatingScheduledExecutorService(
        newLimitedConcurrencyExecutorService(delegate, concurrency),
        ExecutorsRegistrar.SCHEDULER.get());
  }

  /**
   * Returns a {@link PausableExecutor }.
   *
   * <p>The executor does not create any threads of its own and instead delegates to the {@code
   * delegate} executor.
   *
   * <p>While {@link PausableExecutor#pause() paused}, the executor queues tasks which are executed
   * when the executor is {@link PausableExecutor#resume() resumed}, tasks that are already being
   * executed will not be paused and will run to completion.
   */
  public static PausableExecutor newPausableExecutor(Executor delegate) {
    return new PausableExecutorImpl(false, delegate);
  }

  /**
   * Returns a {@link PausableExecutorService }.
   *
   * <p>The executor does not create any threads of its own and instead delegates to the {@code
   * delegate} executor.
   *
   * <p>While {@link PausableExecutor#pause() paused}, the executor queues tasks which are executed
   * when the executor is {@link PausableExecutor#resume() resumed}, tasks that are already being
   * executed will not be paused and will run to completion.
   */
  public static PausableExecutorService newPausableExecutorService(ExecutorService delegate) {
    return new PausableExecutorServiceImpl(false, delegate);
  }

  /**
   * Returns a {@link PausableScheduledExecutorService }.
   *
   * <p>The executor does not create any threads of its own and instead delegates to the {@code
   * delegate} executor.
   *
   * <p>While {@link PausableExecutor#pause() paused}, the executor queues tasks which are executed
   * when the executor is {@link PausableExecutor#resume() resumed}, tasks that are already being
   * executed will not be paused and will run to completion.
   */
  public static PausableScheduledExecutorService newPausableScheduledExecutorService(
      ScheduledExecutorService delegate) {
    return new PausableScheduledExecutorServiceImpl(
        newPausableExecutorService(delegate), ExecutorsRegistrar.SCHEDULER.get());
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
