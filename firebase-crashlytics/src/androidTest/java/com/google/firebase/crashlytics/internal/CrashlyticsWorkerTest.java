/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.concurrent.TestOnlyExecutors;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CrashlyticsWorkerTest {
  private CrashlyticsWorker crashlyticsWorker;

  @Before
  public void setUp() {
    crashlyticsWorker = new CrashlyticsWorker(TestOnlyExecutors.background());
  }

  @After
  public void tearDown() throws Exception {
    // Drain the worker, just in case any test cases would fail but didn't await.
    crashlyticsWorker.await();
  }

  @Test
  public void executesTasksOnThreadPool() throws Exception {
    Set<String> threads = new HashSet<>();

    // Find thread names by adding the names we touch to the set.
    for (int i = 0; i < 100; i++) {
      crashlyticsWorker.submit(() -> threads.add(Thread.currentThread().getName()));
    }

    crashlyticsWorker.await();

    // Verify that we touched at lease some of the expected background threads.
    assertThat(threads)
        .containsAnyOf(
            "Firebase Background Thread #0",
            "Firebase Background Thread #1",
            "Firebase Background Thread #2",
            "Firebase Background Thread #3");
  }

  @Test
  public void executesTasksInOrder() throws Exception {
    List<Integer> list = new ArrayList<>();

    // Add sequential numbers to the list to validate tasks execute in order.
    for (int i = 0; i < 100; i++) {
      int sequential = i;
      crashlyticsWorker.submit(() -> list.add(sequential));
    }

    crashlyticsWorker.await();

    // Verify that the tasks executed in order.
    assertThat(list).isInOrder();
  }

  @Test
  public void executesTasksSequentially() throws Exception {
    List<Integer> list = new ArrayList<>();
    AtomicBoolean reentrant = new AtomicBoolean(false);

    for (int i = 0; i < 100; i++) {
      int sequential = i;
      crashlyticsWorker.submit(
          () -> {
            if (reentrant.get()) {
              // Return early if two runnables ran at the same time.
              return;
            }

            reentrant.set(true);
            // Sleep a bit to simulate some work.
            sleep(5);
            list.add(sequential);
            reentrant.set(false);
          });
    }

    crashlyticsWorker.await();

    // Verify that all the runnable tasks executed, one at a time, and in order.
    assertThat(list).hasSize(100);
    assertThat(list).isInOrder();
  }

  @Test
  public void submitCallableThatReturns() throws Exception {
    String ender = "Remember, the enemy's gate is down.";
    Task<String> task = crashlyticsWorker.submit(() -> ender);

    String result = Tasks.await(task);

    assertThat(result).isEqualTo(ender);
  }

  @Test
  public void submitCallableThatReturnsNull() throws Exception {
    Task<String> task = crashlyticsWorker.submit(() -> null);

    String result = Tasks.await(task);

    assertThat(result).isNull();
  }

  @Test
  public void submitCallableThatThrows() {
    Task<Void> task =
        crashlyticsWorker.submit(
            () -> {
              throw new Exception("I threw in the callable");
            });

    ExecutionException thrown = assertThrows(ExecutionException.class, () -> Tasks.await(task));

    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("I threw in the callable");
  }

  @Test
  public void submitCallableThatThrowsThenReturns() throws Exception {
    Task<Void> throwingTask =
        crashlyticsWorker.submit(
            () -> {
              throw new IOException();
            });

    assertThrows(ExecutionException.class, () -> Tasks.await(throwingTask));

    String hiro =
        "When you are wrestling for possession of a sword, the man with the handle always wins.";
    Task<String> task = crashlyticsWorker.submitTask(() -> Tasks.forResult(hiro));

    String result = Tasks.await(task);

    assertThat(result).isEqualTo(hiro);
  }

  @Test
  public void submitRunnable() throws Exception {
    Task<Void> task = crashlyticsWorker.submit(() -> {});

    Void result = Tasks.await(task);

    // A Runnable does not return, so the task evaluates to null.
    assertThat(result).isNull();
  }

  @Test
  public void submitRunnableThatThrows() {
    Task<Void> task =
        crashlyticsWorker.submit(
            (Runnable)
                () -> {
                  throw new RuntimeException("I threw in the runnable");
                });

    ExecutionException thrown = assertThrows(ExecutionException.class, () -> Tasks.await(task));

    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("I threw in the runnable");
  }

  @Test
  public void submitRunnableThatThrowsThenReturns() throws Exception {
    Task<Void> thowingTask =
        crashlyticsWorker.submit(
            (Runnable)
                () -> {
                  throw new IllegalArgumentException();
                });

    assertThrows(ExecutionException.class, () -> Tasks.await(thowingTask));

    Task<Void> task = crashlyticsWorker.submit(() -> {});

    Void result = Tasks.await(task);

    assertThat(result).isNull();
  }

  @Test
  public void submitTaskThatReturns() throws Exception {
    String skippy = "Think of the problem as an enemy, and defeat them in detail.";
    Task<String> task = crashlyticsWorker.submitTask(() -> Tasks.forResult(skippy));

    String result = Tasks.await(task);

    assertThat(result).isEqualTo(skippy);
  }

  @Test
  public void submitTaskThatReturnsNull() throws Exception {
    Task<String> task = crashlyticsWorker.submitTask(() -> Tasks.forResult(null));

    String result = Tasks.await(task);

    assertThat(result).isNull();
  }

  @Test
  public void submitTaskThatThrows() {
    Task<String> task =
        crashlyticsWorker.submitTask(
            () -> Tasks.forException(new Exception("Thrown from a task.")));

    ExecutionException thrown = assertThrows(ExecutionException.class, () -> Tasks.await(task));

    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("Thrown from a task.");
  }

  @Test
  public void submitTaskThatThrowsThenReturns() throws Exception {
    crashlyticsWorker.submitTask(() -> Tasks.forException(new IllegalStateException()));
    Task<String> task = crashlyticsWorker.submitTask(() -> Tasks.forResult("The Hail Mary"));

    String result = Tasks.await(task);

    assertThat(result).isEqualTo("The Hail Mary");
  }

  @Test
  public void submitTaskThatCancels() {
    Task<Void> task = crashlyticsWorker.submitTask(Tasks::forCanceled);

    CancellationException thrown =
        assertThrows(CancellationException.class, () -> Tasks.await(task));

    assertThat(task.isCanceled()).isTrue();
    assertThat(thrown).hasMessageThat().contains("Task is already canceled");
  }

  @Test
  public void submitTaskThatCancelsThenReturns() throws Exception {
    crashlyticsWorker.submitTask(Tasks::forCanceled);
    Task<String> task = crashlyticsWorker.submitTask(() -> Tasks.forResult("Flying Dutchman"));

    String result = Tasks.await(task);

    assertThat(task.isCanceled()).isFalse();
    assertThat(result).isEqualTo("Flying Dutchman");
  }

  @Test
  public void submitTaskThatCancelsThenAwaitsThenReturns() throws Exception {
    Task<?> cancelled = crashlyticsWorker.submitTask(Tasks::forCanceled);

    // Await on the cancelled task to force the exception to propagate.
    assertThrows(CancellationException.class, () -> Tasks.await(cancelled));

    // Submit another task.
    Task<String> task = crashlyticsWorker.submitTask(() -> Tasks.forResult("Valkyrie"));

    String result = Tasks.await(task);

    assertThat(cancelled.isCanceled()).isTrue();
    assertThat(task.isCanceled()).isFalse();
    assertThat(result).isEqualTo("Valkyrie");
  }

  @Test
  public void submitTaskThatCancelsThenAwaitsThenCallable() throws Exception {
    Task<?> cancelled = crashlyticsWorker.submitTask(Tasks::forCanceled);

    // Await on the cancelled task to force the exception to propagate.
    assertThrows(CancellationException.class, () -> Tasks.await(cancelled));

    // Submit a simple callable.
    Task<Boolean> task = crashlyticsWorker.submit(() -> true);

    boolean result = Tasks.await(task);

    assertThat(cancelled.isCanceled()).isTrue();
    assertThat(task.isCanceled()).isFalse();
    assertThat(result).isTrue();
  }

  @Test
  public void submitTaskThatCancelsThenAwaitsThenRunnable() throws Exception {
    Task<?> cancelled = crashlyticsWorker.submitTask(Tasks::forCanceled);

    // Await on the cancelled task to force the exception to propagate.
    assertThrows(CancellationException.class, () -> Tasks.await(cancelled));

    // Submit an empty runnable.
    Task<Void> task = crashlyticsWorker.submit(() -> {});

    Void result = Tasks.await(task);

    assertThat(cancelled.isCanceled()).isTrue();
    assertThat(task.isCanceled()).isFalse();
    assertThat(result).isNull();
  }

  @Test
  public void submitTaskFromAnotherWorker() throws Exception {
    Task<String> otherTask =
        new CrashlyticsWorker(TestOnlyExecutors.blocking())
            .submit(() -> "Dog's fine. Just sleeping.");

    // This will not use a background thread while waiting for the task on blocking thread.
    Task<String> task = crashlyticsWorker.submitTask(() -> otherTask);

    String result = Tasks.await(task);
    assertThat(result).isEqualTo("Dog's fine. Just sleeping.");
  }

  @Test
  public void submitTaskFromAnotherWorkerThatThrows() throws Exception {
    Task<?> otherTask =
        new CrashlyticsWorker(TestOnlyExecutors.blocking())
            .submitTask(() -> Tasks.forException(new IndexOutOfBoundsException()));

    // Await on the throwing task to force the exception to propagate threw the local worker.
    Task<?> task = crashlyticsWorker.submitTask(() -> otherTask);
    assertThrows(ExecutionException.class, () -> Tasks.await(task));

    // Submit another task to local worker to verify the chain did not break.
    Task<Integer> localTask = crashlyticsWorker.submitTask(() -> Tasks.forResult(0x5f375a86));

    int localResult = Tasks.await(localTask);

    assertThat(otherTask.isSuccessful()).isFalse();
    assertThat(localTask.isSuccessful()).isTrue();
    assertThat(localResult).isEqualTo(0x5f375a86);
  }

  @Test
  public void submitTaskFromAnotherWorkerThatCancels() throws Exception {
    Task<?> otherCancelled =
        new CrashlyticsWorker(TestOnlyExecutors.blocking()).submitTask(Tasks::forCanceled);

    // Await on the cancelled task to force the exception to propagate threw the local worker.
    Task<?> task = crashlyticsWorker.submitTask(() -> otherCancelled);
    assertThrows(CancellationException.class, () -> Tasks.await(task));

    // Submit another task to local worker to verify the chain did not break.
    Task<Long> localTask = crashlyticsWorker.submitTask(() -> Tasks.forResult(0x5fe6eb50c7b537a9L));

    long localResult = Tasks.await(localTask);

    assertThat(otherCancelled.isCanceled()).isTrue();
    assertThat(localTask.isCanceled()).isFalse();
    assertThat(localResult).isEqualTo(0x5fe6eb50c7b537a9L);
  }

  @Test
  public void submitTaskFromAnotherWorkerDoesNotUseLocalThreads() throws Exception {
    // Setup a "local" worker.
    ThreadPoolExecutor localExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
    CrashlyticsWorker localWorker = new CrashlyticsWorker(localExecutor);

    // Use a task off crashlyticsWorker to represent an other task.
    Task<Integer> otherTask =
        crashlyticsWorker.submit(
            () -> {
              sleep(30);
              return localExecutor.getActiveCount();
            });

    // No active threads yet.
    assertThat(localExecutor.getActiveCount()).isEqualTo(0);

    // 1 active thread when doing a local task.
    assertThat(Tasks.await(localWorker.submit(localExecutor::getActiveCount))).isEqualTo(1);

    // 0 active local threads when waiting for other task.
    // Waiting for a task from another worker does not block a local thread.
    assertThat(Tasks.await(localWorker.submitTask(() -> otherTask))).isEqualTo(0);

    // 1 active thread when doing a task.
    assertThat(Tasks.await(localWorker.submit(localExecutor::getActiveCount))).isEqualTo(1);

    // No active threads after.
    assertThat(localExecutor.getActiveCount()).isEqualTo(0);
  }

  @Test
  public void submitTaskWhenThreadPoolFull() {
    // Fill the underlying executor thread pool.
    for (int i = 0; i < 10; i++) {
      crashlyticsWorker.getExecutor().execute(() -> sleep(40));
    }

    Task<Integer> task = crashlyticsWorker.submitTask(() -> Tasks.forResult(42));

    // The underlying thread pool is full with tasks that will take longer than this timeout.
    assertThrows(TimeoutException.class, () -> Tasks.await(task, 30, TimeUnit.MILLISECONDS));
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
