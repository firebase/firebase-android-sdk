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

package com.google.firebase.appdistribution.impl;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.appdistribution.impl.TestUtils.awaitTaskFailure;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.annotations.concurrent.UiThread;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.impl.FirebaseAppDistributionLifecycleNotifier.ActivityConsumer;
import com.google.firebase.appdistribution.impl.FirebaseAppDistributionLifecycleNotifier.ActivityFunction;
import com.google.firebase.appdistribution.impl.FirebaseAppDistributionLifecycleNotifier.NullableActivityFunction;
import com.google.firebase.concurrent.TestOnlyExecutors;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseAppDistributionLifecycleNotifierTest {

  static class TestActivity extends Activity {}

  static class OtherTestActivity extends Activity {}

  private final @UiThread Executor uiThreadExecutor = TestOnlyExecutors.ui();

  @Captor private ArgumentCaptor<Activity> activityArgCaptor;
  private TestActivity activity;
  private OtherTestActivity otherActivity;
  private FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    activity = Robolectric.buildActivity(TestActivity.class).create().get();
    otherActivity = Robolectric.buildActivity(OtherTestActivity.class).create().get();
    lifecycleNotifier = new FirebaseAppDistributionLifecycleNotifier(uiThreadExecutor);
  }

  @Test
  public void consumeForegroundActivity_noCurrentActivity_succeedsAndCallsConsumer() {
    ActivityConsumer consumer = mock(ActivityConsumer.class);
    Task<Void> task = lifecycleNotifier.consumeForegroundActivity(consumer);

    // Simulate an activity resuming
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(activity);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isNull();
    verify(consumer).consume(activity);
  }

  @Test
  public void consumeForegroundActivity_withCurrentActivity_succeedsAndCallsConsumer() {
    // Resume an activity so there is a current foreground activity already when
    // consumeForegroundActivity is called
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(activity);

    ActivityConsumer consumer = mock(ActivityConsumer.class);
    Task<Void> task = lifecycleNotifier.consumeForegroundActivity(consumer);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo(null);
    verify(consumer).consume(activity);
  }

  @Test
  public void consumeForegroundActivity_consumerThrows_fails() {
    RuntimeException consumerException = new RuntimeException("exception in consumer");
    ActivityConsumer consumer = mock(ActivityConsumer.class);
    doThrow(consumerException).when(consumer).consume(activity);
    Task<Void> task = lifecycleNotifier.consumeForegroundActivity(consumer);

    // Simulate an activity resuming
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(activity);

    awaitTaskFailure(task, Status.UNKNOWN, "Unknown", consumerException);
  }

  @Test
  public void applyToForegroundActivity_noCurrentActivity_succeedsAndReturnsValue()
      throws FirebaseAppDistributionException {
    ActivityFunction<String> function = mock(ActivityFunction.class);
    when(function.apply(activity)).thenReturn("return-value");
    Task<String> task = lifecycleNotifier.applyToForegroundActivity(function);

    // Simulate an activity resuming
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(activity);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("return-value");
  }

  @Test
  public void applyToForegroundActivity_withCurrentActivity_succeedsAndReturnsValue()
      throws FirebaseAppDistributionException {
    ActivityFunction<String> function = mock(ActivityFunction.class);
    when(function.apply(activity)).thenReturn("return-value");
    // Resume an activity so there is a current foreground activity already when
    // applyToForegroundActivity is called
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(activity);

    Task<String> task = lifecycleNotifier.applyToForegroundActivity(function);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("return-value");
  }

  @Test
  public void applyToForegroundActivity_functionThrows_fails()
      throws FirebaseAppDistributionException {
    RuntimeException functionException = new RuntimeException("exception in function");
    ActivityFunction<String> function = mock(ActivityFunction.class);
    doThrow(functionException).when(function).apply(activity);
    Task<String> task = lifecycleNotifier.applyToForegroundActivity(function);

    // Simulate an activity resuming
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(activity);

    awaitTaskFailure(task, Status.UNKNOWN, "Unknown", functionException);
  }

  @Test
  public void applyToForegroundActivityTask_withCurrentActivity_succeedsAndCallsConsumer()
      throws Exception {
    // Resume an activity so there is a current foreground activity already when
    // applyToForegroundActivityTask is called
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(activity);

    SuccessContinuation<Activity, String> continuation = mock(SuccessContinuation.class);
    when(continuation.then(activity)).thenReturn(Tasks.forResult("result"));
    Task<String> task = lifecycleNotifier.applyToForegroundActivityTask(continuation);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("result");
  }

  @Test
  public void applyToForegroundActivityTask_noCurrentActivity_succeedsAndCallsConsumer()
      throws Exception {
    SuccessContinuation<Activity, String> continuation = mock(SuccessContinuation.class);
    when(continuation.then(activity)).thenReturn(Tasks.forResult("result"));
    Task<String> task = lifecycleNotifier.applyToForegroundActivityTask(continuation);

    // Simulate an activity resuming
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(activity);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("result");
  }

  @Test
  public void applyToForegroundActivityTask_continuationThrows_fails() throws Exception {
    SuccessContinuation<Activity, String> continuation = mock(SuccessContinuation.class);
    FirebaseAppDistributionException continuationException =
        new FirebaseAppDistributionException(
            "exception in continuation task", Status.AUTHENTICATION_CANCELED);
    when(continuation.then(activity)).thenThrow(continuationException);
    Task<String> task = lifecycleNotifier.applyToForegroundActivityTask(continuation);

    // Simulate an activity resuming
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(activity);

    awaitTaskFailure(task, Status.AUTHENTICATION_CANCELED, "exception in continuation task");
  }

  @Test
  public void applyToForegroundActivityTask_continuationThrowsUnknownException_wrapsException()
      throws Exception {
    SuccessContinuation<Activity, String> continuation = mock(SuccessContinuation.class);
    RuntimeException continuationException = new RuntimeException("exception in continuation");
    when(continuation.then(activity)).thenThrow(continuationException);
    Task<String> task = lifecycleNotifier.applyToForegroundActivityTask(continuation);

    // Simulate an activity resuming
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(activity);

    awaitTaskFailure(task, Status.UNKNOWN, "Unknown", continuationException);
  }

  @Test
  public void applyToForegroundActivityTask_continuationTaskFails_failsWithSameException()
      throws Exception {
    SuccessContinuation<Activity, String> continuation = mock(SuccessContinuation.class);
    RuntimeException continuationException = new RuntimeException("exception in continuation");
    when(continuation.then(activity)).thenReturn(Tasks.forException(continuationException));
    Task<String> task = lifecycleNotifier.applyToForegroundActivityTask(continuation);

    // Simulate an activity resuming
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(activity);

    assertThat(task.isSuccessful()).isFalse();
    assertThat(task.getException()).isInstanceOf(RuntimeException.class);
    RuntimeException e = (RuntimeException) task.getException();
    assertThat(e).hasMessageThat().isEqualTo("exception in continuation");
  }

  @Test
  public void applyToNullableForegroundActivity_ignoringOtherActivity_completesWithCurrentActivity()
      throws FirebaseAppDistributionException {
    NullableActivityFunction<String> function = mock(NullableActivityFunction.class);
    when(function.apply(any())).thenReturn("return-value");
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(
        activity); // Current activity is a TestActivity

    Task<String> task =
        lifecycleNotifier.applyToNullableForegroundActivity(OtherTestActivity.class, function);

    verify(function).apply(activityArgCaptor.capture());
    assertThat(activityArgCaptor.getValue()).isEqualTo(activity);
    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("return-value");
  }

  @Test
  public void applyToNullableForegroundActivity_ignoringCurrentActivity_returnsPreviousActivity()
      throws FirebaseAppDistributionException {
    NullableActivityFunction<String> function = mock(NullableActivityFunction.class);
    when(function.apply(any())).thenReturn("return-value");
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(
        activity); // First activity is a TestActivity
    lifecycleNotifier.lifecycleCallbacks.onActivityPaused(activity);
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(
        otherActivity); // Second activity is a OtherTestActivity

    Task<String> task =
        lifecycleNotifier.applyToNullableForegroundActivity(OtherTestActivity.class, function);

    verify(function).apply(activityArgCaptor.capture());
    assertThat(activityArgCaptor.getValue()).isEqualTo(activity);
    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("return-value");
  }

  @Test
  public void applyToNullableForegroundActivity_previousActivityDestroyed_completesWithNull()
      throws FirebaseAppDistributionException {
    NullableActivityFunction<String> function = mock(NullableActivityFunction.class);
    when(function.apply(any())).thenReturn("return-value");
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(
        activity); // First activity is a TestActivity
    lifecycleNotifier.lifecycleCallbacks.onActivityPaused(activity);
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(
        otherActivity); // Second activity is a OtherTestActivity
    lifecycleNotifier.lifecycleCallbacks.onActivityDestroyed(activity);

    Task<String> task =
        lifecycleNotifier.applyToNullableForegroundActivity(OtherTestActivity.class, function);

    verify(function).apply(activityArgCaptor.capture());
    assertThat(activityArgCaptor.getValue()).isNull();
    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("return-value");
  }

  @Test
  public void applyToNullableForegroundActivity_noPreviousActivity_completesWithNull()
      throws FirebaseAppDistributionException {
    NullableActivityFunction<String> function = mock(NullableActivityFunction.class);
    when(function.apply(any())).thenReturn("return-value");
    lifecycleNotifier.lifecycleCallbacks.onActivityResumed(
        activity); // Current activity is a TestActivity

    Task<String> task =
        lifecycleNotifier.applyToNullableForegroundActivity(TestActivity.class, function);

    verify(function).apply(activityArgCaptor.capture());
    assertThat(activityArgCaptor.getValue()).isNull();
    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("return-value");
  }
}
