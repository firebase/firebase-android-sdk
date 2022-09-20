// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig.internal;

import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.TAG;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.REALTIME_REGEX_URL;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import androidx.annotation.GuardedBy;
import com.google.android.gms.common.util.AndroidUtilsLight;
import com.google.android.gms.common.util.Hex;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigRealtimeUpdateStreamException;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public class ConfigRealtimeHttpClient {
  private static final String API_KEY_HEADER = "X-Goog-Api-Key";
  private static final String X_ANDROID_PACKAGE_HEADER = "X-Android-Package";
  private static final String X_ANDROID_CERT_HEADER = "X-Android-Cert";
  private static final String X_GOOGLE_GFE_CAN_RETRY = "X-Google-GFE-Can-Retry";
  private static final String INSTALLATIONS_AUTH_TOKEN_HEADER =
      "X-Goog-Firebase-Installations-Auth";
  private static final String X_ACCEPT_RESPONSE_STREAMING = "X-Accept-Response-Streaming";

  @GuardedBy("this")
  private final Set<ConfigUpdateListener> listeners;

  @GuardedBy("this")
  private HttpURLConnection httpURLConnection;

  @GuardedBy("this")
  private int httpRetriesRemaining;

  @GuardedBy("this")
  private long httpRetrySeconds;

  @GuardedBy("this")
  private boolean isRealtimeDisabled;

  private final int ORIGINAL_RETRIES = 7;

  private final ScheduledExecutorService scheduledExecutorService;
  private final ConfigFetchHandler configFetchHandler;
  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationsApi firebaseInstallations;
  private final Context context;
  private final String namespace;
  private final Random random;

  public ConfigRealtimeHttpClient(
      FirebaseApp firebaseApp,
      FirebaseInstallationsApi firebaseInstallations,
      ConfigFetchHandler configFetchHandler,
      Context context,
      String namespace,
      Set<ConfigUpdateListener> listeners) {

    this.listeners = listeners;
    this.httpURLConnection = null;
    this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    // Retry parameters
    this.random = new Random();
    resetRetryParameters();

    this.firebaseApp = firebaseApp;
    this.configFetchHandler = configFetchHandler;
    this.firebaseInstallations = firebaseInstallations;
    this.context = context;
    this.namespace = namespace;
    this.isRealtimeDisabled = false;
  }

  /**
   * A regular expression for the GMP App Id format. The first group (index 1) is the project
   * number.
   */
  private static final Pattern GMP_APP_ID_PATTERN =
      Pattern.compile("^[^:]+:([0-9]+):(android|ios|web):([0-9a-f]+)");

  private static String extractProjectNumberFromAppId(String gmpAppId) {
    Matcher matcher = GMP_APP_ID_PATTERN.matcher(gmpAppId);
    return matcher.matches() ? matcher.group(1) : null;
  }

  private void getInstallationAuthToken(HttpURLConnection httpURLConnection) {
    Task<InstallationTokenResult> installationAuthTokenTask = firebaseInstallations.getToken(false);
    installationAuthTokenTask.onSuccessTask(
        unusedToken -> {
          httpURLConnection.setRequestProperty(
              INSTALLATIONS_AUTH_TOKEN_HEADER, unusedToken.getToken());
          return Tasks.forResult(null);
        });
  }

  /** Gets the Android package's SHA-1 fingerprint. */
  private String getFingerprintHashForPackage() {
    byte[] hash;

    try {
      hash =
          AndroidUtilsLight.getPackageCertificateHashBytes(
              this.context, this.context.getPackageName());
      if (hash == null) {
        Log.e(TAG, "Could not get fingerprint hash for package: " + this.context.getPackageName());
        return null;
      } else {
        return Hex.bytesToStringUppercase(hash, /* zeroTerminated= */ false);
      }
    } catch (PackageManager.NameNotFoundException e) {
      Log.i(TAG, "No such package: " + this.context.getPackageName());
      return null;
    }
  }

  private void setCommonRequestHeaders(HttpURLConnection httpURLConnection) {
    // Get Installation Token
    getInstallationAuthToken(httpURLConnection);

    // API Key
    httpURLConnection.setRequestProperty(API_KEY_HEADER, this.firebaseApp.getOptions().getApiKey());

    // Headers required for Android API Key Restrictions.
    httpURLConnection.setRequestProperty(X_ANDROID_PACKAGE_HEADER, context.getPackageName());
    httpURLConnection.setRequestProperty(X_ANDROID_CERT_HEADER, getFingerprintHashForPackage());

    // Header to denote request is retryable on the server.
    httpURLConnection.setRequestProperty(X_GOOGLE_GFE_CAN_RETRY, "yes");

    // Header to tell server that client expects stream response
    httpURLConnection.setRequestProperty(X_ACCEPT_RESPONSE_STREAMING, "true");

    // Headers to denote that the request body is a JSONObject.
    httpURLConnection.setRequestProperty("Content-Type", "application/json");
    httpURLConnection.setRequestProperty("Accept", "application/json");
  }

  private JSONObject createRequestBody() {
    Map<String, String> body = new HashMap<>();
    body.put(
        "project", extractProjectNumberFromAppId(this.firebaseApp.getOptions().getApplicationId()));
    body.put("namespace", this.namespace);
    body.put(
        "lastKnownVersionNumber", Long.toString(configFetchHandler.getTemplateVersionNumber()));
    body.put("appId", firebaseApp.getOptions().getApplicationId());
    body.put("sdkVersion", Integer.toString(Build.VERSION.SDK_INT));

    return new JSONObject(body);
  }

  private void setRequestParams(HttpURLConnection httpURLConnection) throws IOException {
    httpURLConnection.setRequestMethod("POST");
    byte[] body = createRequestBody().toString().getBytes("utf-8");
    OutputStream outputStream = new BufferedOutputStream(httpURLConnection.getOutputStream());
    outputStream.write(body);
    outputStream.flush();
    outputStream.close();
  }

  private synchronized void propagateErrors(FirebaseRemoteConfigException exception) {
    for (ConfigUpdateListener listener : listeners) {
      listener.onError(exception);
    }
  }

  private synchronized void enableBackoff() {
    this.isRealtimeDisabled = true;
  }

  private synchronized int getRetryMultiplier() {
    // Return retry multiplier between range of 5 and 2.
    return random.nextInt(3) + 2;
  }

  private synchronized void resetRetryParameters() {
    httpRetrySeconds = random.nextInt(5) + 1;
    httpRetriesRemaining = ORIGINAL_RETRIES;
  }

  private synchronized boolean canMakeHttpStreamConnection() {
    return !listeners.isEmpty() && httpURLConnection == null && !isRealtimeDisabled;
  }

  private String getRealtimeURL(String namespace) {
    return String.format(
        REALTIME_REGEX_URL,
        extractProjectNumberFromAppId(firebaseApp.getOptions().getApplicationId()),
        namespace);
  }

  private URL getUrl() {
    URL realtimeURL = null;
    try {
      realtimeURL = new URL(getRealtimeURL(namespace));
    } catch (MalformedURLException ex) {
      Log.e(TAG, "URL is malformed");
    }

    return realtimeURL;
  }

  @SuppressLint("VisibleForTests")
  public HttpURLConnection createRealtimeConnection() throws IOException {
    URL realtimeUrl = getUrl();
    HttpURLConnection httpURLConnection = (HttpURLConnection) realtimeUrl.openConnection();
    setCommonRequestHeaders(httpURLConnection);
    setRequestParams(httpURLConnection);

    return httpURLConnection;
  }

  // Try to reopen HTTP connection after a random amount of time
  @SuppressLint("VisibleForTests")
  public synchronized void retryHTTPConnection() {
    if (canMakeHttpStreamConnection() && httpRetriesRemaining > 0) {
      if (httpRetriesRemaining < ORIGINAL_RETRIES) {
        httpRetrySeconds *= getRetryMultiplier();
      }
      httpRetriesRemaining--;
      scheduledExecutorService.schedule(
          new Runnable() {
            @Override
            public void run() {
              beginRealtimeHttpStream();
            }
          },
          httpRetrySeconds,
          TimeUnit.SECONDS);
    } else {
      propagateErrors(
          new FirebaseRemoteConfigRealtimeUpdateStreamException(
              "Unable to establish Realtime http stream."));
    }
  }

  synchronized void stopRealtime() {
    closeRealtimeHttpStream();
    scheduledExecutorService.shutdownNow();
  }

  private synchronized ConfigAutoFetch startAutoFetch(HttpURLConnection httpURLConnection) {
    ConfigUpdateListener retryCallback =
        new ConfigUpdateListener() {
          @Override
          public void onEvent() {
            closeRealtimeHttpStream();
            retryHTTPConnection();
          }

          // This method will only be called when a realtimeDisabled message is sent down the
          // stream.
          @Override
          public void onError(Exception error) {
            if (error != null) {
              enableBackoff();
              propagateErrors(
                  new FirebaseRemoteConfigRealtimeUpdateStreamException(
                      "Back off is enabled, stopping Realtime until app restarts."));
            }
          }
        };

    return new ConfigAutoFetch(httpURLConnection, configFetchHandler, listeners, retryCallback);
  }

  private boolean isStatusCodeRetryable(int statusCode) {
    return statusCode == HttpURLConnection.HTTP_CLIENT_TIMEOUT
        || statusCode == HttpURLConnection.HTTP_BAD_GATEWAY
        || statusCode == HttpURLConnection.HTTP_UNAVAILABLE
        || statusCode == HttpURLConnection.HTTP_GATEWAY_TIMEOUT
        || statusCode == HttpURLConnection.HTTP_OK;
  }

  /**
   * Open the realtime connection, begin listening for updates, and auto-fetch when an update is
   * received.
   *
   * <p>If the connection is successful, this method will block on its thread while it reads the
   * chunk-encoded HTTP body. When the connection closes, it attempts to reestablish the stream.
   */
  @SuppressLint("VisibleForTests")
  public synchronized void beginRealtimeHttpStream() {
    if (!canMakeHttpStreamConnection()) {
      return;
    }

    int responseCode = 200;
    try {
      // Create the open the connection.
      httpURLConnection = createRealtimeConnection();
      responseCode = httpURLConnection.getResponseCode();

      if (responseCode == HttpURLConnection.HTTP_OK) {
        // Reset the retries remaining if we opened the connection without an exception.
        resetRetryParameters();

        // Start listening for realtime notifications.
        ConfigAutoFetch configAutoFetch = startAutoFetch(httpURLConnection);
        configAutoFetch.listenForNotifications();
      }
    } catch (IOException e) {
      Log.d(TAG, "Exception connecting to realtime stream. Retrying the connection...");
    } finally {
      closeRealtimeHttpStream();
      if (isStatusCodeRetryable(responseCode)) {
        retryHTTPConnection();
      } else {
        propagateErrors(
            new FirebaseRemoteConfigRealtimeUpdateStreamException(
                "The server returned a status code that is not retryable. Realtime is shutting down."));
      }
    }
  }

  // Pauses Http stream listening
  synchronized void closeRealtimeHttpStream() {
    if (httpURLConnection != null) {
      this.httpURLConnection.disconnect();

      // Explicitly close the input stream due to a bug in the Android okhttp implementation.
      // See github.com/firebase/firebase-android-sdk/pull/808.
      try {
        this.httpURLConnection.getInputStream().close();
      } catch (IOException e) {
      }
      this.httpURLConnection = null;
    }
  }
}
