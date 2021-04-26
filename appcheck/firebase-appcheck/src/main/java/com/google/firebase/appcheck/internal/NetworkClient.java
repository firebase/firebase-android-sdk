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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.appcheck.FirebaseAppCheck;
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

  private static final String SAFETY_NET_EXCHANGE_URL_TEMPLATE =
      "https://firebaseappcheck.googleapis.com/v1beta/projects/%s/apps/%s:exchangeSafetyNetToken?key=%s";
  private static final String DEBUG_EXCHANGE_URL_TEMPLATE =
      "https://firebaseappcheck.googleapis.com/v1beta/projects/%s/apps/%s:exchangeDebugToken?key=%s";
  private static final String CONTENT_TYPE = "Content-Type";
  private static final String APPLICATION_JSON = "application/json";
  private static final String UTF_8 = "UTF-8";
  @VisibleForTesting static final String X_FIREBASE_CLIENT = "X-Firebase-Client";
  @VisibleForTesting static final String X_FIREBASE_CLIENT_LOG_TYPE = "X-Firebase-Client-Log-Type";

  private final DefaultFirebaseAppCheck firebaseAppCheck;
  private final String apiKey;
  private final String appId;
  private final String projectId;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({UNKNOWN, SAFETY_NET, DEBUG})
  public @interface AttestationTokenType {}

  public static final int UNKNOWN = 0;
  public static final int SAFETY_NET = 1;
  public static final int DEBUG = 2;

  public NetworkClient(@NonNull FirebaseApp firebaseApp) {
    checkNotNull(firebaseApp);
    this.firebaseAppCheck = (DefaultFirebaseAppCheck) FirebaseAppCheck.getInstance(firebaseApp);
    this.apiKey = firebaseApp.getOptions().getApiKey();
    this.appId = firebaseApp.getOptions().getApplicationId();
    this.projectId = firebaseApp.getOptions().getProjectId();
    if (projectId == null) {
      throw new IllegalArgumentException("FirebaseOptions#getProjectId cannot be null.");
    }
  }

  /**
   * Calls the App Check backend using {@link HttpURLConnection} in order to exchange an attestation
   * token for an {@link AppCheckTokenResponse}.
   */
  @NonNull
  public AppCheckTokenResponse exchangeAttestationForAppCheckToken(
      @NonNull byte[] requestBytes, @AttestationTokenType int tokenType)
      throws FirebaseException, IOException, JSONException {
    URL url = new URL(String.format(getUrlTemplate(tokenType), projectId, appId, apiKey));
    HttpURLConnection urlConnection = createHttpUrlConnection(url);

    try {
      urlConnection.setDoOutput(true);
      urlConnection.setFixedLengthStreamingMode(requestBytes.length);
      urlConnection.setRequestProperty(CONTENT_TYPE, APPLICATION_JSON);
      if (firebaseAppCheck.getUserAgent() != null) {
        urlConnection.setRequestProperty(X_FIREBASE_CLIENT, firebaseAppCheck.getUserAgent());
      }
      if (firebaseAppCheck.getHeartbeatCode() != null) {
        urlConnection.setRequestProperty(
            X_FIREBASE_CLIENT_LOG_TYPE, firebaseAppCheck.getHeartbeatCode());
      }

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
        // TODO: Create a mapping from HTTP error codes to public App Check error codes.
        HttpErrorResponse httpErrorResponse = HttpErrorResponse.fromJsonString(responseBody);
        throw new FirebaseException(
            "Error returned from API. code: "
                + httpErrorResponse.getErrorCode()
                + " body: "
                + httpErrorResponse.getErrorMessage());
      }
      return AppCheckTokenResponse.fromJsonString(responseBody);
    } finally {
      urlConnection.disconnect();
    }
  }

  private static String getUrlTemplate(@AttestationTokenType int tokenType) {
    switch (tokenType) {
      case SAFETY_NET:
        return SAFETY_NET_EXCHANGE_URL_TEMPLATE;
      case DEBUG:
        return DEBUG_EXCHANGE_URL_TEMPLATE;
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
