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

import static android.os.Looper.getMainLooper;
import static androidx.test.InstrumentationRegistry.getContext;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.impl.FirebaseAppDistributionLifecycleNotifier.ActivityConsumer;
import com.google.firebase.appdistribution.impl.FirebaseAppDistributionLifecycleNotifier.ActivityFunction;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.stubbing.Answer;

final class TestUtils {
  private TestUtils() {}

  static void awaitTaskFailure(Task task, Status status, String messageSubstring) {
    assertThrows(FirebaseAppDistributionException.class, () -> awaitTask(task));
    assertTaskFailure(task, status, messageSubstring);
  }

  static void awaitTaskFailure(Task task, Status status, String messageSubstring, Throwable cause) {
    assertThrows(FirebaseAppDistributionException.class, () -> awaitTask(task));
    assertTaskFailure(task, status, messageSubstring, cause);
  }

  static FirebaseAppDistributionException assertTaskFailure(
      Task task, Status status, String messageSubstring) {
    assertThat(task.isComplete()).isTrue();
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

  static <T> T awaitTask(Task<T> task)
      throws FirebaseAppDistributionException, ExecutionException, InterruptedException {
    TestOnCompleteListener<T> onCompleteListener = new TestOnCompleteListener<>();
    task.addOnCompleteListener(Executors.newSingleThreadExecutor(), onCompleteListener);

    // Idle the main looper, which is also running these tests, so any Task or lifecycle callbacks
    // can be handled. See http://robolectric.org/blog/2019/06/04/paused-looper/ for more info.
    shadowOf(getMainLooper()).idle();

    return onCompleteListener.await();
  }

  static void awaitAsyncOperations(ExecutorService executorService) throws InterruptedException {
    // Await anything enqueued to the executor
    executorService.awaitTermination(100, TimeUnit.MILLISECONDS);

    // Idle the main looper, which is also running these tests, so any Task or lifecycle callbacks
    // can be handled. See http://robolectric.org/blog/2019/06/04/paused-looper/ for more info.
    shadowOf(getMainLooper()).idle();
  }

  /**
   * Mock out the given lifecycle notifier's applyToForegroundActivity(),
   * consumeForegroundActivity(), and applyToForegroundActivityTask() methods, to immediately
   * provide the given activity.
   */
  static void mockForegroundActivity(
      FirebaseAppDistributionLifecycleNotifier mockLifeCycleNotifier, Activity activity) {
    when(mockLifeCycleNotifier.applyToForegroundActivity(any()))
        .thenAnswer(applyToForegroundActivityAnswer(activity));
    when(mockLifeCycleNotifier.consumeForegroundActivity(any()))
        .thenAnswer(consumeForegroundActivityAnswer(activity));
    when(mockLifeCycleNotifier.applyToForegroundActivityTask(any()))
        .thenAnswer(applyToForegroundActivityTaskAnswer(activity));
  }

  private static <T> Answer<Task<T>> applyToForegroundActivityAnswer(Activity activity) {
    return invocationOnMock -> {
      ActivityFunction<T> function = (ActivityFunction<T>) invocationOnMock.getArgument(0);
      if (function == null) {
        return Tasks.forException(new IllegalStateException("ActivityFunction was null"));
      }
      return Tasks.forResult(function.apply(activity));
    };
  }

  private static Answer<Task<Void>> consumeForegroundActivityAnswer(Activity activity) {
    return invocationOnMock -> {
      ActivityConsumer consumer = (ActivityConsumer) invocationOnMock.getArgument(0);
      if (consumer == null) {
        return Tasks.forException(new IllegalStateException("ActivityConsumer was null"));
      }
      consumer.consume(activity);
      return Tasks.forResult(null);
    };
  }

  private static <T> Answer<Task<T>> applyToForegroundActivityTaskAnswer(Activity activity) {
    return invocationOnMock -> {
      SuccessContinuation<Activity, T> continuation =
          (SuccessContinuation<Activity, T>) invocationOnMock.getArgument(0);
      if (continuation == null) {
        return Tasks.forException(new IllegalStateException("SuccessContinuation was null"));
      }
      return continuation.then(activity);
    };
  }

  static String readTestFile(String fileName) throws IOException {
    final InputStream jsonInputStream = getContext().getResources().getAssets().open(fileName);
    return streamToString(jsonInputStream);
  }

  static JSONObject readTestJSON(String fileName) throws IOException, JSONException {
    final String testJsonString = readTestFile(fileName);
    final JSONObject testJson = new JSONObject(testJsonString);
    return testJson;
  }

  private static String streamToString(InputStream is) {
    final java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }
}
