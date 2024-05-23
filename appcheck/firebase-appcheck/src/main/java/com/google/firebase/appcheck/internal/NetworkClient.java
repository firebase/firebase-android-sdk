// Copyright 2020 Google LLC
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

package com.google.firebase.appcheck.internal;

import static com.google.android.gms.common.internal.Preconditions.checkNotNull;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.util.AndroidUtilsLight;
import com.google.android.gms.common.util.Hex;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.heartbeatinfo.HeartBeatController;
import com.google.firebase.inject.Provider;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONException;

/**
 * Client for HTTP network requests and responses to the App Check backend using {@link
 * HttpURLConnection}.
 */
public class NetworkClient {

  private static final String TAG = NetworkClient.class.getName();

  private static final String DEBUG_EXCHANGE_URL_TEMPLATE =
      "https://firebaseappcheck.googleapis.com/v1/projects/%s/apps/%s:exchangeDebugToken?key=%s";
  private static final String PLAY_INTEGRITY_EXCHANGE_URL_TEMPLATE =
      "https://firebaseappcheck.googleapis.com/v1/projects/%s/apps/%s:exchangePlayIntegrityToken?key=%s";
  private static final String PLAY_INTEGRITY_CHALLENGE_URL_TEMPLATE =
      "https://firebaseappcheck.googleapis.com/v1/projects/%s/apps/%s:generatePlayIntegrityChallenge?key=%s";
  private static final String CONTENT_TYPE = "Content-Type";
  private static final String APPLICATION_JSON = "application/json";
  private static final String UTF_8 = "UTF-8";
  @VisibleForTesting static final String X_FIREBASE_CLIENT = "X-Firebase-Client";
  @VisibleForTesting static final String X_ANDROID_PACKAGE = "X-Android-Package";
  @VisibleForTesting static final String X_ANDROID_CERT = "X-Android-Cert";

  private final Context context;
  private final String apiKey;
  private final String appId;
  private final String projectId;
  private final Provider<HeartBeatController> heartBeatControllerProvider;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({UNKNOWN, DEBUG, PLAY_INTEGRITY})
  public @interface AttestationTokenType {}

  public static final int UNKNOWN = 0;
  public static final int DEBUG = 2;
  public static final int PLAY_INTEGRITY = 3;

  public NetworkClient(@NonNull FirebaseApp firebaseApp) {
    this(
        firebaseApp.getApplicationContext(),
        firebaseApp.getOptions(),
        ((DefaultFirebaseAppCheck) FirebaseAppCheck.getInstance(firebaseApp))
            .getHeartbeatControllerProvider());
  }

  @VisibleForTesting
  NetworkClient(
      @NonNull Context context,
      @NonNull FirebaseOptions firebaseOptions,
      @NonNull Provider<HeartBeatController> heartBeatControllerProvider) {
    checkNotNull(context);
    checkNotNull(firebaseOptions);
    checkNotNull(heartBeatControllerProvider);
    this.context = context;
    this.apiKey = firebaseOptions.getApiKey();
    this.appId = firebaseOptions.getApplicationId();
    this.projectId = firebaseOptions.getProjectId();
    if (projectId == null) {
      throw new IllegalArgumentException("FirebaseOptions#getProjectId cannot be null.");
    }
    this.heartBeatControllerProvider = heartBeatControllerProvider;
  }

  /**
   * Calls the App Check backend using {@link HttpURLConnection} in order to exchange an attestation
   * token for an {@link AppCheckTokenResponse}.
   */
  @NonNull
  public AppCheckTokenResponse exchangeAttestationForAppCheckToken(
      @NonNull byte[] requestBytes,
      @AttestationTokenType int tokenType,
      @NonNull RetryManager retryManager)
      throws FirebaseException, IOException, JSONException {
    if (!retryManager.canRetry()) {
      throw new FirebaseException("Too many attempts.");
    }
    URL url = new URL(String.format(getUrlTemplate(tokenType), projectId, appId, apiKey));
    String response =
        makeNetworkRequest(url, requestBytes, retryManager, /* resetRetryManagerOnSuccess= */ true);
    return AppCheckTokenResponse.fromJsonString(response);
  }

