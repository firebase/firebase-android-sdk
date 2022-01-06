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

import com.google.android.gms.tasks.Task;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FirebaseAppDistributionTest {
  private FirebaseAppDistribution firebaseAppDistribution;

  @Before
  public void setup() {
    firebaseAppDistribution = new FirebaseAppDistribution();
  }

  @Test
  public void updateIfNewReleaseAvailable_returnsACompletedTask() {
    assertThatTaskIsCompletedSuccessfully(firebaseAppDistribution.updateIfNewReleaseAvailable());
  }

  @Test
  public void isTesterSignedIn_returnsFalse() {
    assertThat(firebaseAppDistribution.isTesterSignedIn()).isFalse();
  }

  @Test
  public void signInTester_returnsACompletedTask() {
    assertThatTaskIsCompletedSuccessfully(firebaseAppDistribution.signInTester());
  }

  @Test
  public void signOutTester_doesNotThrow() {
    firebaseAppDistribution.signOutTester();
  }

  @Test
  public void checkForNewRelease_returnsACompletedTask() {
    assertThatTaskIsCompletedSuccessfully(firebaseAppDistribution.checkForNewRelease());
  }

  @Test
  public void updateApp_returnsACompletedTask() {
    assertThatTaskIsCompletedSuccessfully(firebaseAppDistribution.updateApp());
  }

  private void assertThatTaskIsCompletedSuccessfully(Task task) {
    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.isCanceled()).isFalse();
    assertThat(task.getException()).isNull();
    assertThat(task.getResult()).isNull();
  }
}
