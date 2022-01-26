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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import com.google.android.gms.tasks.Task;
import com.google.firebase.appdistribution.FirebaseAppDistributionLifecycleNotifier.ActivityFunction;
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
  public void getForegroundActivity_whenActivityResumes_succeeds() {
    Task<Activity> task = lifecycleNotifier.getForegroundActivity();
    assertThat(task.isComplete()).isFalse();

    // Simulate an activity resuming
    lifecycleNotifier.onActivityResumed(activity);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo(activity);
  }

  @Test
  public void getForegroundActivity_withCurrentActivity_succeeds() {
    // Resume an activity so there is a current foreground activity already when
    // getForegroundActivity is called
    lifecycleNotifier.onActivityResumed(activity);

    Task<Activity> task = lifecycleNotifier.getForegroundActivity();

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo(activity);
  }

  @Test
  public void getForegroundActivity_withConsumer_succeedsAndCallsConsumer()
      throws FirebaseAppDistributionException {
    ActivityFunction consumer = spy(ActivityFunction.class);
    Task<Activity> task = lifecycleNotifier.getForegroundActivity(consumer);

    // Simulate an activity resuming
    lifecycleNotifier.onActivityResumed(activity);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo(activity);
    verify(consumer).apply(activity);
  }

  @Test
  public void getForegroundActivity_withConsumerAndCurrentActivity_succeedsAndCallsConsumer()
      throws FirebaseAppDistributionException {
    // Resume an activity so there is a current foreground activity already when
    // getForegroundActivity is called
    lifecycleNotifier.onActivityResumed(activity);

    ActivityFunction consumer = spy(ActivityFunction.class);
    Task<Activity> task = lifecycleNotifier.getForegroundActivity(consumer);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo(activity);
    verify(consumer).apply(activity);
  }
}
