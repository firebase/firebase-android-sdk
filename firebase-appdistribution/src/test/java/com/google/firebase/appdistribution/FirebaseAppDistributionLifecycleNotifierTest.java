// Copyright 2021 Google LLC
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

package com.google.firebase.appdistribution;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.appdistribution.TestUtils.assertTaskFailure;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.os.Looper;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.FirebaseAppDistributionLifecycleNotifier.ActivityConsumer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseAppDistributionLifecycleNotifierTest {
  private TestActivity activity;
  private FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;
  private ActivityConsumer activityConsumer;
  private SuccessContinuation<Activity, String> successContinuation;

  static class TestActivity extends Activity {}

  @Before
  public void setup() throws Exception {
    activity = Robolectric.buildActivity(TestActivity.class).create().get();
    lifecycleNotifier = new FirebaseAppDistributionLifecycleNotifier();

    activityConsumer = mock(ActivityConsumer.class);
    doAnswer(
            invocationOnMock -> {
              // Assert that consumer is called on the main thread
              assertThat(Looper.myLooper() == Looper.getMainLooper()).isTrue();
              return null;
            })
        .when(activityConsumer)
        .consume(any());

    successContinuation = mock(SuccessContinuation.class);
    doAnswer(
            invocationOnMock -> {
              // Assert that continuation is called on the main thread
              assertThat(Looper.myLooper() == Looper.getMainLooper()).isTrue();
              return Tasks.forResult("result");
            })
        .when(successContinuation)
        .then(any());
  }

  @Test
  public void applyToForegroundActivity_noCurrentActivity_succeedsAndCallsConsumerOnMainThread() {
    Task<Void> task = lifecycleNotifier.applyToForegroundActivity(activityConsumer);

    // Simulate an activity resuming
    lifecycleNotifier.onActivityResumed(activity);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isNull();
    verify(activityConsumer).consume(activity);
  }

  @Test
  public void applyToForegroundActivity_withCurrentActivity_succeedsAndCallsConsumer() {
    // Resume an activity so there is a current foreground activity already when
    // applyToForegroundActivity is called
    lifecycleNotifier.onActivityResumed(activity);

    Task<Void> task = lifecycleNotifier.applyToForegroundActivity(activityConsumer);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo(null);
    verify(activityConsumer).consume(activity);
  }

  @Test
  public void applyToForegroundActivity_onBackgroundThread_succeedsAndCallsConsumer()
      throws InterruptedException, ExecutionException {
    // Resume an activity so there is a current foreground activity already when
    // applyToForegroundActivityTask is called
    lifecycleNotifier.onActivityResumed(activity);

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    Future<Task<Void>> future =
        executorService.submit(() -> lifecycleNotifier.applyToForegroundActivity(activityConsumer));
    TestUtils.awaitAsyncOperations(executorService);

    Task<Void> task = future.get();
    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo(null);
    verify(activityConsumer).consume(activity);
  }

  @Test
  public void applyToForegroundActivity_consumerThrows_fails() {
    RuntimeException consumerException = new RuntimeException("exception in consumer");
    doThrow(consumerException).when(activityConsumer).consume(activity);
    Task<Void> task = lifecycleNotifier.applyToForegroundActivity(activityConsumer);

    // Simulate an activity resuming
    lifecycleNotifier.onActivityResumed(activity);

    assertTaskFailure(task, Status.UNKNOWN, "Unknown", consumerException);
  }

  @Test
  public void applyToForegroundActivityTask_withCurrentActivity_succeedsAndCallsConsumer() {
    // Resume an activity so there is a current foreground activity already when
    // applyToForegroundActivityTask is called
    lifecycleNotifier.onActivityResumed(activity);

    Task<String> task = lifecycleNotifier.applyToForegroundActivityTask(successContinuation);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("result");
  }

  @Test
  public void applyToForegroundActivityTask_onBackgroundThread_succeedsAndCallsConsumer()
      throws InterruptedException, ExecutionException {
    // Resume an activity so there is a current foreground activity already when
    // applyToForegroundActivityTask is called
    lifecycleNotifier.onActivityResumed(activity);

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    Future<Task<String>> future =
        executorService.submit(
            () -> lifecycleNotifier.applyToForegroundActivityTask(successContinuation));
    TestUtils.awaitAsyncOperations(executorService);

    Task<String> task = future.get();
    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("result");
  }

  @Test
  public void applyToForegroundActivityTask_noCurrentActivity_succeedsAndCallsConsumer() {
    Task<String> task = lifecycleNotifier.applyToForegroundActivityTask(successContinuation);

    // Simulate an activity resuming
    lifecycleNotifier.onActivityResumed(activity);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("result");
  }

  @Test
  public void applyToForegroundActivityTask_continuationThrows_fails() throws Exception {
    FirebaseAppDistributionException continuationException =
        new FirebaseAppDistributionException(
            "exception in continuation task", Status.AUTHENTICATION_CANCELED);
    when(successContinuation.then(activity)).thenThrow(continuationException);
    Task<String> task = lifecycleNotifier.applyToForegroundActivityTask(successContinuation);

    // Simulate an activity resuming
    lifecycleNotifier.onActivityResumed(activity);

    assertTaskFailure(task, Status.AUTHENTICATION_CANCELED, "exception in continuation task");
  }

  @Test
  public void applyToForegroundActivityTask_continuationThrowsUnknownException_wrapsException()
      throws Exception {
    RuntimeException continuationException = new RuntimeException("exception in continuation");
    when(successContinuation.then(activity)).thenThrow(continuationException);
    Task<String> task = lifecycleNotifier.applyToForegroundActivityTask(successContinuation);

    // Simulate an activity resuming
    lifecycleNotifier.onActivityResumed(activity);

    assertTaskFailure(task, Status.UNKNOWN, "Unknown", continuationException);
  }

  @Test
  public void applyToForegroundActivityTask_continuationTaskFails_failsWithSameException()
      throws Exception {
    RuntimeException continuationException = new RuntimeException("exception in continuation");
    when(successContinuation.then(activity)).thenReturn(Tasks.forException(continuationException));
    Task<String> task = lifecycleNotifier.applyToForegroundActivityTask(successContinuation);

    // Simulate an activity resuming
    lifecycleNotifier.onActivityResumed(activity);

    assertThat(task.isSuccessful()).isFalse();
    assertThat(task.getException()).isInstanceOf(RuntimeException.class);
    RuntimeException e = (RuntimeException) task.getException();
    assertThat(e).hasMessageThat().isEqualTo("exception in continuation");
  }
}
