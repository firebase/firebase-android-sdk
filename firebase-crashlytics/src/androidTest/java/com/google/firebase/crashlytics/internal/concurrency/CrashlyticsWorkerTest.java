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

package com.google.firebase.crashlytics.internal.concurrency;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.crashlytics.internal.concurrency.ConcurrencyTesting.getThreadName;
import static com.google.firebase.crashlytics.internal.concurrency.ConcurrencyTesting.newNamedSingleThreadExecutor;
import static com.google.firebase.crashlytics.internal.concurrency.ConcurrencyTesting.sleep;
import static org.junit.Assert.assertThrows;

import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.concurrent.TestOnlyExecutors;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
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
    Set<String> threads = Collections.synchronizedSet(new HashSet<>());

    // Find thread names by adding the names we touch to the set.
    for (int i = 0; i < 100; i++) {
      crashlyticsWorker.submit(() -> threads.add(getThreadName()));
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
    Queue<Integer> list = new ConcurrentLinkedQueue<>();

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
    Queue<Integer> list = new ConcurrentLinkedQueue<>();
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
      crashlyticsWorker.execute(() -> sleep(40));
    }

    Task<Integer> task = crashlyticsWorker.submitTask(() -> Tasks.forResult(42));

    // The underlying thread pool is full with tasks that will take longer than this timeout.
    assertThrows(TimeoutException.class, () -> Tasks.await(task, 30, TimeUnit.MILLISECONDS));
  }

  @Test
  public void submitTaskThatReturnsWithContinuation() throws Exception {
    Task<String> result =
        crashlyticsWorker.submitTask(
            () -> Tasks.forResult(1337),
            task -> Tasks.forResult(Integer.toString(task.getResult())));

    assertThat(Tasks.await(result)).isEqualTo("1337");
  }

  @Test
  public void submitTaskThatThrowsWithContinuation() throws Exception {
    Task<String> result =
        crashlyticsWorker.submitTask(
            () -> Tasks.forException(new IndexOutOfBoundsException("Sometimes we look too far.")),
            task -> {
              if (task.getException() != null) {
                return Tasks.forResult("Task threw.");
              }
              return Tasks.forResult("I dunno how I got here?");
            });

    assertThat(Tasks.await(result)).isEqualTo("Task threw.");
  }

  @Test
  public void submitTaskWithContinuationThatThrows() throws Exception {
    Task<String> result =
        crashlyticsWorker.submitTask(
            () -> Tasks.forResult(7), task -> Tasks.forException(new IOException()));

    ExecutionException thrown = assertThrows(ExecutionException.class, () -> Tasks.await(result));

    assertThat(thrown).hasCauseThat().isInstanceOf(IOException.class);

    // Verify the worker still executes tasks after the continuation threw.
    assertThat(Tasks.await(crashlyticsWorker.submit(() -> 42))).isEqualTo(42);
  }

  @Test
  public void submitTaskThatCancelsWithContinuation() throws Exception {
    Task<String> result =
        crashlyticsWorker.submitTask(
            Tasks::forCanceled,
            task -> Tasks.forResult(task.isCanceled() ? "Task cancelled." : "What?"));

    assertThat(Tasks.await(result)).isEqualTo("Task cancelled.");
  }

  @Test
  public void submitTaskWithContinuationThatCancels() throws Exception {
    Task<String> result =
        crashlyticsWorker.submitTask(() -> Tasks.forResult(7), task -> Tasks.forCanceled());

    assertThrows(CancellationException.class, () -> Tasks.await(result));

    // Verify the worker still executes tasks after the continuation was cancelled.
    assertThat(Tasks.await(crashlyticsWorker.submit(() -> "jk"))).isEqualTo("jk");
  }

  @Test
  public void submitTaskOnSuccess() throws Exception {
    TaskCompletionSource<Integer> waitingSource = new TaskCompletionSource<>();
    Task<Integer> waitingTask = waitingSource.getTask();

    Task<String> task =
        crashlyticsWorker.submitTaskOnSuccess(
            () -> waitingTask,
            integerResult -> {
              // This gets called with the result when the waiting task resolves successfully.
              return Tasks.forResult(integerResult + " Success!");
            });

    waitingSource.trySetResult(1337);

    String result = Tasks.await(task);

    assertThat(result).isEqualTo("1337 Success!");
  }

  @Test
  public void submitTaskThatReturnsWithSuccessContinuation() throws Exception {
    Task<String> task =
        crashlyticsWorker.submitTaskOnSuccess(
            () -> Tasks.forResult(1337), integer -> Tasks.forResult(Integer.toString(integer)));

    String result = Tasks.await(task);

    assertThat(result).isEqualTo("1337");
  }

  @Test
  public void submitTaskThatThrowsWithSuccessContinuation() {
    Task<String> task =
        crashlyticsWorker.submitTaskOnSuccess(
            () -> Tasks.forException(new IndexOutOfBoundsException()),
            object -> Tasks.forResult("Still you don't believe."));

    ExecutionException thrown = assertThrows(ExecutionException.class, () -> Tasks.await(task));

    assertThat(thrown).hasCauseThat().isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  public void submitTaskWithSuccessContinuationThatThrows() throws Exception {
    Task<String> task =
        crashlyticsWorker.submitTaskOnSuccess(
            () -> Tasks.forResult(7), integer -> Tasks.forException(new IOException()));

    ExecutionException thrown = assertThrows(ExecutionException.class, () -> Tasks.await(task));

    assertThat(thrown).hasCauseThat().isInstanceOf(IOException.class);

    // Verify the worker still executes tasks after the success continuation threw.
    assertThat(Tasks.await(crashlyticsWorker.submit(() -> 42))).isEqualTo(42);
  }

  @Test
  public void submitTaskThatCancelsWithSuccessContinuation() throws Exception {
    Task<String> task =
        crashlyticsWorker.submitTaskOnSuccess(
            Tasks::forCanceled, object -> Tasks.forResult("Will set you free"));

    assertThrows(CancellationException.class, () -> Tasks.await(task));

    // Verify the worker still executes tasks after the task cancelled.
    assertThat(Tasks.await(crashlyticsWorker.submit(() -> 42))).isEqualTo(42);
  }

  @Test
  public void submitTaskWithSuccessContinuationThatCancels() throws Exception {
    Task<String> task =
        crashlyticsWorker.submitTaskOnSuccess(
            () -> Tasks.forResult(7), integer -> Tasks.forCanceled());

    assertThrows(CancellationException.class, () -> Tasks.await(task));

    // Verify the worker still executes tasks after the success continuation was cancelled.
    assertThat(Tasks.await(crashlyticsWorker.submit(() -> "jk"))).isEqualTo("jk");
  }

  @Test
  public void submitTaskWithContinuationExecutesInOrder() throws Exception {
    // The integers added to the list represent the order they should be executed in.
    Queue<Integer> list = new ConcurrentLinkedQueue<>();

    // Start the chain which adds 1, then kicks off tasks to add 6 & 7 later, but adds 2 before
    // executing the newly added tasks in the continuation.
    crashlyticsWorker.submitTask(
        () -> {
          list.add(1);

          // Sleep to give time for the tasks 3, 4, 5, 6 to be submitted.
          sleep(3);

          // We added the 1 and will add 2 in the continuation. And 3, 4, 5, 6 have been submitted.
          crashlyticsWorker.submit(() -> list.add(7));
          crashlyticsWorker.submit(() -> list.add(8));

          return Tasks.forResult(1);
        },
        task -> {
          // When the task 1 completes the next number to add is 2. Because all the other tasks
          // are just submitted, not executed yet.
          list.add(2);
          return Tasks.forResult("a");
        });

    // Submit task to add 3 since we just added 1 and know a continuation will add the 2.
    crashlyticsWorker.submit(() -> list.add(3));

    // Submit a waiting task that blocks adding 4 and 5 so we can kick it off later.
    TaskCompletionSource<Void> waitingSource = new TaskCompletionSource<>();
    Task<Void> waitingTask = waitingSource.getTask();

    crashlyticsWorker.submitTask(
        () ->
            waitingTask
                .continueWith(crashlyticsWorker, task -> list.add(4))
                .continueWith(crashlyticsWorker, task -> list.add(5)));

    // Submit task to add 6 after the waiting continuations to add 4, 5.
    crashlyticsWorker.submit(() -> list.add(6));

    // Kick off the waiting task to add 4, 5 now that 6 is queued up.
    waitingSource.trySetResult(null);

    crashlyticsWorker.await();

    // Verify the list is complete and in order.
    assertThat(list).isInOrder();
    assertThat(list).hasSize(8);
  }

  @Test
  public void tasksRunOnCorrectThreads() throws Exception {
    ExecutorService executor = newNamedSingleThreadExecutor("workerThread");
    CrashlyticsWorker worker = new CrashlyticsWorker(executor);

    ExecutorService otherExecutor = newNamedSingleThreadExecutor("otherThread");
    CrashlyticsWorker otherWorker = new CrashlyticsWorker(otherExecutor);

    // Submit a Runnable.
    worker.submit(
        () -> {
          // The runnable blocks an underlying thread.
          assertThat(getThreadName()).isEqualTo("workerThread");
        });

    // Submit a Callable.
    worker.submit(
        () -> {
          // The callable blocks an underlying thread.
          assertThat(getThreadName()).isEqualTo("workerThread");
          return null;
        });

    // Submit a Callable<Task>.
    worker.submitTask(
        () -> {
          // The callable itself blocks an underlying thread.
          assertThat(getThreadName()).isEqualTo("workerThread");
          return otherWorker.submit(
              () -> {
                // The called task blocks an underlying thread in its own executor.
                assertThat(getThreadName()).isEqualTo("otherThread");
              });
        });

    // Submit a Callable<Task> with a Continuation.
    worker.submitTask(
        () -> {
          // The callable itself blocks an underlying thread.
          assertThat(getThreadName()).isEqualTo("workerThread");
          return otherWorker.submitTask(
              () -> {
                // The called task blocks an underlying thread in its own executor.
                assertThat(getThreadName()).isEqualTo("otherThread");
                return Tasks.forResult(null);
              });
        },
        task -> {
          // The continuation blocks an underlying thread of the original worker.
          assertThat(getThreadName()).isEqualTo("workerThread");
          return Tasks.forResult(null);
        });

    // Await on the worker to force all the tasks to run their assertions.
    worker.await();
  }

  @Test
  public void executeContinuationOnWorker() throws Exception {
    Task<String> task =
        crashlyticsWorker
            .submit(() -> "Hello!")
            .continueWith(crashlyticsWorker, greetingTask -> getThreadName());

    String result = Tasks.await(task);
    assertThat(result).contains("Firebase Background Thread");
  }

  @Test
  public void executeContinuationInsideWorker() throws Exception {
    Task<String> task =
        crashlyticsWorker.submitTask(
            () ->
                Tasks.forResult("Hello!")
                    .continueWith(crashlyticsWorker, greetingTask -> getThreadName()));

    String result = Tasks.await(task);
    assertThat(result).contains("Firebase Background Thread");
  }

  @Test
  public void executeSuccessContinuationOnWorker() throws Exception {
    Task<String> task =
        crashlyticsWorker
            .submit(() -> "Ahoy-hoy!")
            .onSuccessTask(crashlyticsWorker, greeting -> Tasks.forResult(getThreadName()));

    String result = Tasks.await(task);
    assertThat(result).contains("Firebase Background Thread");
  }

  @Test
  public void executeSuccessContinuationInsideWorker() throws Exception {
    Task<String> task =
        crashlyticsWorker.submitTask(
            () ->
                Tasks.forResult("Ahoy-hoy!")
                    .onSuccessTask(
                        crashlyticsWorker, greeting -> Tasks.forResult(getThreadName())));

    String result = Tasks.await(task);
    assertThat(result).contains("Firebase Background Thread");
  }

  @Test
  public void executeSuccessContinuationOnExceptionOnWorker() throws Exception {
    Task<String> task =
        crashlyticsWorker
            .submitTask(() -> Tasks.forException(new IllegalStateException()))
            .onSuccessTask(crashlyticsWorker, greeting -> Tasks.forResult(getThreadName()));

    ExecutionException thrown = assertThrows(ExecutionException.class, () -> Tasks.await(task));
    assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);

    // Verify the chain did not break by adding the on success to a failed task.
    assertThat(Tasks.await(crashlyticsWorker.submitTask(() -> Tasks.forResult(7)))).isEqualTo(7);
  }

  @Test
  public void executeSuccessContinuationOnExceptionInsideWorker() throws Exception {
    Task<String> task =
        crashlyticsWorker.submitTask(
            () ->
                Tasks.forException(new IllegalStateException())
                    .onSuccessTask(
                        crashlyticsWorker, greeting -> Tasks.forResult(getThreadName())));

    ExecutionException thrown = assertThrows(ExecutionException.class, () -> Tasks.await(task));
    assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);

    // Verify the chain did not break by adding the on success to a failed task.
    assertThat(Tasks.await(crashlyticsWorker.submitTask(() -> Tasks.forResult(7)))).isEqualTo(7);
  }

  @Test
  public void executeContinuationThatThrowsOnWorker() {
    Task<String> task =
        crashlyticsWorker
            .submit(() -> "Aloha")
            .continueWithTask(
                crashlyticsWorker, greeting -> Tasks.forException(new ArithmeticException()));

    ExecutionException thrown = assertThrows(ExecutionException.class, () -> Tasks.await(task));
    assertThat(thrown).hasCauseThat().isInstanceOf(ArithmeticException.class);
  }

  @Test
  public void executeContinuationThatThrowsInsideWorker() {
    Task<String> task =
        crashlyticsWorker.submitTask(
            () ->
                Tasks.forResult("Aloha")
                    .continueWithTask(
                        crashlyticsWorker,
                        greeting -> Tasks.forException(new ArithmeticException())));

    ExecutionException thrown = assertThrows(ExecutionException.class, () -> Tasks.await(task));
    assertThat(thrown).hasCauseThat().isInstanceOf(ArithmeticException.class);
  }

  @Test
  public void executeContinuationThatCancelsOnWorker() throws Exception {
    Task<String> task =
        crashlyticsWorker
            .submit(() -> "Aloha")
            .continueWithTask(crashlyticsWorker, greeting -> Tasks.forCanceled());

    assertThrows(CancellationException.class, () -> Tasks.await(task));

    // Verify the chain did not break by the continuation cancelling.
    assertThat(Tasks.await(crashlyticsWorker.submitTask(() -> Tasks.forResult(7)))).isEqualTo(7);
  }

  @Test
  public void executeContinuationThatCancelsInsideWorker() throws Exception {
    Task<String> task =
        crashlyticsWorker.submitTask(
            () ->
                Tasks.forResult("Aloha")
                    .continueWithTask(crashlyticsWorker, greeting -> Tasks.forCanceled()));

    assertThrows(CancellationException.class, () -> Tasks.await(task));

    // Verify the chain did not break by the continuation cancelling.
    assertThat(Tasks.await(crashlyticsWorker.submitTask(() -> Tasks.forResult(7)))).isEqualTo(7);
  }

  @Test
  public void executeContinuationsOnOrInsideWorkerDoesNotDeadlock() throws Exception {
    // Create a single thread worker to catch potential deadlocks.
    CrashlyticsWorker worker = new CrashlyticsWorker(newNamedSingleThreadExecutor("single"));

    // Create a waiting task so we can queue up stuff before executing it.
    TaskCompletionSource<String> waiting = new TaskCompletionSource<>();

    Task<String> task =
        worker
            .submitTask(
                () -> waiting.getTask().continueWith(worker, greetingTask -> getThreadName()))
            .continueWith(worker, insideTask -> insideTask.getResult() + "-" + getThreadName());

    // Kick off the waiting task.
    waiting.trySetResult("Howdy!");

    String result = Tasks.await(task);

    // Verify both on and inside continuations ran on the worker's underlying executor.
    assertThat(result).contains("single-single");
  }

  @Test
  public void executeContinuationTasksOnOrInsideWorkerDoesNotDeadlock() throws Exception {
    // Create a single thread worker to catch potential deadlocks.
    CrashlyticsWorker worker = new CrashlyticsWorker(newNamedSingleThreadExecutor("single"));

    // Create a waiting task so we can queue up stuff before executing it.
    TaskCompletionSource<String> waiting = new TaskCompletionSource<>();

    Task<String> task =
        worker
            .submitTask(
                () ->
                    waiting
                        .getTask()
                        .continueWithTask(worker, greetingTask -> Tasks.forResult(getThreadName())))
            .continueWithTask(
                worker,
                insideTask -> Tasks.forResult(insideTask.getResult() + "-" + getThreadName()));

    // Kick off the waiting task.
    waiting.trySetResult("Howdy!");

    String result = Tasks.await(task);

    // Verify both on and inside continuations ran on the worker's underlying executor.
    assertThat(result).contains("single-single");
  }

  @Test
  public void cancelledTaskInMiddleDoesNotBreakChain() throws Exception {
    // List to keep track of tasks that successfully executed.
    Queue<String> list = new ConcurrentLinkedQueue<>();

    // Create a waiting task to block execution on the worker until more tasks are queued up.
    TaskCompletionSource<String> taskSource = new TaskCompletionSource<>();
    crashlyticsWorker.submitTask(taskSource::getTask);

    // Setup a waiting cancellation, so the task does not know it's cancelled when submitting more.
    CancellationTokenSource cancellationSource = new CancellationTokenSource();
    CancellationToken cancellationToken = cancellationSource.getToken();

    // Submit the first task that will cancel.
    crashlyticsWorker.submitTask(() -> new TaskCompletionSource<>(cancellationToken).getTask());

    // Submit a Runnable
    crashlyticsWorker.submit(
        () -> {
          list.add("runnable");
        });

    // Submit another task that will cancel.
    crashlyticsWorker.submitTask(() -> new TaskCompletionSource<>(cancellationToken).getTask());

    // Submit a Callable
    crashlyticsWorker.submit(() -> list.add("callable"));

    crashlyticsWorker.submitTask(() -> new TaskCompletionSource<>(cancellationToken).getTask());

    // Submit a Callable<Task>
    crashlyticsWorker.submitTask(
        () -> {
          list.add("callable task");
          return Tasks.forResult(null);
        });

    crashlyticsWorker.submitTask(() -> new TaskCompletionSource<>(cancellationToken).getTask());

    // Submit a Callable<Task> with a Continuation.
    crashlyticsWorker.submitTask(
        () -> Tasks.forResult(null),
        task -> {
          list.add("continuation");
          return Tasks.forResult(null);
        });

    // Trigger the cancellations.
    cancellationSource.cancel();
    taskSource.trySetResult("go!");

    crashlyticsWorker.await();

    // Verify that all types of tasks executed.
    assertThat(list).containsExactly("runnable", "callable", "callable task", "continuation");
  }
}
