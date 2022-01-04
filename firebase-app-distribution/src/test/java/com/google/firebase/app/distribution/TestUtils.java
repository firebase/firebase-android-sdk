package com.google.firebase.app.distribution;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.gms.tasks.Task;
import com.google.firebase.app.distribution.FirebaseAppDistributionException.Status;

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
