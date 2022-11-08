// Copyright 2022 Google LLC
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

package com.google.firebase.appdistribution.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.app.Activity;
import com.google.android.gms.tasks.RuntimeExecutionException;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.UpdateTask;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import junit.framework.AssertionFailedError;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.LEGACY)
public class FirebaseAppDistributionProxyTest {

  private FirebaseAppDistribution firebaseAppDistribution;
  private TestActivity activity;
  private Executor executor = Runnable::run;

  static class TestActivity extends Activity {}

  @Before
  public void setup() {
    firebaseAppDistribution = new FirebaseAppDistributionProxy(() -> null);
    activity = Robolectric.buildActivity(TestActivity.class).create().get();
  }

  @Test
  public void updateIfNewReleaseAvailable() {
    UpdateTask updateTask = firebaseAppDistribution.updateIfNewReleaseAvailable();
    assertTaskFailsWithNotImplemented(updateTask);
    assertUpdateTaskListeners(updateTask);
  }

  @Test
  public void isTesterSignedIn_returnsFalse() {
    assertThat(firebaseAppDistribution.isTesterSignedIn()).isFalse();
  }

  @Test
  public void signInTester_failsWithNotImplemented() {
    assertTaskFailsWithNotImplemented(firebaseAppDistribution.signInTester());
  }

  @Test
  public void signOutTester_doesNotThrow() {
    firebaseAppDistribution.signOutTester();
  }

  @Test
  public void checkForNewRelease_failsWithNotImplemented() {
    assertTaskFailsWithNotImplemented(firebaseAppDistribution.checkForNewRelease());
  }

  @Test
  public void updateApp() {
    UpdateTask updateTask = firebaseAppDistribution.updateApp();
    assertTaskFailsWithNotImplemented(updateTask);
    assertUpdateTaskListeners(updateTask);
    assertTaskContinuations(updateTask);
  }

  private void assertUpdateTaskListeners(UpdateTask updateTask) {
    assertTaskListeners(updateTask);

    updateTask.addOnProgressListener(
        up -> {
          throw new AssertionFailedError("UpdateTask should not update progress");
        });
    updateTask.addOnProgressListener(
        executor,
        up -> {
          throw new AssertionFailedError("UpdateTask should not update progress");
        });
  }

  private void assertTaskListeners(Task<?> task) {
    AtomicBoolean wasCalled = new AtomicBoolean(false);
    task.addOnFailureListener(e -> wasCalled.set(true));
    assertThat(wasCalled.get()).isTrue();

    wasCalled.set(false);
    task.addOnFailureListener(executor, e -> wasCalled.set(true));
    assertThat(wasCalled.get()).isTrue();

    wasCalled.set(false);
    task.addOnFailureListener(activity, e -> wasCalled.set(true));
    assertThat(wasCalled.get()).isTrue();

    task.addOnSuccessListener(
        r -> {
          throw new AssertionFailedError("UpdateTask should not call success listener");
        });
    task.addOnSuccessListener(
        executor,
        r -> {
          throw new AssertionFailedError("UpdateTask should not call success listener");
        });
    task.addOnSuccessListener(
        activity,
        r -> {
          throw new AssertionFailedError("UpdateTask should not call success listener");
        });

    task.addOnCanceledListener(
        () -> {
          throw new AssertionFailedError("UpdateTask should not call canceled listener");
        });
    task.addOnCanceledListener(
        executor,
        () -> {
          throw new AssertionFailedError("UpdateTask should not call canceled listener");
        });
    task.addOnCanceledListener(
        activity,
        () -> {
          throw new AssertionFailedError("UpdateTask should not call canceled listener");
        });
  }

  private void assertTaskContinuations(Task<?> task) {
    // Should not throw exceptions
    task.continueWith(result -> Tasks.forResult(null));
    task.continueWith(result -> null);
    task.continueWith(executor, result -> Tasks.forResult(null));
    task.continueWith(executor, result -> null);

    task.onSuccessTask(result -> Tasks.forResult(null));
    task.onSuccessTask(executor, result -> Tasks.forResult(null));
  }

  private void assertTaskFailsWithNotImplemented(Task task) {
    assertThat(task.isSuccessful()).isFalse();
    assertThat(task.isCanceled()).isFalse();
    Exception e = task.getException();
    assertThat(e).isInstanceOf(FirebaseAppDistributionException.class);
    assertThat(((FirebaseAppDistributionException) e).getErrorCode())
        .isEqualTo(Status.NOT_IMPLEMENTED);
    assertThat(((FirebaseAppDistributionException) e).getRelease()).isNull();
    assertThat(task.isComplete()).isTrue();
    assertThat(assertThrows(RuntimeExecutionException.class, () -> task.getResult()))
        .hasCauseThat()
        .isEqualTo(e);
  }
}
