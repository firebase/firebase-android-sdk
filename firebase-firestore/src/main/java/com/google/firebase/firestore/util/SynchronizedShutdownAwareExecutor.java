// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
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

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;

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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * A wrapper around a {@link ScheduledThreadPoolExecutor} class that provides:
 *
 * <ol>
 *   <li>Synchronized task scheduling. This is different from function 3, which is about task
 *       execution in a single thread.
 *   <li>Ability to do soft-shutdown: only critical tasks related to shutting Firestore SDK down
 *       can be executed once the shutdown process initiated.
 *   <li>Single threaded execution service, no concurrent execution among the `Runnable`s
 *       scheduled in this Executor.
 * </ol>
 */
class SynchronizedShutdownAwareExecutor implements Executor {
    public static final String ASYNC_QUEUE_IS_SHUTDOWN = "AsyncQueue is shutdown";
    /**
     * The single threaded executor that is backing this Executor. This is also the executor used
     * when some tasks explicitly request to run after shutdown has been initiated.
     */
    final ScheduledThreadPoolExecutor internalExecutor;

    /**
     * Task ss assigned when the shutdown process has been initiated, once it is started, it is not revertable.
     */
    private Task<Void> shutdownTask;

    private Runnable onShutdown = null;

    /**
     * The single thread that will be used by the executor. This is created early and managed
     * directly so that it's possible later to make assertions about executing on the correct
     * thread.
     */
    private final Thread thread;

    /**
     * A ThreadFactory for a single, pre-created thread.
     */
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

    // TODO(b/258277574): Migrate to go/firebase-android-executors
    @SuppressLint("ThreadPoolCreation")
    public SynchronizedShutdownAwareExecutor() {
        DelayedStartFactory threadFactory = new DelayedStartFactory();

        thread = Executors.defaultThreadFactory().newThread(threadFactory);
        thread.setName("FirestoreWorker");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((crashingThread, throwable) -> {
            shutdownNow();
            AsyncQueue.halt(throwable);
        });

        internalExecutor =
                new ScheduledThreadPoolExecutor(1, threadFactory) {
                    @Override
                    protected void afterExecute(Runnable r, Throwable t) {
                        super.afterExecute(r, t);
                        if (t == null && r instanceof Future<?>) {
                            Future<?> future = (Future<?>) r;
                            try {
                                // Not all Futures will be done, for example when used with scheduledAtFixedRate.
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
                        if (t != null && !ASYNC_QUEUE_IS_SHUTDOWN.equals(t.getMessage())) {
                            shutdownNow();
                            AsyncQueue.halt(t);
                        }
                    }
                };

        // Core threads don't time out, this only takes effect when we drop the number of required
        // core threads
        internalExecutor.setKeepAliveTime(3, TimeUnit.SECONDS);
    }

    private SynchronizedShutdownAwareExecutor(ScheduledThreadPoolExecutor internalExecutor, Thread thread) {
        this.internalExecutor = internalExecutor;
        this.thread = thread;
    }

    synchronized void verifyNotShutdown() {
        if (shutdownTask != null) {
            throw new RejectedExecutionException(ASYNC_QUEUE_IS_SHUTDOWN);
        }
    }

    void setOnShutdown(Runnable onShutdown) {
        verifyNotShutdown();
        hardAssert(this.onShutdown == null, "setOnShutdown can only be called once.");
        this.onShutdown = onShutdown;
    }

    /**
     * Synchronized access to isShuttingDown
     */
    synchronized boolean isShuttingDown() {
        return shutdownTask != null;
    }

    /**
     * Check if shutdown is initiated before scheduling. If it is initiated, the command will not be
     * executed.
     */

    void verifyIsCurrentThread() {
        Thread current = Thread.currentThread();
        if (thread != current) {
            throw fail(
                    "We are running on the wrong thread. Expected to be on the AsyncQueue "
                            + "thread %s/%d but was %s/%d",
                    thread.getName(), thread.getId(), current.getName(), current.getId());
        }
    }

    @Override
    public synchronized void execute(Runnable command) {
        verifyNotShutdown();
        internalExecutor.execute(command);
    }

    /**
     * Run a given `Callable` on this executor, and report the result of the `Callable` in a {@link
     * Task}. The `Callable` will not be run if the executor started shutting down already.
     *
     * @return A {@link Task} resolves when the requested `Callable` completes, or reports error
     * when the `Callable` runs into exceptions.
     */
    <T> Task<T> executeAndReportResult(Callable<T> task) {
        final TaskCompletionSource<T> completionSource = new TaskCompletionSource<>();
        try {
            this.execute(
                    () -> {
                        try {
                            completionSource.setResult(task.call());
                        } catch (Exception e) {
                            completionSource.setException(e);
                        }
                    });
        } catch (RejectedExecutionException e) {
            // The only way we can get here is if the AsyncQueue has panicked and we're now racing with
            // the post to the main looper that will crash the app.
            Logger.warn(AsyncQueue.class.getSimpleName(), "Refused to enqueue task after panic");
            completionSource.setException(e);
        }
        return completionSource.getTask();
    }

    /**
     * Initiate the shutdown process. Once called, the only possible way to run `Runnable`s are by
     * holding the `internalExecutor` reference.
     */
    synchronized Task<Void> shutdown() {
        if (shutdownTask == null) {
            shutdownTask = executeAndReportResult(() -> {
                if (this.onShutdown != null) {
                    this.onShutdown.run();
                }
                return null;
            });
        }
        return shutdownTask;
    }

    /**
     * Initiate the shutdown process and reduce thread pool to 0.
     */
    synchronized void terminate() {
        shutdown();

        // Will cause the executor to de-reference all threads, the best we can do
        internalExecutor.setCorePoolSize(0);
    }


    synchronized SynchronizedShutdownAwareExecutor reincarnate() {
        hardAssert(isShuttingDown(), "Executor must be shutting down to be eligible for reincarnation.");
        hardAssert(!isTerminated(), "Cannot reincarnate executor that is terminated.");
        return new SynchronizedShutdownAwareExecutor(internalExecutor, thread);
    }

    private boolean isTerminated() {
        return internalExecutor.getCorePoolSize() == 0;
    }

    /**
     * Wraps {@link ScheduledThreadPoolExecutor#schedule(Runnable, long, TimeUnit)} and provides
     * shutdown state check: the command will not be scheduled if the shutdown has been initiated.
     */
    synchronized ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        verifyNotShutdown();
        return internalExecutor.schedule(command, delay, unit);
    }

    /**
     * Wraps around {@link ScheduledThreadPoolExecutor#shutdownNow()}.
     */
    void shutdownNow() {
        internalExecutor.shutdownNow();
    }
}
