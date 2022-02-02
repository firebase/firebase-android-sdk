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

import static android.os.Looper.getMainLooper;
import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.FirebaseAppDistributionLifecycleNotifier.ActivityConsumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.mockito.stubbing.Answer;

final class TestUtils {
  private TestUtils() {}

  static FirebaseAppDistributionException assertTaskFailure(
      Task task, Status status, String messageSubstring) {
    assertThat(task.isSuccessful()).isFalse();
    assertThat(task.getException()).isInstanceOf(FirebaseAppDistributionException.class);
    FirebaseAppDistributionException e = (FirebaseAppDistributionException) task.getException();
    assertThat(e.getErrorCode()).isEqualTo(status);
    assertThat(e).hasMessageThat().contains(messageSubstring);
    return e;
  }

  static void assertTaskFailure(
      Task task, Status status, String messageSubstring, Throwable cause) {
    assertTaskFailure(task, status, messageSubstring);
    assertThat(task.getException()).hasCauseThat().isEqualTo(cause);
  }

  static void awaitAsyncOperations(ExecutorService executorService) throws InterruptedException {
    // Await anything enqueued to the executor
    executorService.awaitTermination(100, TimeUnit.MILLISECONDS);

    // Idle the main looper, which is also running these tests, so any Task or lifecycle callbacks
    // can be handled. See http://robolectric.org/blog/2019/06/04/paused-looper/ for more info.
    shadowOf(getMainLooper()).idle();
  }

  static Answer<Task<Void>> applyToForegroundActivityAnswer(Activity activity) {
    return invocationOnMock -> {
      ActivityConsumer consumer = (ActivityConsumer) invocationOnMock.getArgument(0);
      if (consumer == null) {
        return Tasks.forException(new IllegalStateException("ActivityConsumer was null"));
      }
      consumer.consume(activity);
      return Tasks.forResult(null);
    };
  }

  static <T> Answer<Task<T>> applyToForegroundActivityTaskAnswer(Activity activity) {
    return invocationOnMock -> {
      SuccessContinuation<Activity, T> continuation =
          (SuccessContinuation<Activity, T>) invocationOnMock.getArgument(0);
      if (continuation == null) {
        return Tasks.forException(new IllegalStateException("Success was null"));
      }
      return continuation.then(activity);
    };
  }
}
