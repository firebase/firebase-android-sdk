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

import android.content.Context;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.util.AndroidUtilsLight;
import com.google.android.gms.common.util.Hex;
import com.google.auto.value.AutoValue;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

/** Client that makes FIS-authenticated GET and POST requests to the App Distribution Tester API. */
class TesterApiHttpClient {

  /** A respone from an HTTP request. */
  @AutoValue
  abstract static class HttpResponse {
    /** The HTTP status code. */
    abstract int code();

    /**
     * The JSON response body.
     *
     * <p>This will be an empty JSON object if the status code does not indicate success.
     */
    abstract JSONObject body();

    /** Returns {@code true} if the response code indicates a successful request. */
    boolean isSuccess() {
      return isResponseSuccess(code());
    }

    /** Convenience method to create an HTTPResponse. */
    static HttpResponse create(int code, JSONObject body) {
      return new AutoValue_TesterApiHttpClient_HttpResponse(code, body);
    }
  }

  @VisibleForTesting static final String APP_TESTERS_HOST = "firebaseapptesters.googleapis.com";
  private static final String REQUEST_METHOD_GET = "GET";
  private static final String REQUEST_METHOD_POST = "POST";
  private static final String CONTENT_TYPE_HEADER_KEY = "Content-Type";
  private static final String JSON_CONTENT_TYPE = "application/json";
  private static final String CONTENT_ENCODING_HEADER_KEY = "Content-Encoding";
  private static final String GZIP_CONTENT_ENCODING = "gzip";
  private static final String API_KEY_HEADER = "x-goog-api-key";
  private static final String INSTALLATION_AUTH_HEADER = "X-Goog-Firebase-Installations-Auth";
  private static final String X_ANDROID_PACKAGE_HEADER_KEY = "X-Android-Package";
  private static final String X_ANDROID_CERT_HEADER_KEY = "X-Android-Cert";
  // Format of "X-Client-Version": "{ClientId}/{ClientVersion}"
  private static final String X_CLIENT_VERSION_HEADER_KEY = "X-Client-Version";

  private static final String TAG = "TesterApiClient:";
  private static final int DEFAULT_BUFFER_SIZE = 8192;

  private final FirebaseApp firebaseApp;
  private final HttpsUrlConnectionFactory httpsUrlConnectionFactory;

  TesterApiHttpClient(@NonNull FirebaseApp firebaseApp) {
    this(firebaseApp, new HttpsUrlConnectionFactory());
  }

  @VisibleForTesting
  TesterApiHttpClient(
      @NonNull FirebaseApp firebaseApp,
      @NonNull HttpsUrlConnectionFactory httpsUrlConnectionFactory) {
    this.firebaseApp = firebaseApp;
    this.httpsUrlConnectionFactory = httpsUrlConnectionFactory;
  }

