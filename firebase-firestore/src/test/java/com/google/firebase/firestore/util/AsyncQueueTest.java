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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.util.AsyncQueue.DelayedTask;
import com.google.firebase.firestore.util.AsyncQueue.TimerId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class AsyncQueueTest {
  // In these generic tests the specific TimerIDs don't matter.
  private static final TimerId TIMER_ID_1 = TimerId.LISTEN_STREAM_CONNECTION_BACKOFF;
  private static final TimerId TIMER_ID_2 = TimerId.LISTEN_STREAM_IDLE;
  private static final TimerId TIMER_ID_3 = TimerId.WRITE_STREAM_CONNECTION_BACKOFF;

  private AsyncQueue queue;
  private ArrayList<Integer> completedSteps;
  private List<Integer> expectedSteps;
  private Semaphore expectedStepsCompleted;

  @Before
  public void before() {
    queue = new AsyncQueue();
    completedSteps = new ArrayList<>();
    expectedSteps = null;
    expectedStepsCompleted = new Semaphore(0);
  }

  /**
   * Helper that returns a Runnable that adds `n` to completedSteps and resolves
   * expectedStepsCompleted if the completedSteps match the expectedSteps.
   */
  private Runnable runnableForStep(int n) {
    return () -> {
      completedSteps.add(n);
      if (expectedSteps != null && completedSteps.size() >= expectedSteps.size()) {
        assertEquals(expectedSteps, completedSteps);
        assertEquals(
            "Expected steps already completed.", 0, expectedStepsCompleted.availablePermits());
        expectedStepsCompleted.release();
      }
    };
  }

  private void waitForExpectedSteps() {
    try {
      expectedStepsCompleted.acquire(1);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void canScheduleTasksInTheFuture() {
    expectedSteps = Arrays.asList(1, 2, 3, 4);
    queue.enqueueAndForget(runnableForStep(1));
    queue.enqueueAfterDelay(TIMER_ID_1, 5, runnableForStep(4));
    queue.enqueueAndForget(runnableForStep(2));
    queue.enqueueAfterDelay(TIMER_ID_2, 1, runnableForStep(3));
    waitForExpectedSteps();
  }

  @Test
  public void canCancelDelayedTasks() {
    expectedSteps = Arrays.asList(1, 3);
    // Queue everything from the queue to ensure nothing completes before we cancel.
    queue.enqueueAndForget(
        () -> {
          queue.enqueueAndForget(runnableForStep(1));
          DelayedTask step2Timer = queue.enqueueAfterDelay(TIMER_ID_1, 1, runnableForStep(2));
          queue.enqueueAfterDelay(TIMER_ID_3, 5, runnableForStep(3));

          assertTrue(queue.containsDelayedTask(TIMER_ID_1));
          step2Timer.cancel();
          assertFalse(queue.containsDelayedTask(TIMER_ID_1));
        });

    waitForExpectedSteps();
  }

  @Test
  public void canManuallyDrainAllDelayedTasksForTesting() throws Exception {
    queue.enqueueAndForget(runnableForStep(1));
    queue.enqueueAfterDelay(TIMER_ID_1, 20, runnableForStep(4));
    queue.enqueueAfterDelay(TIMER_ID_2, 10, runnableForStep(3));
    queue.enqueueAndForget(runnableForStep(2));

    queue.runDelayedTasksUntil(TimerId.ALL);
    assertEquals(Arrays.asList(1, 2, 3, 4), completedSteps);
  }

  @Test
  public void canManuallyDrainSpecificDelayedTasksForTesting() throws Exception {
    queue.enqueueAndForget(runnableForStep(1));
    queue.enqueueAfterDelay(TIMER_ID_1, 20, runnableForStep(5));
    queue.enqueueAfterDelay(TIMER_ID_2, 10, runnableForStep(3));
    queue.enqueueAfterDelay(TIMER_ID_3, 15, runnableForStep(4));
    queue.enqueueAndForget(runnableForStep(2));

    queue.runDelayedTasksUntil(TIMER_ID_3);
    assertEquals(Arrays.asList(1, 2, 3, 4), completedSteps);
  }

  @Test
  public void tasksAreScheduledWithRespectToShutdown() {
    expectedSteps = Arrays.asList(1, 2, 4);
    queue.enqueueAndForget(runnableForStep(1));

    // From this point on, `normal` tasks are not scheduled. Only those who explicitly request to
    // run after shutdown initiated will run.
    queue.enqueueAndInitiateShutdown(runnableForStep(2));

    queue.enqueueAndForget(runnableForStep(3));
    queue.enqueueAndForgetEvenAfterShutdown(runnableForStep(4));

    queue.getExecutor().execute(runnableForStep(5));
    waitForExpectedSteps();
  }
}
