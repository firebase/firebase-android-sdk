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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.FirebaseAppDistributionLifecycleNotifier.ActivityConsumer;
import com.google.firebase.appdistribution.FirebaseAppDistributionLifecycleNotifier.ActivityContinuation;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseAppDistributionLifecycleNotifierTest {
  private TestActivity activity;
  private FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;

  static class TestActivity extends Activity {}

  @Before
  public void setup() {
    activity = Robolectric.buildActivity(TestActivity.class).create().get();
    lifecycleNotifier = new FirebaseAppDistributionLifecycleNotifier();
  }

  @Test
  public void applyToForegroundActivity_noCurrentActivity_succeedsAndCallsConsumer() {
    ActivityConsumer consumer = spy(ActivityConsumer.class);
    Task<Void> task = lifecycleNotifier.applyToForegroundActivity(consumer);

    // Simulate an activity resuming
    lifecycleNotifier.onActivityResumed(activity);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isNull();
    verify(consumer).consume(activity);
  }

  @Test
  public void applyToForegroundActivity_withCurrentActivity_succeedsAndCallsConsumer() {
    // Resume an activity so there is a current foreground activity already when
    // applyToForegroundActivity is called
    lifecycleNotifier.onActivityResumed(activity);

    ActivityConsumer consumer = spy(ActivityConsumer.class);
    Task<Void> task = lifecycleNotifier.applyToForegroundActivity(consumer);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo(null);
    verify(consumer).consume(activity);
  }

  @Test
  public void applyToForegroundActivity_consumerThrows_fails() {
    RuntimeException consumerException = new RuntimeException("exception in consumer");
    ActivityConsumer consumer = spy(ActivityConsumer.class);
    doThrow(consumerException).when(consumer).consume(activity);
    Task<Void> task = lifecycleNotifier.applyToForegroundActivity(consumer);

    // Simulate an activity resuming
    lifecycleNotifier.onActivityResumed(activity);

    assertTaskFailure(task, Status.UNKNOWN, "Unknown", consumerException);
  }

  @Test
  public void applyToForegroundActivityTask_withCurrentActivity_succeedsAndCallsConsumer()
      throws Exception {
    // Resume an activity so there is a current foreground activity already when
    // applyToForegroundActivityTask is called
    lifecycleNotifier.onActivityResumed(activity);

    ActivityContinuation<String> continuation = spy(ActivityContinuation.class);
    when(continuation.then(activity)).thenReturn(Tasks.forResult("result"));
    Task<String> task = lifecycleNotifier.applyToForegroundActivityTask(continuation);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("result");
  }

  @Test
  public void applyToForegroundActivityTask_noCurrentActivity_succeedsAndCallsConsumer()
      throws Exception {
    ActivityContinuation<String> continuation = spy(ActivityContinuation.class);
    when(continuation.then(activity)).thenReturn(Tasks.forResult("result"));
    Task<String> task = lifecycleNotifier.applyToForegroundActivityTask(continuation);

    // Simulate an activity resuming
    lifecycleNotifier.onActivityResumed(activity);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("result");
  }

  @Test
  public void applyToForegroundActivityTask_continuationThrows_fails() throws Exception {
    ActivityContinuation<String> continuation = spy(ActivityContinuation.class);
    FirebaseAppDistributionException continuationException =
        new FirebaseAppDistributionException(
            "exception in continuation task", Status.AUTHENTICATION_CANCELED);
    when(continuation.then(activity)).thenThrow(continuationException);
    Task<String> task = lifecycleNotifier.applyToForegroundActivityTask(continuation);

    // Simulate an activity resuming
    lifecycleNotifier.onActivityResumed(activity);

    assertTaskFailure(task, Status.AUTHENTICATION_CANCELED, "exception in continuation task");
  }

  @Test
  public void applyToForegroundActivityTask_continuationThrowsUnknownException_wrapsException()
      throws Exception {
    ActivityContinuation<String> continuation = spy(ActivityContinuation.class);
    RuntimeException continuationException = new RuntimeException("exception in continuation");
    when(continuation.then(activity)).thenThrow(continuationException);
    Task<String> task = lifecycleNotifier.applyToForegroundActivityTask(continuation);

    // Simulate an activity resuming
    lifecycleNotifier.onActivityResumed(activity);

    assertTaskFailure(task, Status.UNKNOWN, "Unknown", continuationException);
  }

  @Test
  public void applyToForegroundActivityTask_continuationTaskFails_failsWithSameException()
      throws Exception {
    ActivityContinuation<String> continuation = spy(ActivityContinuation.class);
    RuntimeException continuationException = new RuntimeException("exception in continuation");
    when(continuation.then(activity)).thenReturn(Tasks.forException(continuationException));
    Task<String> task = lifecycleNotifier.applyToForegroundActivityTask(continuation);

    // Simulate an activity resuming
    lifecycleNotifier.onActivityResumed(activity);

    assertThat(task.isSuccessful()).isFalse();
    assertThat(task.getException()).isInstanceOf(RuntimeException.class);
    RuntimeException e = (RuntimeException) task.getException();
    assertThat(e).hasMessageThat().isEqualTo("exception in continuation");
  }
}
