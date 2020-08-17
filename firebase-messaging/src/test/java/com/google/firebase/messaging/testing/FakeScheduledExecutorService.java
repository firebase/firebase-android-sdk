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
package com.google.firebase.messaging.testing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fake implementation of {@link ScheduledExecutorService} that lets tests control when tasks are
 * executed. Note that this class is not thread-safe. Internally keeps a queue of {@link Runnable
 * commands} that were executed, and a different queue of {@link Runnable commands} that were
 * scheduled.
 */
public class FakeScheduledExecutorService extends AbstractExecutorService
    implements ScheduledExecutorService {

  // The clock operates in milliseconds.
  private static final TimeUnit INTERNAL_UNITS = TimeUnit.MILLISECONDS;

  private final MockClock clock;
  private final Queue<Runnable> executeQueue = new ConcurrentLinkedQueue<Runnable>();
  private final PriorityBlockingQueue<DelayedFuture<?>> scheduledQueue =
      new PriorityBlockingQueue<DelayedFuture<?>>();

  private volatile long nextSequenceId = 0; // for ordering scheduled actions
  private volatile boolean running = true;

  public FakeScheduledExecutorService() {
    this.clock = new MockClock();
  }

  @Override
  public boolean isShutdown() {
    return !running;
  }

  @Override
  public boolean isTerminated() {
    return isShutdown() && isEmpty();
  }

  @Override
  public void shutdown() {
    running = false;
  }

  @Override
  public List<Runnable> shutdownNow() {
    running = false;
    List<Runnable> commands = Lists.newArrayList();
    commands.addAll(executeQueue);
    commands.addAll(scheduledQueue);
    executeQueue.clear();
    scheduledQueue.clear();
    return commands;
  }

  /**
   * Blocks until all tasks have completed execution after a shutdown request, or the simulated
   * timeout occurs. All pending items on the execute queue are run, followed by all pending items
   * on the scheduled queue that are scheduled to run within the given timeout.
   *
   * @param timeout the maximum time to wait
   * @param unit the time unit of the timeout argument
   * @return <tt>true</tt> if this executor terminated and <tt>false</tt> if the timeout elapsed
   *     before termination
   */
  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) {
    checkState(!running);
    while (!executeQueue.isEmpty()) {
      runNext();
    }
    simulateSleepExecutingAllTasks(timeout, unit);
    return isEmpty(); // i.e. terminated
  }

  @Override
  public void execute(Runnable command) {
    assertRunning();
    executeQueue.add(command);
  }

  private void assertRunning() {
    if (!running) {
      throw new RejectedExecutionException();
    }
  }

  private void advanceClockBy(long millis) {
    try {
      clock.advanceBy(millis);
    } catch (InterruptedException e) {
      // People shouldn't use this with a SystemClock that would throw anyway.
    }
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    assertRunning();
    DelayedFuture future = new DelayedFuture<>(command, delay, unit);
    scheduledQueue.add(future);
    return future;
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    assertRunning();
    DelayedFuture<V> future = new DelayedCallable<V>(callable, delay, unit);
    scheduledQueue.add(future);
    return future;
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(
      Runnable command, long initialDelay, long period, TimeUnit unit) {
    assertRunning();
    DelayedFuture future = new FixedRateDelayedFuture<>(command, initialDelay, period, unit);
    scheduledQueue.add(future);
    return future;
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable command, long initialDelay, long delay, TimeUnit unit) {
    assertRunning();
    DelayedFuture future = new FixedDelayDelayedFuture<>(command, initialDelay, delay, unit);
    scheduledQueue.add(future);
    return future;
  }

  /**
   * Determines if there is at least one runnable in the execute queue.
   *
   * @return <tt>true</tt> iff there is at least one runnable
   */
  public boolean hasNext() {
    return !executeQueue.isEmpty();
  }

  /**
   * Gets a collection of the {@code Runnable}s that have been submitted for execution but not yet
   * run.
   *
   * @return a collection of {@code Runnable}s
   */
  public Collection<Runnable> getExecutedButNotRunTasks() {
    return Collections.unmodifiableCollection(executeQueue);
  }

  /**
   * Runs the next runnable in the execute queue.
   *
   * <p>Throws an unchecked throwable ({@code RuntimeException} or {@code Error}) if any {@code
   * Runnable} being run throws one, enabling this to work well with jmock expectations and junit
   * asserts.
   */
  public void runNext() {
    checkState(!executeQueue.isEmpty(), "execute queue must not be empty");
    Runnable runnable = executeQueue.remove();
    runnable.run();
  }

  /**
   * Runs all of the runnables in the execute queue.
   *
   * <p>Throws an unchecked throwable ({@code RuntimeException} or {@code Error}) if any {@code
   * Runnable} being run throws one, enabling this to work well with jmock expectations and junit
   * asserts.
   */
  public void runAll() {
    while (hasNext()) {
      runNext();
    }
  }

  /**
   * Determines if there are any runnables on either the execute or schedule queues.
   *
   * @return <tt>true</tt> iff there are no runnables on either queue
   */
  public boolean isEmpty() {
    return executeQueue.isEmpty() && scheduledQueue.isEmpty();
  }

  /**
   * Clears any runnables on the execute and schedule queues.
   *
   * @return the cleared runnables
   */
  public List<Runnable> clearAll() {
    List<Runnable> commands = Lists.newArrayList();
    commands.addAll(executeQueue);
    commands.addAll(scheduledQueue);
    executeQueue.clear();
    scheduledQueue.clear();
    return commands;
  }

  /**
   * Gets the simulated time, in millis.
   *
   * @return simulated time
   */
  @SuppressWarnings("GoodTime") // should return a java.time.Duration
  public long getSimulatedTime() {
    return clock.currentTimeMillis();
  }

  /**
   * Determines if there is at least one runnable in the scheduled queue.
   *
   * @return <tt>true</tt> iff there is at least one runnable
   */
  public boolean hasNextScheduledTask() {
    return !scheduledQueue.isEmpty();
  }

  /**
   * Gets the delay until next task.
   *
   * @param unit the time unit of the result
   * @return delay in the given unit.
   */
  @SuppressWarnings("GoodTime") // should return a java.time.Duration
  public long getDelayToNextTask(TimeUnit unit) {
    if (!hasNextScheduledTask()) {
      throw new IllegalStateException("No scheduled tasks found.");
    }
    DelayedFuture<?> future = scheduledQueue.peek();
    return future.getDelay(unit);
  }

  /**
   * Simulate sleeping, executing any scheduled events that are scheduled to run in the given number
   * of seconds. Does not run any other commands.
   *
   * <p>Throws an unchecked throwable ({@code RuntimeException} or {@code Error}) if any {@code
   * Runnable} being run throws one, enabling this to work well with jmock expectations and junit
   * asserts.
   *
   * <p>Note that no throwables will be thrown for any submitted {@code Callable}, you need to
   * unwrap the {@code ExecutionException}.
   *
   * @param seconds - seconds to pretend to wait
   * @return number of tasks run
   */
  @SuppressWarnings("GoodTime") // should accept a java.time.Duration
  public int simulateSleepExecutingAllTasks(long seconds) {
    return simulateSleepExecutingAllTasks(seconds, TimeUnit.SECONDS);
  }

  /**
   * Simulate sleeping, executing any scheduled events that are scheduled to run in the given period
   * of time. Does not run any other commands.
   *
   * <p>Throws an unchecked throwable ({@code RuntimeException} or {@code Error}) if any {@code
   * Runnable} being run throws one, enabling this to work well with jmock expectations and junit
   * asserts.
   *
   * <p>Note that no throwables will be thrown for any submitted {@code Callable}, you need to
   * unwrap the {@code ExecutionException}.
   *
   * @param timeout the maximum time to wait
   * @param unit the time unit of the timeout argument
   * @return number of tasks run
   */
  @SuppressWarnings("GoodTime") // should accept a java.time.Duration
  public int simulateSleepExecutingAllTasks(long timeout, TimeUnit unit) {
    checkArgument(timeout >= 0, "timeout (%s) cannot be negative", timeout);

    long stopTime = clock.currentTimeMillis() + unit.toMillis(timeout);
    boolean done = false;
    int tasksRun = 0;

    while (!done) {
      long delay = (stopTime - clock.currentTimeMillis());
      if (delay >= 0 && simulateSleepExecutingAtMostOneTask(delay, INTERNAL_UNITS)) {
        tasksRun++;
      } else {
        done = true;
      }
    }

    return tasksRun;
  }

  /**
   * Will run as an executor service for the given time period. All tasks submitted to {@code
   * execute()}, either before this method is called or by any tasks executed by it, will be run
   * before the next scheduled task is run.
   *
   * <p>Tasks submitted to execute by the last scheduled task run by this call will not be run.
   *
   * <p>Throws an unchecked throwable ({@code RuntimeException} or {@code Error}) if any {@code
   * Runnable} being run throws one, enabling this to work well with jmock expectations and junit
   * asserts.
   *
   * <p>Note that no throwables will be thrown for any submitted {@code Callable}, you need to
   * unwrap the {@code ExecutionException}.
   *
   * @param timeout the maximum time to wait
   * @param unit the time unit of the timeout argument
   */
  @SuppressWarnings("GoodTime") // should accept a java.time.Duration
  public void simulateNormalOperationFor(long timeout, TimeUnit unit) {
    checkArgument(timeout >= 0, "timeout (%s) cannot be negative", timeout);

    long stopTime = clock.currentTimeMillis() + unit.toMillis(timeout);
    boolean done = false;

    while (!done) {
      runAll();
      long delay = (stopTime - clock.currentTimeMillis());
      if (delay >= 0 && simulateSleepExecutingAtMostOneTask(delay, INTERNAL_UNITS)) {
      } else {
        done = true;
      }
    }
  }

  /**
   * Simulate sleeping, executing at most one scheduled event that is scheduled to run in the given
   * number of seconds. Does not run any other commands. Exits after a task is run, or the simulated
   * time ends, whichever is first.
   *
   * <p>Throws an unchecked throwable ({@code RuntimeException} or {@code Error}) if any {@code
   * Runnable} being run throws one, enabling this to work well with jmock expectations and junit
   * asserts.
   *
   * <p>Note that no throwables will be thrown for any submitted {@code Callable}, you need to
   * unwrap the {@code ExecutionException}.
   *
   * @param seconds - seconds to pretend to wait
   * @return <tt>true</tt> iff a task was run
   */
  @SuppressWarnings("GoodTime") // should accept a java.time.Duration
  public boolean simulateSleepExecutingAtMostOneTask(long seconds) {
    return simulateSleepExecutingAtMostOneTask(seconds, TimeUnit.SECONDS);
  }

  /**
   * Simulate sleeping, executing at most one scheduled event that is scheduled to run in the given
   * period of time. Does not run any other commands. Exits after a task is run, or the simulated
   * time ends, whichever is first.
   *
   * <p>Throws an unchecked throwable ({@code RuntimeException} or {@code Error}) if any {@code
   * Runnable} being run throws one, enabling this to work well with jmock expectations and junit
   * asserts.
   *
   * <p>Note that no throwables will be thrown for any submitted {@code Callable}, you need to
   * unwrap the {@code ExecutionException}.
   *
   * @param timeout the maximum time to wait
   * @param unit the time unit of the timeout argument
   * @return <tt>true</tt> iff a task was run
   */
  @SuppressWarnings("GoodTime") // should accept a java.time.Duration
  public boolean simulateSleepExecutingAtMostOneTask(long timeout, TimeUnit unit) {
    checkArgument(timeout >= 0, "timeout (%s) cannot be negative", timeout);
    if (scheduledQueue.isEmpty()) {
      advanceClockBy(unit.toMillis(timeout));
      return false;
    }

    DelayedFuture<?> future = scheduledQueue.peek();
    long delay = future.getDelay(unit);
    if (delay > timeout) {
      // Next event is too far in the future; delay the entire time
      advanceClockBy(unit.toMillis(timeout));
      return false;
    }

    scheduledQueue.poll();
    future.run(); // adjusts clock

    return true;
  }

  /**
   * Simulate sleeping until the next scheduled task is set to run, then run the task. Does not run
   * any other commands.
   *
   * <p>Throws an unchecked throwable ({@code RuntimeException} or {@code Error}) if any {@code
   * Runnable} being run throws one, enabling this to work well with jmock expectations and junit
   * asserts.
   *
   * <p>Note that no throwables will be thrown for any submitted {@code Callable}, you need to
   * unwrap the {@code ExecutionException}.
   *
   * @return <tt>true</tt> iff a task was run
   */
  public boolean simulateSleepExecutingAtMostOneTask() {
    if (scheduledQueue.isEmpty()) {
      return false;
    }

    DelayedFuture<?> future = scheduledQueue.poll();
    future.run(); // adjusts clock
    return true;
  }

  /**
   * Simulates wall clock advancing by {@code time} {@code unit}s. Does not run any commands.
   *
   * <p>This is useful to simulate cases where, due to android device deep sleep or nondeterministic
   * timing, submitted commands execute late.
   */
  @SuppressWarnings("GoodTime") // should accept a java.time.Duration
  public void advanceClockWithoutExecuting(long time, TimeUnit unit) {
    advanceClockBy(unit.toMillis(time));
  }

  private class DelayedFuture<T> implements ScheduledFuture<T>, Runnable {
    protected long timeToRun;
    private final long sequenceId;
    private final Runnable command;
    private boolean cancelled;
    private boolean done;

    public DelayedFuture(Runnable command, long delay, TimeUnit unit) {
      checkArgument(delay >= 0, "delay (%s) cannot be negative", delay);

      this.command = command;
      timeToRun = clock.currentTimeMillis() + unit.toMillis(delay);
      sequenceId = nextSequenceId++;
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(timeToRun - clock.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    protected void maybeReschedule() {
      done = true;
    }

    @Override
    public void run() {
      if (clock.currentTimeMillis() < timeToRun) {
        advanceClockBy(timeToRun - clock.currentTimeMillis());
      }
      command.run();
      maybeReschedule();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      cancelled = true;
      done = true;
      return scheduledQueue.remove(this);
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public boolean isDone() {
      return done;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      return null;
    }

    @Override
    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return null;
    }

    @Override
    public int compareTo(Delayed other) {
      if (other == this) { // compare zero ONLY if same object
        return 0;
      }
      DelayedFuture<?> that = (DelayedFuture<?>) other;
      long diff = timeToRun - that.timeToRun;
      if (diff < 0) {
        return -1;
      } else if (diff > 0) {
        return 1;
      } else if (sequenceId < that.sequenceId) {
        return -1;
      } else {
        return 1;
      }
    }
  }

  private class DelayedCallable<T> extends DelayedFuture<T> {
    private final FutureTask<T> task;

    private DelayedCallable(FutureTask<T> task, long delay, TimeUnit unit) {
      super(task, delay, unit);
      this.task = task;
    }

    public DelayedCallable(Callable<T> callable, long delay, TimeUnit unit) {
      this(new FutureTask<T>(callable), delay, unit);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      task.cancel(mayInterruptIfRunning);
      return super.cancel(mayInterruptIfRunning);
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      return task.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return task.get(timeout, unit);
    }
  }

  /**
   * Fake delayed future for executing simulated tasks at a fixed rate--the time for starting the
   * next repetition is independent of how long it took to run the previous time.
   */
  private class FixedRateDelayedFuture<T> extends DelayedFuture<T> {
    private final long period;

    public FixedRateDelayedFuture(Runnable command, long delay, long period, TimeUnit unit) {
      super(command, delay, unit);
      this.period = INTERNAL_UNITS.convert(period, unit);
    }

    @Override
    protected void maybeReschedule() {
      if (isCancelled()) {
        return;
      }
      timeToRun += period;
      scheduledQueue.add(this);
    }
  }

  /**
   * Fake delayed future for executing simulated tasks at a fixed delay--the time for starting the
   * next iteration depends on how long it took to run the previous iteration.
   */
  private class FixedDelayDelayedFuture<T> extends DelayedFuture<T> {
    private final long period;

    public FixedDelayDelayedFuture(Runnable command, long delay, long period, TimeUnit unit) {
      super(command, delay, unit);
      this.period = INTERNAL_UNITS.convert(period, unit);
    }

    @Override
    protected void maybeReschedule() {
      if (isCancelled()) {
        return;
      }
      // Note that fakeTime might have changed inside runAndReset() - only in
      // those cases does this behave differently than FixedRateDelayedFuture.
      timeToRun = clock.currentTimeMillis() + period;
      scheduledQueue.add(this);
    }
  }
}
