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
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;

final class TestUtils {
  private TestUtils() {}

  static void assertTaskFailure(Task task, Status status, String messageSubstring) {
    assertThat(task.isSuccessful()).isFalse();
    assertThat(task.getException()).isInstanceOf(FirebaseAppDistributionException.class);
    FirebaseAppDistributionException e = (FirebaseAppDistributionException) task.getException();
    assertThat(e.getErrorCode()).isEqualTo(status);
    assertThat(e).hasMessageThat().contains(messageSubstring);
  }

  static void assertTaskFailure(
      UpdateTask updateTask, Status status, String messageSubstring, Throwable cause) {
    assertTaskFailure(updateTask, status, messageSubstring);
    assertThat(updateTask.getException()).hasCauseThat().isEqualTo(cause);
  }
}