  /** Make a GET request to the tester API at the given path using a FIS token for auth. */
  HttpResponse makeGetRequest(String path, String token) throws FirebaseAppDistributionException {
    HttpsURLConnection connection = null;
    try {
      connection = openHttpsUrlConnection(getTesterApiUrl(path), token);
      return readResponse(connection);
    } catch (IOException e) {
      throw new FirebaseAppDistributionException(
          ErrorMessages.NETWORK_ERROR, Status.NETWORK_FAILURE, e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /** Make a POST request to the tester API at the given path using a FIS token for auth. */
  HttpResponse makePostRequest(String path, String token, String requestBody)
      throws FirebaseAppDistributionException {
    HttpsURLConnection connection = null;
    try {
      connection = openHttpsUrlConnection(getTesterApiUrl(path), token);
      connection.setDoOutput(true);
      connection.setRequestMethod(REQUEST_METHOD_POST);
      connection.addRequestProperty(CONTENT_TYPE_HEADER_KEY, JSON_CONTENT_TYPE);
      connection.addRequestProperty(CONTENT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);
      connection.getOutputStream();
      GZIPOutputStream gzipOutputStream = new GZIPOutputStream(connection.getOutputStream());
      try {
        gzipOutputStream.write(requestBody.getBytes("UTF-8"));
      } catch (IOException e) {
        throw new FirebaseAppDistributionException(
            "Error compressing network request body", Status.UNKNOWN, e);
      } finally {
        gzipOutputStream.close();
      }
      return readResponse(connection);
    } catch (IOException e) {
      throw new FirebaseAppDistributionException(
          ErrorMessages.NETWORK_ERROR, Status.NETWORK_FAILURE, e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  private static String getTesterApiUrl(String path) {
    return String.format("https://%s/%s", APP_TESTERS_HOST, path);
  }

  private static HttpResponse readResponse(HttpsURLConnection connection)
      throws IOException, FirebaseAppDistributionException {
    int responseCode = connection.getResponseCode();
    String responseBody = readResponseBody(connection);
    LogWrapper.getInstance().v(String.format("Response (%d): %s", responseCode, responseBody));
    JSONObject jsonBody =
        isResponseSuccess(responseCode) ? parseJson(responseBody) : new JSONObject();
    return HttpResponse.create(responseCode, jsonBody);
  }

  private static JSONObject parseJson(String json) throws FirebaseAppDistributionException {
    try {
      return new JSONObject(json);
    } catch (JSONException e) {
      throw new FirebaseAppDistributionException(
          ErrorMessages.JSON_PARSING_ERROR, Status.UNKNOWN, e);
    }
  }

  private static String readResponseBody(HttpsURLConnection connection) throws IOException {
    boolean isSuccess = isResponseSuccess(connection.getResponseCode());
    try (InputStream inputStream =
        isSuccess ? connection.getInputStream() : connection.getErrorStream()) {
      if (inputStream == null && !isSuccess) {
        // If the server returns a response with an error code and no response body, getErrorStream
        // returns null. We return an empty string to reflect the empty body.
        return "";
      }
      return convertInputStreamToString(new BufferedInputStream(inputStream));
    }
  }

  private static boolean isResponseSuccess(int responseCode) {
    return responseCode >= 200 && responseCode < 300;
  }

  private HttpsURLConnection openHttpsUrlConnection(String url, String authToken)
      throws IOException {
    LogWrapper.getInstance().v("Opening connection to " + url);
    Context context = firebaseApp.getApplicationContext();
    HttpsURLConnection httpsURLConnection;
    httpsURLConnection = httpsUrlConnectionFactory.openConnection(url);
    httpsURLConnection.setRequestMethod(REQUEST_METHOD_GET);
    httpsURLConnection.setRequestProperty(API_KEY_HEADER, firebaseApp.getOptions().getApiKey());
    httpsURLConnection.setRequestProperty(INSTALLATION_AUTH_HEADER, authToken);
    httpsURLConnection.addRequestProperty(X_ANDROID_PACKAGE_HEADER_KEY, context.getPackageName());
    httpsURLConnection.addRequestProperty(
        X_ANDROID_CERT_HEADER_KEY, getFingerprintHashForPackage());
    httpsURLConnection.addRequestProperty(
        X_CLIENT_VERSION_HEADER_KEY, String.format("android-sdk/%s", BuildConfig.VERSION_NAME));
    return httpsURLConnection;
  }

  private static String convertInputStreamToString(InputStream is) throws IOException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
    int length;
    while ((length = is.read(buffer)) != -1) {
      result.write(buffer, 0, length);
    }
    return result.toString();
  }

  /** Gets the Android package's SHA-1 fingerprint. */
  private String getFingerprintHashForPackage() {
    Context context = firebaseApp.getApplicationContext();
    byte[] hash;

    try {
      hash = AndroidUtilsLight.getPackageCertificateHashBytes(context, context.getPackageName());

      if (hash == null) {
        LogWrapper.getInstance()
            .e(
                TAG
                    + "Could not get fingerprint hash for X-Android-Cert header. Package is not signed: "
                    + context.getPackageName());
        return null;
      } else {
        return Hex.bytesToStringUppercase(hash, /* zeroTerminated= */ false);
      }
    } catch (PackageManager.NameNotFoundException e) {
      LogWrapper.getInstance()
          .e(
              TAG
                  + "Could not get fingerprint hash for X-Android-Cert header. No such package: "
                  + context.getPackageName(),
              e);
      return null;
    }
  }
}
