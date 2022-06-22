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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LoadBundleTaskTest {
  static final LoadBundleTaskProgress SUCCESS_RESULT =
      new LoadBundleTaskProgress(0, 0, 0, 0, null, LoadBundleTaskProgress.TaskState.SUCCESS);
  static final Exception TEST_EXCEPTION = new Exception("Test Exception");
  static final String TEST_THREAD_NAME = "test-thread";

  static class TestActivity extends Activity {}

  @Rule
  public ActivityTestRule<ListenerRegistrationTest.TestActivity> activityTestRule =
      new ActivityTestRule<>(
          ListenerRegistrationTest.TestActivity.class,
          /*initialTouchMode=*/ false,
          /*launchActivity=*/ false);

  Executor testExecutor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r);
            t.setName(TEST_THREAD_NAME);
            return t;
          });

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
  public void testSuccessListener() {
    List<TaskCompletionSource> sources = taskSourceOf(3);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnSuccessListener(progress -> sources.get(0).setResult(0));
    task.addOnSuccessListener(testExecutor, progress -> sources.get(1).setResult(1));
    ListenerRegistrationTest.TestActivity activity =
        activityTestRule.launchActivity(/*intent=*/ null);
    task.addOnSuccessListener(activity, progress -> sources.get(2).setResult(2));

    task.setResult(SUCCESS_RESULT);

    assertSourcesResolveTo(sources, 0, 1, 2);
  }

  @Test
  public void testFailureListener() {
    List<TaskCompletionSource> sources = taskSourceOf(3);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnFailureListener(progress -> sources.get(0).setResult(0));
    task.addOnFailureListener(testExecutor, progress -> sources.get(1).setResult(1));
    ListenerRegistrationTest.TestActivity activity =
        activityTestRule.launchActivity(/*intent=*/ null);
    task.addOnFailureListener(activity, progress -> sources.get(2).setResult(2));

    task.setException(TEST_EXCEPTION);

    assertSourcesResolveTo(sources, 0, 1, 2);
  }

  @Test
  public void testCompleteListener() {
    List<TaskCompletionSource> sources = taskSourceOf(3);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnCompleteListener(progress -> sources.get(0).setResult(0));
    task.addOnCompleteListener(testExecutor, progress -> sources.get(1).setResult(1));
    ListenerRegistrationTest.TestActivity activity =
        activityTestRule.launchActivity(/*intent=*/ null);
    task.addOnCompleteListener(activity, progress -> sources.get(2).setResult(2));

    task.setResult(SUCCESS_RESULT);

    assertSourcesResolveTo(sources, 0, 1, 2);
  }

  @Test
  public void testProgressListener() throws InterruptedException {
    List<TaskCompletionSource> sources = taskSourceOf(3);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnProgressListener(progress -> sources.get(0).setResult(0));
    task.addOnProgressListener(testExecutor, progress -> sources.get(1).setResult(1));
    ListenerRegistrationTest.TestActivity activity =
        activityTestRule.launchActivity(/*intent=*/ null);
    task.addOnProgressListener(activity, progress -> sources.get(2).setResult(2));

    task.updateProgress(SUCCESS_RESULT);

    assertSourcesResolveTo(sources, 0, 1, 2);
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
    List<TaskCompletionSource> sources = taskSourceOf(2);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnProgressListener(
        p -> {
          try {
            assertNotEquals(Thread.currentThread().getName(), TEST_THREAD_NAME);
          } finally {
            sources.get(0).setResult(0);
          }
        });
    task.addOnProgressListener(
        testExecutor,
        p -> {
          try {
            assertEquals(Thread.currentThread().getName(), TEST_THREAD_NAME);
          } finally {
            sources.get(1).setResult(1);
          }
        });

    task.updateProgress(SUCCESS_RESULT);

    assertSourcesResolveTo(sources, 0, 1);
  }

  @Test
  public void testProgressListenerCanAddProgressListener() {
    List<TaskCompletionSource> sources = taskSourceOf(3);

    AtomicInteger outerTaskRun = new AtomicInteger();
    LoadBundleTask task = new LoadBundleTask();
    task.addOnProgressListener(
        p1 -> {
          sources.get(outerTaskRun.getAndIncrement()).setResult(outerTaskRun.get());
          task.addOnProgressListener(
              p2 -> {
                sources.get(2).setResult(3);
              });
        });

    // First update runs the outer listener, and registers the inner listener.
    task.updateProgress(SUCCESS_RESULT);

    waitFor(sources.get(0).getTask());
    // Second update runs the outer listener, then the inner listener.
    task.updateProgress(SUCCESS_RESULT);

    assertSourcesResolveTo(sources, 1, 2, 3);
  }

  @Test
  public void testProgressListenerWithSuccess() throws InterruptedException {
    BlockingQueue<LoadBundleTaskProgress> actualSnapshots = new ArrayBlockingQueue<>(3);

    LoadBundleTask task = new LoadBundleTask();
    task.addOnProgressListener(testExecutor, actualSnapshots::add);

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
    task.addOnProgressListener(testExecutor, actualSnapshots::add);

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
    task.addOnProgressListener(testExecutor, actualSnapshots::add);

    LoadBundleTaskProgress failureProgress =
        new LoadBundleTaskProgress(
            0, 0, 0, 0, TEST_EXCEPTION, LoadBundleTaskProgress.TaskState.ERROR);
    task.setException(TEST_EXCEPTION);
    assertEquals(failureProgress, actualSnapshots.take());
  }

  @Test
  public void testContinueWith() throws InterruptedException {
    List<TaskCompletionSource> sources = taskSourceOf(2);

    LoadBundleTask task = new LoadBundleTask();
    task.continueWith(
        task1 -> {
          sources.get(0).setResult(0);
          return null;
        });
    task.continueWith(
        testExecutor,
        task1 -> {
          sources.get(1).setResult(1);
          return null;
        });

    task.setResult(SUCCESS_RESULT);

    assertSourcesResolveTo(sources, 0, 1);
  }

  @Test
  public void testContinueWithTask() throws InterruptedException {
    List<TaskCompletionSource> sources = taskSourceOf(2);

    LoadBundleTask task = new LoadBundleTask();
    task.continueWithTask(
        task1 -> {
          sources.get(0).setResult(0);
          return null;
        });
    task.continueWithTask(
        testExecutor,
        task1 -> {
          sources.get(1).setResult(1);
          return null;
        });

    task.setResult(SUCCESS_RESULT);

    assertSourcesResolveTo(sources, 0, 1);
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

  private List<TaskCompletionSource> taskSourceOf(int size) {
    List<TaskCompletionSource> result = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      result.add(new TaskCompletionSource());
    }
    return result;
  }

  private void assertSourcesResolveTo(
      @NonNull List<TaskCompletionSource> sources, Object... expected) {
    Task<List<Object>> listTask =
        Tasks.whenAllSuccess(sources.stream().map(s -> s.getTask()).collect(Collectors.toList()));
    waitFor(listTask);
    assertArrayEquals(Arrays.stream(expected).toArray(), listTask.getResult().toArray());
  }
}