  /**
   * Calls the App Check backend using {@link HttpURLConnection} in order to generate a challenge
   * nonce for the Play Integrity attestation flow.
   */
  @NonNull
  public String generatePlayIntegrityChallenge(
      @NonNull byte[] requestBytes, @NonNull RetryManager retryManager)
      throws FirebaseException, IOException, JSONException {
    if (!retryManager.canRetry()) {
      throw new FirebaseException("Too many attempts.");
    }
    URL url =
        new URL(String.format(PLAY_INTEGRITY_CHALLENGE_URL_TEMPLATE, projectId, appId, apiKey));
    return makeNetworkRequest(
        url, requestBytes, retryManager, /* resetRetryManagerOnSuccess= */ false);
  }

  private String makeNetworkRequest(
      @NonNull URL url,
      @NonNull byte[] requestBytes,
      @NonNull RetryManager retryManager,
      boolean resetRetryManagerOnSuccess)
      throws FirebaseException, IOException, JSONException {
    HttpURLConnection urlConnection = createHttpUrlConnection(url);

    try {
      urlConnection.setDoOutput(true);
      urlConnection.setFixedLengthStreamingMode(requestBytes.length);
      urlConnection.setRequestProperty(CONTENT_TYPE, APPLICATION_JSON);
      String heartBeatHeader = getHeartBeat();
      if (heartBeatHeader != null) {
        urlConnection.setRequestProperty(X_FIREBASE_CLIENT, heartBeatHeader);
      }

      // Headers for Android API key restrictions.
      urlConnection.setRequestProperty(X_ANDROID_PACKAGE, context.getPackageName());
      urlConnection.setRequestProperty(X_ANDROID_CERT, getFingerprintHashForPackage());

      try (OutputStream out =
          new BufferedOutputStream(urlConnection.getOutputStream(), requestBytes.length)) {
        out.write(requestBytes, /* off= */ 0, requestBytes.length);
      }

      int responseCode = urlConnection.getResponseCode();
      InputStream in =
          isResponseSuccess(responseCode)
              ? urlConnection.getInputStream()
              : urlConnection.getErrorStream();
      StringBuilder response = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          response.append(line);
        }
      }
      String responseBody = response.toString();
      if (!isResponseSuccess(responseCode)) {
        retryManager.updateBackoffOnFailure(responseCode);
        // TODO: Create a mapping from HTTP error codes to public App Check error codes.
        HttpErrorResponse httpErrorResponse = HttpErrorResponse.fromJsonString(responseBody);
        throw new FirebaseException(
            "Error returned from API. code: "
                + httpErrorResponse.getErrorCode()
                + " body: "
                + httpErrorResponse.getErrorMessage());
      }
      if (resetRetryManagerOnSuccess) {
        retryManager.resetBackoffOnSuccess();
      }
      return responseBody;
    } finally {
      urlConnection.disconnect();
    }
  }

  @VisibleForTesting
  String getHeartBeat() {
    HeartBeatController controller = heartBeatControllerProvider.get();
    if (controller != null) {
      try {
        return Tasks.await(controller.getHeartBeatsHeader());
      } catch (Exception e) {
        Log.w(TAG, "Unable to get heartbeats!");
        return null;
      }
    } else {
      return null;
    }
  }
  /** Gets the Android package's SHA-1 fingerprint. */
  private String getFingerprintHashForPackage() {
    byte[] hash;

    try {
      hash = AndroidUtilsLight.getPackageCertificateHashBytes(context, context.getPackageName());
      if (hash == null) {
        Log.e(TAG, "Could not get fingerprint hash for package: " + context.getPackageName());
        return null;
      }
      return Hex.bytesToStringUppercase(hash, /* zeroTerminated= */ false);
    } catch (NameNotFoundException e) {
      Log.e(TAG, "No such package: " + context.getPackageName(), e);
      return null;
    }
  }

  private static String getUrlTemplate(@AttestationTokenType int tokenType) {
    switch (tokenType) {
      case DEBUG:
        return DEBUG_EXCHANGE_URL_TEMPLATE;
      case PLAY_INTEGRITY:
        return PLAY_INTEGRITY_EXCHANGE_URL_TEMPLATE;
      default:
        throw new IllegalArgumentException("Unknown token type.");
    }
  }

  @VisibleForTesting
  HttpURLConnection createHttpUrlConnection(URL url) throws IOException {
    return (HttpURLConnection) url.openConnection();
  }

  private static final boolean isResponseSuccess(int responseCode) {
    return responseCode >= 200 && responseCode < 300;
  }
}
