// Copyright 2018 Google LLC
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

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.BuildConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.annotation.CheckReturnValue;

/** A helper class that allows to schedule/queue Runnables on a single threaded background queue. */
public class AsyncQueue {

  /**
   * Well-known "timer" IDs used when scheduling delayed tasks on the AsyncQueue. These IDs can then
   * be used from tests to check for the presence of tasks or to run them early.
   */
  public enum TimerId {
    /** ALL can be used with runDelayedTasksUntil() to run all timers. */
    ALL,

    /**
     * The following 4 timers are used with the listen and write streams. The IDLE timer is used to
     * close the stream due to inactivity. The CONNECTION_BACKOFF timer is used to restart a stream
     * once the appropriate backoff delay has elapsed.
     */
    LISTEN_STREAM_IDLE,
    LISTEN_STREAM_CONNECTION_BACKOFF,
    WRITE_STREAM_IDLE,
    WRITE_STREAM_CONNECTION_BACKOFF,

    /**
     * A timer used in OnlineStateTracker to transition from OnlineState UNKNOWN to OFFLINE after a
     * set timeout, rather than waiting indefinitely for success or failure.
     */
    ONLINE_STATE_TIMEOUT,
    /** A timer used to periodically attempt LRU Garbage collection */
    GARBAGE_COLLECTION
  }

  /**
   * Represents a Task scheduled to be run in the future on an AsyncQueue.
   *
   * <p>It is created via createAndScheduleDelayedTask().
   *
   * <p>Supports cancellation (via cancel()) and early execution (via skipDelay()).
   */
  public class DelayedTask {
    private final TimerId timerId;
    private final long targetTimeMs;
    private final Runnable task;
    // The ScheduledFuture returned by executor.schedule(). It is set to null after the task has
    // been run or canceled.
    private ScheduledFuture scheduledFuture;

    private DelayedTask(TimerId timerId, long targetTimeMs, Runnable task) {
      this.timerId = timerId;
      this.targetTimeMs = targetTimeMs;
      this.task = task;
    }

    /**
     * Schedules the DelayedTask. This is called immediately after construction by
     * createAndScheduleDelayedTask().
     */
    private void start(long delayMs) {
      scheduledFuture = executor.schedule(this::handleDelayElapsed, delayMs, TimeUnit.MILLISECONDS);
    }

    /** Runs the operation immediately (if it hasn't already been run or canceled). */
    void skipDelay() {
      handleDelayElapsed();
    }

    /**
     * Cancels the task if it hasn't already been executed or canceled.
     *
     * <p>As long as the task has not yet been run, calling cancel() (from a task already running on
     * the AsyncQueue) provides a guarantee that the task will not be run.
     */
    public void cancel() {
      verifyIsCurrentThread();
      if (scheduledFuture != null) {
        // NOTE: We don't rely on this cancel() succeeding since handleDelayElapsed() will become
        // a no-op anyway (since markDone() sets scheduledFuture to null).
        scheduledFuture.cancel(/*mayInterruptRunning=*/ false);
        markDone();
      }
    }

    private void handleDelayElapsed() {
      verifyIsCurrentThread();
      if (scheduledFuture != null) {
        markDone();
        task.run();
      }
    }

    /** Marks this delayed task as done, notifying the AsyncQueue that it should be removed. */
    private void markDone() {
      hardAssert(
          scheduledFuture != null, "Caller should have verified scheduledFuture is non-null.");
      scheduledFuture = null;
      removeDelayedTask(this);
    }
  }

  /**
   * Executes the given Callable on a specific executor and returns a Task that completes when the
   * Task returned from the Callable completes. Similar to Tasks.call, but takes a function that
   * returns a Task.
   *
   * @param executor The executor to run the task on.
   * @param task The Callable to execute.
   * @return A Task that will be completed when task's Task is complete.
   */
  public static <TResult> Task<TResult> callTask(Executor executor, Callable<Task<TResult>> task) {
    TaskCompletionSource<TResult> tcs = new TaskCompletionSource<>();
    executor.execute(
        () -> {
          try {
            task.call()
                .continueWith(
                    executor,
                    (Continuation<TResult, Void>)
                        task1 -> {
                          if (task1.isSuccessful()) {
                            tcs.setResult(task1.getResult());
                          } else {
                            tcs.setException(task1.getException());
                          }
                          return null;
                        });
          } catch (Exception e) {
            tcs.setException(e);
          } catch (Throwable t) {
            Exception e = new IllegalStateException("Unhandled throwable in callTask.", t);
            tcs.setException(e);
          }
        });
    return tcs.getTask();
  }

  /**
   * The single thread that will be used by the executor. This is created early and managed directly
   * so that it's possible later to make assertions about executing on the correct thread.
   */
  private final Thread thread;

