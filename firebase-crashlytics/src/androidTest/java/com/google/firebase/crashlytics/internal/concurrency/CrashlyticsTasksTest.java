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
import static com.google.firebase.crashlytics.internal.concurrency.ConcurrencyTesting.sleep;
import static org.junit.Assert.assertThrows;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.concurrent.TestOnlyExecutors;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;

public class CrashlyticsTasksTest {

  @Test
  public void raceReturnsFirstResult() throws Exception {
    // Create 2 tasks on different workers to race.
    Task<String> task1 =
        new CrashlyticsWorker(TestOnlyExecutors.background())
            .submit(
                () -> {
                  sleep(20);
                  return "first";
                });
    Task<String> task2 =
        new CrashlyticsWorker(TestOnlyExecutors.background())
            .submit(
                () -> {
                  sleep(40);
                  return "slow";
                });

    Task<String> task = CrashlyticsTasks.race(task1, task2);
    String result = Tasks.await(task);

    assertThat(result).isEqualTo("first");
  }

  @Test
  public void raceReturnsFirstException() {
    // Create 2 tasks on different workers to race.
    Task<String> task1 =
        new CrashlyticsWorker(TestOnlyExecutors.background())
            .submitTask(
                () -> {
                  sleep(20);
                  return Tasks.forException(new ArithmeticException());
                });
    Task<String> task2 =
        new CrashlyticsWorker(TestOnlyExecutors.background())
            .submitTask(
                () -> {
                  sleep(40);
                  return Tasks.forException(new IllegalStateException());
                });

    Task<String> task = CrashlyticsTasks.race(task1, task2);
    ExecutionException thrown = assertThrows(ExecutionException.class, () -> Tasks.await(task));

    // The first task throws an ArithmeticException.
    assertThat(thrown).hasCauseThat().isInstanceOf(ArithmeticException.class);
  }

  @Test
  public void raceFirstCancelsReturnsSecondResult() throws Exception {
    // Create 2 tasks on different workers to race.
    Task<String> task1 =
        new CrashlyticsWorker(TestOnlyExecutors.background())
            .submitTask(
                () -> {
                  sleep(20);
                  return Tasks.forCanceled();
                });
    Task<String> task2 =
        new CrashlyticsWorker(TestOnlyExecutors.background())
            .submitTask(
                () -> {
                  sleep(40);
                  return Tasks.forResult("I am slow but didn't cancel.");
                });

    Task<String> task = CrashlyticsTasks.race(task1, task2);
    String result = Tasks.await(task);

    assertThat(result).isEqualTo("I am slow but didn't cancel.");
  }

  @Test
  public void raceBothCancel() {
    // Create 2 tasks on different workers to race.
    Task<String> task1 =
        new CrashlyticsWorker(TestOnlyExecutors.background())
            .submitTask(
                () -> {
                  sleep(20);
                  return Tasks.forCanceled();
                });
    Task<String> task2 =
        new CrashlyticsWorker(TestOnlyExecutors.background())
            .submitTask(
                () -> {
                  sleep(40);
                  return Tasks.forCanceled();
                });

    Task<String> task = CrashlyticsTasks.race(task1, task2);

    // Both cancelled, so cancel the race result.
    assertThrows(CancellationException.class, () -> Tasks.await(task));
  }

  @Test
  public void raceTasksOnSameWorker() throws Exception {
    CrashlyticsWorker worker = new CrashlyticsWorker(TestOnlyExecutors.background());

    // Create 2 tasks on the same worker to race.
    Task<String> task1 =
        worker.submit(
            () -> {
              sleep(20);
              return "first";
            });
    Task<String> task2 =
        worker.submit(
            () -> {
              sleep(30);
              return "second";
            });

    Task<String> task = CrashlyticsTasks.race(task1, task2);
    String result = Tasks.await(task);

    assertThat(result).isEqualTo("first");
  }

  @Test
  public void raceTasksOnSameSingleThreadWorker() throws Exception {
    CrashlyticsWorker worker = new CrashlyticsWorker(Executors.newSingleThreadExecutor());

    // Create 2 tasks on the same worker to race.
    Task<String> task1 = worker.submit(() -> "first");
    Task<String> task2 = worker.submit(() -> "second");

    Task<String> task = CrashlyticsTasks.race(task1, task2);
    String result = Tasks.await(task);

    // The first task is submitted to this single thread worker first, so will always be first.
    assertThat(result).isEqualTo("first");
  }

  @Test
  public void raceTaskOneOnWorkerAnotherNeverCompletes() throws Exception {
    // Create a task on a worker, and another that never completes, to race.
    Task<String> task1 =
        new CrashlyticsWorker(TestOnlyExecutors.background()).submit(() -> "first");
    Task<String> task2 = new TaskCompletionSource<String>().getTask();

    Task<String> task = CrashlyticsTasks.race(task1, task2);
    String result = Tasks.await(task);

    assertThat(result).isEqualTo("first");
  }

  @Test
  public void raceTaskOneOnWorkerAnotherOtherThatCompletesFirst() throws Exception {
    CrashlyticsWorker worker = new CrashlyticsWorker(TestOnlyExecutors.background());

    // Add a decoy task to the worker to take up some time.
    worker.submitTask(
        () -> {
          sleep(20);
          return Tasks.forResult(null);
        });

    // Create a task on this worker, and another, to race.
    Task<String> task1 = worker.submit(() -> "worker");
    TaskCompletionSource<String> task2 = new TaskCompletionSource<>();
    task2.trySetResult("other");

    Task<String> task = CrashlyticsTasks.race(task1, task2.getTask());
    String result = Tasks.await(task);

    // The other tasks completes first because the first task is queued up later on the worker.
    assertThat(result).isEqualTo("other");
  }

  @Test
  public void raceNoExecutor() throws Exception {
    // Create tasks with no explicit executor.
    TaskCompletionSource<String> task1 = new TaskCompletionSource<>();
    TaskCompletionSource<String> task2 = new TaskCompletionSource<>();

    Task<String> task = CrashlyticsTasks.race(task1.getTask(), task2.getTask());

    // Set a task result from another thread.
    new Thread(
            () -> {
              sleep(30);
              task1.trySetResult("yes");
            })
        .start();

    String result = Tasks.await(task);

    assertThat(result).isEqualTo("yes");
  }

  @Test
  public void raceTasksThatNeverResolve() {
    // Create tasks that will never resolve.
    Task<String> task1 = new TaskCompletionSource<String>().getTask();
    Task<String> task2 = new TaskCompletionSource<String>().getTask();

    Task<String> task = CrashlyticsTasks.race(task1, task2);

    // Since the tasks never resolve, the await will timeout.
    assertThrows(TimeoutException.class, () -> Tasks.await(task, 300, TimeUnit.MILLISECONDS));
  }
}
