// Copyright 2019 Google LLC
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

package com.google.firebase.firestore.util;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * A queue for parallel execution of independent tasks.
 *
 * <p>All tasks must return the same result type T and results can be be fetched via
 * `awaitResults()`. It is an error to enqueue tasks after invoking `awaitResults()`.
 *
 * <p>This class is not thread-safe. All public methods must be called from the same thread.
 */
public class TaskQueue<T> {
  private BlockingQueue<TaskResult<T>> taskResults = new PriorityBlockingQueue<>();
  private Semaphore completedTasks = new Semaphore(0);
  private int totalTaskCount = 0;
  private boolean acceptsTasks = true;

  /** A single result from a background task. Encapsulates the task number to allow for sorting. */
  private static class TaskResult<T> implements Comparable<TaskResult<T>> {
    final int numTask;
    final T result;
    final Exception exception;

    private TaskResult(int numTask, T result) {
      this.numTask = numTask;
      this.result = result;
      this.exception = null;
    }

    private TaskResult(int numTask, Exception exception) {
      this.numTask = numTask;
      this.result = null;
      this.exception = exception;
    }

    @Override
    public int compareTo(TaskResult<T> other) {
      return Integer.compare(numTask, other.numTask);
    }
  }

  /** Enqueue a task on Android's THREAD_POOL_EXECUTOR. */
  public void enqueueInBackground(Callable<T> task) {
    hardAssert(acceptsTasks, "enqueueInBackground() called after awaitResults()");

    int currentTask = ++totalTaskCount;
    Executors.BACKGROUND_EXECUTOR.execute(
        () -> {
          try {
            T result = task.call();
            taskResults.add(new TaskResult<>(currentTask, result));
          } catch (Exception e) {
            taskResults.add(new TaskResult<>(currentTask, e));
          }
          completedTasks.release();
        });
  }

  /** Executes a task inline, allowing for more efficient execution of small tasks. */
  public void enqueueInline(Callable<T> callable) {
    hardAssert(acceptsTasks, "enqueueInBackground() called after awaitResults()");

    int currentTask = ++totalTaskCount;
    try {
      T result = callable.call();
      taskResults.add(new TaskResult<>(currentTask, result));
    } catch (Exception e) {
      taskResults.add(new TaskResult<>(currentTask, e));
    }
    completedTasks.release();
  }

  /**
   * Blocks on the execution of all tasks. Returns a list of results in the order that tasks where
   * added.
   */
  public List<T> awaitResults() throws ExecutionException {
    acceptsTasks = false;

    List<T> allResults = new ArrayList<>(totalTaskCount);
    try {
      completedTasks.acquire(totalTaskCount);
      while (!taskResults.isEmpty()) {
        TaskResult<T> currentResult = taskResults.take();

        if (currentResult.exception != null) {
          throw new ExecutionException("Unhandled exception in task", currentResult.exception);
        } else {
          allResults.add(currentResult.result);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return allResults;
  }
}