  /** The single threaded executor that is backing this AsyncQueue */
  private final ScheduledThreadPoolExecutor executor;

  // Tasks scheduled to be queued in the future. Tasks are automatically removed after they are run
  // or canceled.
  // NOTE: We disallow duplicates currently, so this could be a Set<> which might have better
  // theoretical removal speed, except this list will always be small so ArrayList is fine.
  private final ArrayList<DelayedTask> delayedTasks;

  /** A ThreadFactory for a single, pre-created thread. */
  private class DelayedStartFactory implements Runnable, ThreadFactory {
    private final CountDownLatch latch = new CountDownLatch(1);
    private Runnable delegate;

    @Override
    public void run() {
      try {
        latch.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      delegate.run();
    }

    @Override
    public Thread newThread(@NonNull Runnable runnable) {
      hardAssert(delegate == null, "Only one thread may be created in an AsyncQueue.");
      delegate = runnable;
      latch.countDown();
      return thread;
    }
  }

  public AsyncQueue() {
    delayedTasks = new ArrayList<>();

    DelayedStartFactory threadFactory = new DelayedStartFactory();

    thread = Executors.defaultThreadFactory().newThread(threadFactory);
    thread.setName("FirestoreWorker");
    thread.setDaemon(true);
    thread.setUncaughtExceptionHandler((crashingThread, throwable) -> panic(throwable));

    executor =
        new ScheduledThreadPoolExecutor(1, threadFactory) {
          @Override
          protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            if (t == null && r instanceof Future<?>) {
              Future<?> future = (Future<?>) r;
              try {
                // Not all Futures will be done, e.g. when used with scheduledAtFixedRate
                if (future.isDone()) {
                  future.get();
                }
              } catch (CancellationException ce) {
                // Cancellation exceptions are okay, we expect them to happen sometimes
              } catch (ExecutionException ee) {
                t = ee.getCause();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
            if (t != null) {
              panic(t);
            }
          }
        };

    // Core threads don't time out, this only takes effect when we drop the number of required
    // core threads
    executor.setKeepAliveTime(3, TimeUnit.SECONDS);
  }

  public Executor getExecutor() {
    return executor;
  }

  /** Verifies that the current thread is the managed AsyncQueue thread. */
  public void verifyIsCurrentThread() {
    Thread current = Thread.currentThread();
    if (thread != current) {
      throw fail(
          "We are running on the wrong thread. Expected to be on the AsyncQueue "
              + "thread %s/%d but was %s/%d",
          thread.getName(), thread.getId(), current.getName(), current.getId());
    }
  }

  /**
   * Queue and run this Callable task immediately after every other already queued task.
   *
   * @param task The task to run.
   * @return A Task object that is resolved after the enqueued operation has completed, with the
   *     return value of the operation.
   */
  @CheckReturnValue
  public <T> Task<T> enqueue(Callable<T> task) {
    final TaskCompletionSource<T> completionSource = new TaskCompletionSource<>();
    try {
      executor.execute(
          () -> {
            try {
              completionSource.setResult(task.call());
            } catch (Exception e) {
              completionSource.setException(e);
              throw new RuntimeException(e);
            }
          });
    } catch (RejectedExecutionException e) {
      // The only way we can get here is if the AsyncQueue has panicked and we're now racing with
      // the post to the main looper that will crash the app.
      Logger.warn(AsyncQueue.class.getSimpleName(), "Refused to enqueue task after panic");
    }
    return completionSource.getTask();
  }

  /**
   * Queue and run this Runnable task immediately after every other already queued task.
   *
   * @param task The task to run.
   * @return A Task object that is resolved after the enqueued operation has completed.
   */
  @CheckReturnValue
  public Task<Void> enqueue(Runnable task) {
    return enqueue(
        () -> {
          task.run();
          return null;
        });
  }

  /**
   * Queue and run this Runnable task immediately after every other already queued task. Unlike
   * enqueue(), returns void instead of a Task Object for use when we have no need to "wait" on the
   * task completing.
   *
   * @param task The task to run.
   */
  @SuppressWarnings({"CheckReturnValue", "ResultOfMethodCallIgnored"})
  public void enqueueAndForget(Runnable task) {
    enqueue(task);
  }

  /**
   * Schedule a task after the specified delay.
   *
   * <p>The returned DelayedTask can be used to cancel the task prior to its running.
   *
   * @param timerId A TimerId that can be used from tests to check for the presence of this delayed
   *     task or to schedule it to run early.
   * @param delayMs The delay after which the task will run.
   * @param task The task to run
   * @return A DelayedTask instance that can be used for cancellation.
   */
  public DelayedTask enqueueAfterDelay(TimerId timerId, long delayMs, Runnable task) {
    // While not necessarily harmful, we currently don't expect to have multiple tasks with the same
    // timer id in the queue, so defensively reject them.
    hardAssert(
        !containsDelayedTask(timerId),
        "Attempted to schedule multiple operations with timer id %s.",
        timerId);

    DelayedTask delayedTask = createAndScheduleDelayedTask(timerId, delayMs, task);
    delayedTasks.add(delayedTask);

    return delayedTask;
  }

  /**
   * Immediately stops running any scheduled tasks and causes a "panic" (through crashing the app).
   *
   * <p>Should only be used for unrecoverable exceptions.
   *
   * @param t The Throwable that is caused the panic.
   */
  public void panic(Throwable t) {
    executor.shutdownNow();
    Handler handler = new Handler(Looper.getMainLooper());
    handler.post(
        () -> {
          if (t instanceof OutOfMemoryError) {
            // OOMs can happen if developers try to load too much data at once. Instead of treating
            // this as an internal error, give a hint that this might be due to excessive queries
            // in Firestore.
            OutOfMemoryError error =
                new OutOfMemoryError(
                    "Firestore ("
                        + BuildConfig.VERSION_NAME
                        + ") ran out of memory. "
                        + "Check your queries to make sure they are not loading an excessive "
                        + "amount of data.");
            error.initCause(t);
            throw error;
          } else {
            throw new RuntimeException(
                "Internal error in Firestore (" + BuildConfig.VERSION_NAME + ").", t);
          }
        });
  }

  /** Runs a task on the AsyncQueue, blocking until it completes. */
  @VisibleForTesting
  public void runSync(Runnable task) throws InterruptedException {
    Semaphore done = new Semaphore(0);
    Throwable[] t = new Throwable[1];
    enqueueAndForget(
        () -> {
          try {
            task.run();
          } catch (Throwable throwable) {
            t[0] = throwable;
          }
          done.release();
        });

    done.acquire(1);
    if (t[0] != null) {
      throw new RuntimeException("Synchronous task failed", t[0]);
    }
  }

  /** Determines if a delayed task with a particular timerId exists. */
  @VisibleForTesting
  public boolean containsDelayedTask(TimerId timerId) {
    for (DelayedTask delayedTask : delayedTasks) {
      if (delayedTask.timerId == timerId) {
        return true;
      }
    }
    return false;
  }

  /**
   * Runs some or all delayed tasks early, blocking until completion.
   *
   * @param lastTimerId Only delayed tasks up to and including one that was scheduled using this
   *     TimerId will be run. Method throws if no matching task exists. Pass TimerId.ALL to run all
   *     delayed tasks.
   */
  @VisibleForTesting
  public void runDelayedTasksUntil(TimerId lastTimerId) throws InterruptedException {
    runSync(
        () -> {
          hardAssert(
              lastTimerId == TimerId.ALL || containsDelayedTask(lastTimerId),
              "Attempted to run tasks until missing TimerId: %s",
              lastTimerId);

          // NOTE: For performance we could store the tasks sorted, but runDelayedTasksUntil()
          // is only called from tests, and the size is guaranteed to be small since we don't allow
          // duplicate TimerIds.
          Collections.sort(delayedTasks, (a, b) -> Long.compare(a.targetTimeMs, b.targetTimeMs));

          // We copy the list before enumerating to avoid concurrent modification as we remove
          // tasks.
          for (DelayedTask delayedTask : new ArrayList<>(delayedTasks)) {
            delayedTask.skipDelay();
            if (lastTimerId != TimerId.ALL && delayedTask.timerId == lastTimerId) {
              break;
            }
          }
        });
  }

  /**
   * Shuts down the AsyncQueue and releases resources after which no progress will ever be made
   * again.
   */
  public void shutdown() {
    // Will cause the executor to de-reference all threads, the best we can do
    executor.setCorePoolSize(0);
  }

  /**
   * Creates and returns a DelayedTask that has been scheduled to be executed on the provided queue
   * after the provided delayMs.
   *
   * @param timerId A TimerId identifying the type of operation this is.
   * @param delayMs The delay (ms) before the operation should be scheduled.
   * @param task The task to run.
   */
  private DelayedTask createAndScheduleDelayedTask(TimerId timerId, long delayMs, Runnable task) {
    long targetTimeMs = System.currentTimeMillis() + delayMs;
    DelayedTask delayedTask = new DelayedTask(timerId, targetTimeMs, task);
    delayedTask.start(delayMs);
    return delayedTask;
  }

  /** Called by DelayedTask to remove itself from our list of pending delayed tasks. */
  private void removeDelayedTask(DelayedTask task) {
    boolean found = delayedTasks.remove(task);
    hardAssert(found, "Delayed task not found.");
  }
}
