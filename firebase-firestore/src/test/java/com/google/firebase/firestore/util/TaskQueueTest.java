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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import org.junit.Test;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class TaskQueueTest {

  @Test
  public void canEnqueueInBackground() throws ExecutionException {
    TaskQueue<Integer> queue = new TaskQueue<>();
    queue.enqueueInBackground(() -> 1);
    queue.enqueueInBackground(() -> 2);
    queue.enqueueInBackground(() -> 3);
    List<Integer> result = queue.awaitResults();
    assertEquals(Arrays.asList(1, 2, 3), result);
  }

  @Test
  public void canEnqueueInline() throws ExecutionException {
    TaskQueue<Integer> queue = new TaskQueue<>();
    queue.enqueueInline(() -> 1);
    queue.enqueueInline(() -> 2);
    queue.enqueueInline(() -> 3);
    List<Integer> result = queue.awaitResults();
    assertEquals(Arrays.asList(1, 2, 3), result);
  }

  @Test(expected = ExecutionException.class)
  public void enqueueInBackgroundForwardsException() throws ExecutionException {
    TaskQueue<Integer> queue = new TaskQueue<>();
    queue.enqueueInBackground(() -> 1);
    queue.enqueueInBackground(
        () -> {
          throw new Exception("foo");
        });
    queue.enqueueInBackground(() -> 3);
    queue.awaitResults();
  }

  @Test(expected = ExecutionException.class)
  public void enqueueInlineForwardsException() throws ExecutionException {
    TaskQueue<Integer> queue = new TaskQueue<>();
    queue.enqueueInline(() -> 1);
    queue.enqueueInline(
        () -> {
          throw new Exception("foo");
        });
    queue.enqueueInline(() -> 3);
    queue.awaitResults();
  }

  @Test
  public void enqueuePreservesOrder() throws ExecutionException {
    Semaphore firstTaskCompleted = new Semaphore(0);
    Semaphore secondTaskCompleted = new Semaphore(0);
    Semaphore thirdTaskCompleted = new Semaphore(0);

    TaskQueue<Integer> queue = new TaskQueue<>();
    queue.enqueueInBackground(
        () -> {
          thirdTaskCompleted.acquire();
          return 1;
        });
    queue.enqueueInBackground(
        () -> {
          secondTaskCompleted.acquire();
          thirdTaskCompleted.release();
          return 2;
        });
    queue.enqueueInBackground(
        () -> {
          firstTaskCompleted.release();
          return 3;
        });
    queue.enqueueInline(
        () -> {
          firstTaskCompleted.acquire();
          secondTaskCompleted.release();
          return 4;
        });

    List<Integer> result = queue.awaitResults();
    assertEquals(Arrays.asList(1, 2, 3, 4), result);
  }
}
