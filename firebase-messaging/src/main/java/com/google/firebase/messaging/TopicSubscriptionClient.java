// Copyright 2026 Google LLC
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
package com.google.firebase.messaging;

import static com.google.firebase.messaging.Constants.TAG;

import android.net.Uri;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** A client for complying with the FCM topic subscription and unsubscription.
 * @hide
 * */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public class TopicSubscriptionClient {

  static final String ERROR_INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
  static final String ERROR_SERVICE_NOT_AVAILABLE = "SERVICE_NOT_AVAILABLE";

  private static final long RPC_TIMEOUT_SEC = 30;

  private final FirebaseInstallationsApi firebaseInstallationsApi;
  private final FirebaseApp firebaseApp;
  private final FirebaseMessaging firebaseMessaging;

  @VisibleForTesting
  public interface HttpConnectionFactory {
    @NonNull
    HttpURLConnection createConnection(@NonNull URL url) throws IOException;
  }

  private static HttpConnectionFactory connectionFactory =
      url -> (HttpURLConnection) url.openConnection();

  /**
   * This method will be used inside G3 for Hermetic tests.
   */
  @VisibleForTesting
  public static void setConnectionFactoryForTesting(@Nullable HttpConnectionFactory factory) {
    connectionFactory = factory != null ? factory : url -> (HttpURLConnection) url.openConnection();
  }

  TopicSubscriptionClient(
      FirebaseApp firebaseApp,
      FirebaseMessaging firebaseMessaging,
      FirebaseInstallationsApi firebaseInstallationsApi) {
    this.firebaseInstallationsApi = firebaseInstallationsApi;
    this.firebaseApp = firebaseApp;
    this.firebaseMessaging = firebaseMessaging;
  }

  @WorkerThread
  void subscribe(String topic) throws IOException {
    String fisToken = awaitTask(firebaseInstallationsApi.getToken(false)).getToken();
    // During subscribe operation, we are making sure the app instance is registered.
    firebaseMessaging.blockingRegister(false);
    String fid = awaitTask(firebaseInstallationsApi.getId());
    performTopicOperation(topic, fisToken, fid, "subscribe");
  }

  @WorkerThread
  void unsubscribe(String topic) throws IOException {
    String fisToken = awaitTask(firebaseInstallationsApi.getToken(false)).getToken();
    // During subscribe operation, we are making sure the app instance is registered.
    firebaseMessaging.blockingRegister(false);
    String fid = awaitTask(firebaseInstallationsApi.getId());
    performTopicOperation(topic, fisToken, fid, "unsubscribe");
  }

  @WorkerThread
  private void performTopicOperation(String topic, String token, String fid, String operation)
      throws IOException {

    if (token == null || fid == null) {
      throw new IOException("FIS auth token or FIS ID is empty");
    }

    String projectId = firebaseApp.getOptions().getProjectId();
    String apiKey = firebaseApp.getOptions().getApiKey();

    if (projectId == null) {
      throw new IOException("Project ID or API Key is missing");
    }

    URL url =
        new URL(
            "https://fcmregistrations.googleapis.com/v1/projects/"
                + projectId
                + "/registrations/"
                + fid
                + "/topicSubscriptions/"
                + Uri.encode(topic)
                + ":"
                + operation);

    if (isDebugLogEnabled()) {
      Log.d(TAG, "Topic " + operation + " for: " + topic + " with url: " + url);
    }

    HttpURLConnection connection = createConnection(url);
    connection.setRequestMethod("POST");
    connection.setRequestProperty("x-goog-api-key", apiKey);
    connection.setRequestProperty("x-goog-firebase-installations-auth", token);
    connection.setDoOutput(false);

    int responseCode;
    String responseMessage;
    try {
      responseCode = connection.getResponseCode();
      responseMessage = connection.getResponseMessage();
    } catch (IOException e) {
      throw new IOException(ERROR_SERVICE_NOT_AVAILABLE, e);
    } finally {
      closeQuietly(connection);
      connection.disconnect();
    }

    if (responseCode >= 200 && responseCode < 300) {
      // Success
      if (isDebugLogEnabled()) {
        Log.d(TAG, "Topic " + operation + " for: " + topic + " succeeded.");
      }
    } else if (responseCode == 404 || responseCode == 403) {
      if (isDebugLogEnabled()) {
        Log.d(TAG, "Topic " + operation + " failed: " + responseMessage);
      }
      throw new IOException("Topic " + operation + " failed: " + responseMessage);
    } else if (responseCode >= 500) {
      throw new IOException(ERROR_INTERNAL_SERVER_ERROR);
    } else {
      throw new IOException("Topic " + operation + " failed with status: " + responseCode);
    }
  }

  private static void closeQuietly(HttpURLConnection connection) {
    try {
      InputStream inputStream = connection.getInputStream();
      if (inputStream != null) {
        inputStream.close();
      }
    } catch (IOException ignored) {
      // getInputStream() throws IOException for HTTP error status codes (for example, 4xx or 5xx)
    }
    try {
      InputStream errorStream = connection.getErrorStream();
      if (errorStream != null) {
        errorStream.close();
      }
    } catch (IOException ignored) {
      // Ignore exception while closing error stream
    }
  }

  @NonNull
  @VisibleForTesting
  protected HttpURLConnection createConnection(@NonNull URL url) throws IOException {
    return connectionFactory.createConnection(url);
  }

  /** Awaits an RPC task, rethrowing any IOExceptions or RuntimeExceptions. */
  @WorkerThread
  private static <T> T awaitTask(Task<T> task) throws IOException {
    try {
      return Tasks.await(task, RPC_TIMEOUT_SEC, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      // The underlying exception should always be an IOException or RuntimeException, which we
      // rethrow.
      Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException) cause;
      } else if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      // should not happen but for safety
      throw new IOException(e);
    } catch (InterruptedException | TimeoutException e) {
      throw new IOException(ERROR_SERVICE_NOT_AVAILABLE, e);
    }
  }

  static boolean isDebugLogEnabled() {
    // special workaround for Log.isLoggable being flaky in Android M: b/27572147
    return Log.isLoggable(TAG, Log.DEBUG)
        || (Build.VERSION.SDK_INT == Build.VERSION_CODES.M && Log.isLoggable(TAG, Log.DEBUG));
  }
}
