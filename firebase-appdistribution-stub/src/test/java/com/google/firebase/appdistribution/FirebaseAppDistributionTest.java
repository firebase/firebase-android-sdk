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
import static org.junit.Assert.assertThrows;

import com.google.android.gms.tasks.RuntimeExecutionException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import junit.framework.AssertionFailedError;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseAppDistributionTest {
  private FirebaseAppDistribution firebaseAppDistribution;

  @Before
  public void setup() {
    firebaseAppDistribution = new FirebaseAppDistribution();
  }

  @Test
  public void updateIfNewReleaseAvailable_failsWithStatusInProduction() {
    assertTaskFailsWithStatusInProduction(firebaseAppDistribution.updateIfNewReleaseAvailable());
  }

  @Test
  public void updateIfNewReleaseAvailable_doesNotUpdateProgress() {
    assertUpdateTaskDoesNotUpdateProgress(firebaseAppDistribution.updateIfNewReleaseAvailable());
  }

  @Test
  public void isTesterSignedIn_returnsFalse() {
    assertThat(firebaseAppDistribution.isTesterSignedIn()).isFalse();
  }

  @Test
  public void signInTester_failsWithStatusInProduction() {
    assertTaskFailsWithStatusInProduction(firebaseAppDistribution.signInTester());
  }

  @Test
  public void signOutTester_doesNotThrow() {
    firebaseAppDistribution.signOutTester();
  }

  @Test
  public void checkForNewRelease_failsWithStatusInProduction() {
    assertTaskFailsWithStatusInProduction(firebaseAppDistribution.checkForNewRelease());
  }

  @Test
  public void updateApp_failsWithStatusInProduction() {
    assertTaskFailsWithStatusInProduction(firebaseAppDistribution.updateApp());
  }

  @Test
  public void updateApp_doesNotUpdateProgress() {
    assertUpdateTaskDoesNotUpdateProgress(firebaseAppDistribution.updateApp());
  }

  private void assertUpdateTaskDoesNotUpdateProgress(UpdateTask task) {
    OnProgressListener onProgressListener =
        (updateProgress) -> {
          throw new AssertionFailedError("UpdateTask not update progress");
        };
    task.addOnProgressListener(onProgressListener);
    TestOnCompleteListener onCompleteListener = new TestOnCompleteListener();
    task.addOnCompleteListener(onCompleteListener);
    assertThrows(FirebaseAppDistributionException.class, () -> onCompleteListener.await());
  }

  private void assertTaskFailsWithStatusInProduction(Task task) {
    TestOnCompleteListener listener = new TestOnCompleteListener();
    task.addOnCompleteListener(listener);
    FirebaseAppDistributionException e =
        assertThrows(FirebaseAppDistributionException.class, () -> listener.await());
    assertThat(e).hasMessageThat().contains("production");
    assertThat(e.getErrorCode()).isEqualTo(Status.APP_RUNNING_IN_PRODUCTION);
    assertThat(e.getRelease()).isNull();
    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isFalse();
    assertThat(task.isCanceled()).isFalse();
    assertThat(task.getException()).isEqualTo(e);
    assertThat(assertThrows(RuntimeExecutionException.class, () -> task.getResult()))
        .hasCauseThat()
        .isEqualTo(e);
  }
}
