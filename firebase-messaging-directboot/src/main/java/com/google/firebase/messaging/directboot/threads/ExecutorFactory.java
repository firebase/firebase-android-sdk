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

package com.google.firebase.messaging.directboot.threads;

import com.google.errorprone.annotations.CompileTimeConstant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Factory to work as a drop-in replacement for {@link java.util.concurrent.Executors}.
 *
 * <ul>
 *   <li>Specifying a ThreadFactory is not allowed, since thread creation is not at the discretion
 *       of the client.
 *   <li>The core pool size is decided globally, and is not at the discretion of any individual
 *       client.
 *   <li>Setting the current thread priority or name will affect future tasks and is banned.
 * </ul>
 *
 * <p>One divergence from Executors is that unused threads are allowed to time out after a period of
 * inactivity, so subsequent semi-cold starts may experience a tiny bit of latency. It is not
 * expected to be a significant performance concern, but will save significant memory.
 */
public interface ExecutorFactory {

  /**
   * Creates a thread pool that creates new threads as needed, but will reuse previously constructed
   * threads when they are available.
   *
   * <p>Drop-in replacement for {@link java.util.concurrent.Executors#newCachedThreadPool()}.
   *
   * @param priority see {@link ThreadPriority}.
   */
  public ExecutorService newThreadPool(ThreadPriority priority);

  /**
   * Creates a thread pool that creates new threads as needed, but will reuse previously constructed
   * threads when they are available.
   *
   * <p>Drop-in replacement for {@link
   * java.util.concurrent.Executors#newCachedThreadPool(ThreadFactory)}.
   *
   * @param threadFactory the factory to use when creating new threads.
   * @param priority see {@link ThreadPriority}.
   */
  public ExecutorService newThreadPool(ThreadFactory threadFactory, ThreadPriority priority);

  /**
   * Creates a thread pool that creates new threads as needed, but will reuse previously constructed
   * threads when they are available.
   *
   * <p>Drop-in replacement for {@link java.util.concurrent.Executors#newFixedThreadPool(int)}.
   *
   * @param maxConcurrency at most this number of tasks will be executed concurrently. Overflow
   *     tasks will be placed into an unbounded queue.
   * @param priority see {@link ThreadPriority}.
   */
  public ExecutorService newThreadPool(int maxConcurrency, ThreadPriority priority);

  /**
   * Creates a thread pool that creates new threads as needed, but will reuse previously constructed
   * threads when they are available.
   *
   * <p>Drop-in replacement for {@link java.util.concurrent.Executors#newFixedThreadPool(int,
   * ThreadFactory)}.
   *
   * @param maxConcurrency at most this number of tasks will be executed concurrently. Overflow
   *     tasks will be placed into an unbounded queue.
   * @param threadFactory the factory to use when creating new threads.
   * @param priority see {@link ThreadPriority}.
   */
  public ExecutorService newThreadPool(
      int maxConcurrency, ThreadFactory threadFactory, ThreadPriority priority);

  /**
   * Creates an executor that mimics a single-threaded executor, in the sense that operations can
   * happen at most one-at-a-time.
   *
   * <p>Drop-in replacement for {@link java.util.concurrent.Executors#newSingleThreadExecutor()}.
   *
   * @param priority see {@link ThreadPriority}.
   */
  public ExecutorService newSingleThreadExecutor(ThreadPriority priority);

  /**
   * Creates an executor that mimics a single-threaded executor, in the sense that operations can
   * happen at most one-at-a-time.
   *
   * <p>Drop-in replacement for {@link
   * java.util.concurrent.Executors#newSingleThreadExecutor(ThreadFactory)}.
   *
   * @param threadFactory the factory to use when creating new threads.
   * @param priority see {@link ThreadPriority}.
   */
  public ExecutorService newSingleThreadExecutor(
      ThreadFactory threadFactory, ThreadPriority priority);

  /**
   * Creates a ScheduledThreadPool that allows executing tasks in the future. If you don't require
   * this functionality, just use {@link #newCachedThreadPool(int)}.
   *
   * <p>WARNING: Do not leak these, since these never terminate their threads.
   *
   * <p>Drop-in replacement for {@link java.util.concurrent.Executors#newScheduledThreadPool(int)}.
   *
   * @param maxConcurrency at most this number of tasks will be executed concurrently. Overflow
   *     tasks will be placed into an unbounded queue.
   * @param priority see {@link ThreadPriority}.
   */
  public ScheduledExecutorService newScheduledThreadPool(
      int maxConcurrency, ThreadPriority priority);

  /**
   * Creates a ScheduledThreadPool that allows executing tasks in the future. If you don't require
   * this functionality, just use {@link #newCachedThreadPool(int)}.
   *
   * <p>WARNING: Do not leak these, since these never terminate their threads.
   *
   * <p>Drop-in replacement for {@link java.util.concurrent.Executors#newScheduledThreadPool(int,
   * ThreadFactory)}.
   *
   * @param maxConcurrency at most this number of tasks will be executed concurrently. Overflow
   *     tasks will be placed into an unbounded queue.
   * @param threadFactory the factory to use when creating new threads.
   * @param priority see {@link ThreadPriority}.
   */
  public ScheduledExecutorService newScheduledThreadPool(
      int maxConcurrency, ThreadFactory threadFactory, ThreadPriority priority);

  /**
   * Executes a one-off task where previously {@code new Thread()} may have been used.
   *
   * <p>Note that this may use a global, unlimited thread pool. Thus, threads should not generally
   * try to manipulate the thread priority or name within the Runnable.
   *
   * @param moduleName name of the module
   * @param name name of the thread; only sometimes honored
   * @param priority see {@link ThreadPriority}.
   * @param runnable the Runnable to run
   */
  public void executeOneOff(
      @CompileTimeConstant String moduleName,
      @CompileTimeConstant String name,
      ThreadPriority priority,
      Runnable runnable);

  /**
   * Executes a one-off task where previously {@code new Thread()} may have been used.
   *
   * <p>This returns a Future to allow you to query the state; you can use {@code Future.isDone()}
   * instead of {@code !Thread.isAlive())}, and {@code Future.get()} instead of {@code
   * Thread.join()}.
   *
   * @param moduleName name of the module
   * @param name name of the thread; only sometimes honored
   * @param priority see {@link ThreadPriority}.
   * @param runnable the Runnable to run
   */
  public Future<?> submitOneOff(
      @CompileTimeConstant String moduleName,
      @CompileTimeConstant String name,
      ThreadPriority priority,
      Runnable runnable);
}
