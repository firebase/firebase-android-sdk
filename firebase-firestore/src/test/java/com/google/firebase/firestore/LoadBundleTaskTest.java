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

package com.google.firebase.firestore;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import com.google.android.gms.tasks.Task;
import java.lang.reflect.Method;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class LoadBundleTaskTest {
  static final LoadBundleTaskProgress SUCCESS_RESULT =
      new LoadBundleTaskProgress(0, 0, 0, 0, null, LoadBundleTaskProgress.TaskState.SUCCESS);
  static final Exception TEST_EXCEPTION = new Exception("Test Exception");
  static final String TEST_THREAD_NAME = "test-thread";

  @Rule public ErrorCollector collector = new ErrorCollector();

  Executor testExecutor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r);
            t.setName(TEST_THREAD_NAME);
            return t;
          });
  ActivityController<Activity> activityController =
      Robolectric.buildActivity(Activity.class).create();
  Activity activity = activityController.get();

  @Test
  public void testImplementsAllTaskInterface() {
    // Check if the internal gms Hide annotation is accessible.
    Class hideClazz = null;
    try {
      hideClazz = Class.forName("com.google.android.gms.common.internal.Hide");
    } catch (ClassNotFoundException e) {
      // Swallow the exception.
    }

    for (Method method : Task.class.getDeclaredMethods()) {
      // This method is annotated with @Hide, skipping.
      if (hideClazz != null && method.getAnnotation(hideClazz) != null) {
        continue;
      }

      try {
        LoadBundleTask.class.getDeclaredMethod(method.getName(), method.getParameterTypes());
      } catch (NoSuchMethodException e) {
        fail(
            "'LoadBundleTask' is expected to override all methods in 'Task', but it is missing "
                + method.toGenericString());
      }
    }
  }

  @Test
  public void testSuccessListener() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(3);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnSuccessListener(progress -> latch.countDown());
    task.addOnSuccessListener(testExecutor, progress -> latch.countDown());
    task.addOnSuccessListener(activity, progress -> latch.countDown());

    task.setResult(SUCCESS_RESULT);

    latch.await();
  }

  @Test
  public void testFailureListener() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(3);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnFailureListener(progress -> latch.countDown());
    task.addOnFailureListener(testExecutor, progress -> latch.countDown());
    task.addOnFailureListener(activity, progress -> latch.countDown());

    task.setException(TEST_EXCEPTION);

    latch.await();
  }

  @Test
  public void testCompleteListener() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(3);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnCompleteListener(progress -> latch.countDown());
    task.addOnCompleteListener(testExecutor, progress -> latch.countDown());
    task.addOnCompleteListener(activity, progress -> latch.countDown());

    task.setResult(SUCCESS_RESULT);

    latch.await();
  }

  @Test
  public void testProgressListener() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(3);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnProgressListener(progress -> latch.countDown());
    task.addOnProgressListener(testExecutor, progress -> latch.countDown());
    task.addOnProgressListener(activity, progress -> latch.countDown());

    task.updateProgress(SUCCESS_RESULT);

    latch.await();
  }

  @Test
  public void testProgressListenerFiresInOrder() throws InterruptedException {
    BlockingQueue<Integer> blockingQueue = new ArrayBlockingQueue<>(2);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnProgressListener(testExecutor, progress -> blockingQueue.add(1));
    task.addOnProgressListener(testExecutor, progress -> blockingQueue.add(2));

    task.updateProgress(SUCCESS_RESULT);

    assertEquals(1, (long) blockingQueue.take());
    assertEquals(2, (long) blockingQueue.take());
  }

  @Test
  public void testProgressListenerFireOnSpecifiedExecutor() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(2);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnProgressListener(
        p -> {
          try {
            collector.checkThat(Thread.currentThread().getName(), not(equalTo(TEST_THREAD_NAME)));
          } finally {
            latch.countDown();
          }
        });
    task.addOnProgressListener(
        testExecutor,
        p -> {
          try {
            collector.checkThat(Thread.currentThread().getName(), equalTo(TEST_THREAD_NAME));
          } finally {
            latch.countDown();
          }
        });

    task.updateProgress(SUCCESS_RESULT);

    latch.await();
  }

  @Test
  public void testProgressListenerCanAddProgressListener() throws InterruptedException {
    BlockingQueue<Integer> blockingQueue = new ArrayBlockingQueue<>(3);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnProgressListener(
        p1 -> {
          blockingQueue.add(1);
          task.addOnProgressListener(
              p2 -> {
                blockingQueue.add(2);
              });
        });

    task.updateProgress(SUCCESS_RESULT);
    assertEquals(1, (long) blockingQueue.take());

    task.updateProgress(SUCCESS_RESULT);
    assertEquals(1, (long) blockingQueue.take());
    assertEquals(2, (long) blockingQueue.take());
  }

  @Test
  public void testProgressListenerWithSuccess() throws InterruptedException {
    BlockingQueue<LoadBundleTaskProgress> actualSnapshots = new ArrayBlockingQueue<>(3);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnProgressListener(actualSnapshots::add);

    LoadBundleTaskProgress initialProgress =
        new LoadBundleTaskProgress(
            0, 10, 0, 10, /* exception= */ null, LoadBundleTaskProgress.TaskState.RUNNING);
    task.updateProgress(initialProgress);
    assertEquals(initialProgress, actualSnapshots.take());

    LoadBundleTaskProgress partialProgress =
        new LoadBundleTaskProgress(
            5, 10, 5, 10, /* exception= */ null, LoadBundleTaskProgress.TaskState.RUNNING);
    task.updateProgress(partialProgress);
    assertEquals(partialProgress, actualSnapshots.take());

    LoadBundleTaskProgress successProgress =
        new LoadBundleTaskProgress(
            10, 10, 10, 10, /* exception= */ null, LoadBundleTaskProgress.TaskState.SUCCESS);
    task.setResult(successProgress);
    assertEquals(successProgress, actualSnapshots.take());
  }

  @Test
  public void testProgressListenerWithException() throws InterruptedException {
    BlockingQueue<LoadBundleTaskProgress> actualSnapshots = new ArrayBlockingQueue<>(3);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnProgressListener(actualSnapshots::add);

    LoadBundleTaskProgress initialProgress =
        new LoadBundleTaskProgress(
            0, 10, 0, 10, /* exception= */ null, LoadBundleTaskProgress.TaskState.RUNNING);
    task.updateProgress(initialProgress);
    assertEquals(initialProgress, actualSnapshots.take());

    LoadBundleTaskProgress partialProgress =
        new LoadBundleTaskProgress(
            5, 10, 5, 10, /* exception= */ null, LoadBundleTaskProgress.TaskState.RUNNING);
    task.updateProgress(partialProgress);
    assertEquals(partialProgress, actualSnapshots.take());

    LoadBundleTaskProgress failureProgress =
        new LoadBundleTaskProgress(
            5, 10, 5, 10, TEST_EXCEPTION, LoadBundleTaskProgress.TaskState.ERROR);
    task.setException(TEST_EXCEPTION);
    assertEquals(failureProgress, actualSnapshots.take());
  }

  @Test
  public void testProgressListenerWithInitialException() throws InterruptedException {
    BlockingQueue<LoadBundleTaskProgress> actualSnapshots = new ArrayBlockingQueue<>(3);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnProgressListener(actualSnapshots::add);

    LoadBundleTaskProgress failureProgress =
        new LoadBundleTaskProgress(
            0, 0, 0, 0, TEST_EXCEPTION, LoadBundleTaskProgress.TaskState.ERROR);
    task.setException(TEST_EXCEPTION);
    assertEquals(failureProgress, actualSnapshots.take());
  }

  @Test
  public void testContinueWith() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(2);

    LoadBundleTask task = new LoadBundleTask();
    task.continueWith(
        task1 -> {
          latch.countDown();
          return null;
        });
    task.continueWith(
        testExecutor,
        task1 -> {
          latch.countDown();
          return null;
        });

    task.setResult(SUCCESS_RESULT);

    latch.await();
  }

  @Test
  public void testContinueWithTask() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(2);

    LoadBundleTask task = new LoadBundleTask();
    task.continueWithTask(
        task1 -> {
          latch.countDown();
          return null;
        });
    task.continueWithTask(
        testExecutor,
        task1 -> {
          latch.countDown();
          return null;
        });

    task.setResult(SUCCESS_RESULT);

    latch.await();
  }

  @Test
  public void testIsSuccessful() {
    LoadBundleTask task = new LoadBundleTask();
    assertFalse(task.isSuccessful());
    task.setResult(SUCCESS_RESULT);
    assertTrue(task.isSuccessful());
  }

  @Test
  public void testIsCompleteWithSuccess() {
    LoadBundleTask task = new LoadBundleTask();
    assertFalse(task.isComplete());
    task.setResult(SUCCESS_RESULT);
    assertTrue(task.isComplete());
  }

  @Test
  public void testIsCompleteWithFailure() {
    LoadBundleTask task = new LoadBundleTask();
    assertFalse(task.isComplete());
    task.setException(new Exception());
    assertTrue(task.isComplete());
  }

  @Test
  public void testGetResult() {
    LoadBundleTask task = new LoadBundleTask();
    task.setResult(SUCCESS_RESULT);
    assertEquals(SUCCESS_RESULT, task.getResult());
  }

  @Test
  public void testGetException() {
    LoadBundleTask task = new LoadBundleTask();
    task.setException(TEST_EXCEPTION);
    assertEquals(TEST_EXCEPTION, task.getException());
  }
}
