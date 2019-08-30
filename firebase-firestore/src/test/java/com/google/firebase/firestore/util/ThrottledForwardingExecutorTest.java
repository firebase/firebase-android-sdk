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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ThrottledForwardingExecutorTest {
  @Test
  public void limitsNumberOfForwardedTasks() throws InterruptedException {
    Semaphore completedTasks = new Semaphore(0);
    int maximumConcurrency = 10;

    CountingExecutor countingExecutor = new CountingExecutor();
    ThrottledForwardingExecutor throttledExecutor =
        new ThrottledForwardingExecutor(maximumConcurrency, countingExecutor);

    // Schedule more than `maximumConcurrency` parallel tasks and wait until all scheduling has
    // finished.
    int numTasks = maximumConcurrency + 1;
    CountDownLatch schedulingLatch = new CountDownLatch(1);
    for (int i = 0; i < numTasks; ++i) {
      int currentTask = i;
      throttledExecutor.execute(
          () -> {
            try {
              if (currentTask < maximumConcurrency) {
                // Block if we are running on the forwarded executor. We can't block the thread that
                // is running this test.
                schedulingLatch.await();
              }
              completedTasks.release();
            } catch (InterruptedException e) {
              fail("Unexpected InterruptedException: " + e);
            }
          });
    }
    schedulingLatch.countDown();

    // Verify that only `maximumConcurrency` tasks were forwarded to the executor.
    completedTasks.acquire(numTasks);
    assertEquals(maximumConcurrency, countingExecutor.getNumTasks());
  }

  @Test
  public void handlesRejectedExecutionException() {
    AtomicInteger result = new AtomicInteger(0);

    ThrottledForwardingExecutor executor =
        new ThrottledForwardingExecutor(
            10,
            command -> {
              throw new RejectedExecutionException();
            });

    executor.execute(result::incrementAndGet);

    assertEquals(1, result.get());
  }

  /** An executor that counts the number of tasks submitted. */
  private static class CountingExecutor implements Executor {
    int numTasks = 0;

    @Override
    public void execute(Runnable command) {
      ++numTasks;
      new Thread() {
        @Override
        public void run() {
          command.run();
        }
      }.start();
    }

    int getNumTasks() {
      return numTasks;
    }
  }
}
