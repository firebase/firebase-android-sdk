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

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import com.google.android.gms.common.util.AndroidUtilsLight;
import com.google.android.gms.common.util.Hex;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

/** Client that makes FIS-authenticated GET and POST requests to the App Distribution Tester API. */
class TesterApiHttpClient {
  private static final String TAG = "TesterApiHttpClient";

  /** Functional interface for a function that writes a request body to an output stream */
  interface RequestBodyWriter {
    void write(OutputStream os) throws IOException;
  }

  private static final String APP_TESTERS_HOST = "firebaseapptesters.googleapis.com";
  private static final String REQUEST_METHOD_GET = "GET";
  private static final String REQUEST_METHOD_POST = "POST";
  private static final String CONTENT_TYPE_HEADER_KEY = "Content-Type";
  private static final String JSON_CONTENT_TYPE = "application/json";
  private static final String API_KEY_HEADER = "x-goog-api-key";
  private static final String INSTALLATION_AUTH_HEADER = "X-Goog-Firebase-Installations-Auth";
  private static final String X_ANDROID_PACKAGE_HEADER_KEY = "X-Android-Package";
  private static final String X_ANDROID_CERT_HEADER_KEY = "X-Android-Cert";
  // Format of "X-Client-Version": "{ClientId}/{ClientVersion}"
  private static final String X_CLIENT_VERSION_HEADER_KEY = "X-Client-Version";
  private static final String X_GOOG_UPLOAD_PROTOCOL_HEADER = "X-Goog-Upload-Protocol";
  private static final String X_GOOG_UPLOAD_PROTOCOL_RAW = "raw";
  private static final String X_GOOG_UPLOAD_FILE_NAME_HEADER = "X-Goog-Upload-File-Name";
  private static final String X_GOOG_UPLOAD_FILE_NAME = "screenshot.png";
  // StandardCharsets.UTF_8 requires API level 19
  private static final String UTF_8 = "UTF-8";

  private static final int DEFAULT_BUFFER_SIZE = 8192;

  private final Context applicationContext;
  private final FirebaseOptions firebaseOptions;
  private final HttpsUrlConnectionFactory httpsUrlConnectionFactory;

  @Inject
  TesterApiHttpClient(
      Context applicationContext,
      FirebaseOptions firebaseOptions,
      HttpsUrlConnectionFactory httpsUrlConnectionFactory) {
    this.applicationContext = applicationContext;
    this.firebaseOptions = firebaseOptions;
    this.httpsUrlConnectionFactory = httpsUrlConnectionFactory;
  }

  /**
   * Make a GET request to the tester API at the given path using a FIS token for auth.
   *
   * @return the response body
   */
  JSONObject makeGetRequest(String tag, String path, String token)
      throws FirebaseAppDistributionException {
    HttpsURLConnection connection = null;
    try {
      connection = openHttpsUrlConnection(getTesterApiUrl(path), token);
      return readResponse(tag, connection);
    } catch (IOException e) {
      throw getException(tag, ErrorMessages.NETWORK_ERROR, Status.NETWORK_FAILURE, e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /**
   * Make a POST request to the tester API at the given path using a FIS token for auth.
   *
   * @return the response body
   */
  JSONObject makeJsonPostRequest(String tag, String path, String token, String requestBody)
      throws FirebaseAppDistributionException {
    return makeJsonPostRequest(tag, path, token, requestBody, new HashMap<>());
  }

  JSONObject makeJsonPostRequest(
      String tag, String path, String token, String requestBody, Map<String, String> extraHeaders)
      throws FirebaseAppDistributionException {
    byte[] bytes;
    try {
      bytes = requestBody.getBytes(UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new FirebaseAppDistributionException(
          "Unsupported encoding: " + UTF_8, Status.UNKNOWN, e);
    }
    return makePostRequest(
        tag,
        path,
        token,
        JSON_CONTENT_TYPE,
        extraHeaders,
        outputStream -> outputStream.write(bytes));
  }

  /**
   * Make an upload request to the tester API at the given path using a FIS token for auth.
   *
   * <p>Uploads the content with gzip encoding.
   *
   * @return the response body
   */
  JSONObject makeUploadRequest(
      String tag, String path, String token, String filename, String contentType, Uri contentUri)
      throws FirebaseAppDistributionException {
    Map<String, String> extraHeaders = new HashMap<>();
    ContentResolver contentResolver = applicationContext.getContentResolver();
    extraHeaders.put(X_GOOG_UPLOAD_PROTOCOL_HEADER, X_GOOG_UPLOAD_PROTOCOL_RAW);
    extraHeaders.put(X_GOOG_UPLOAD_FILE_NAME_HEADER, filename);
    RequestBodyWriter requestBodyWriter =
        outputStream -> {
          try (InputStream inputStream = contentResolver.openInputStream(contentUri)) {
            writeInputStreamToOutputStream(inputStream, outputStream);
          }
        };
    return makePostRequest(tag, path, token, contentType, extraHeaders, requestBodyWriter);
  }

  private JSONObject makePostRequest(
      String tag,
      String path,
      String token,
      String contentType,
      Map<String, String> extraHeaders,
      RequestBodyWriter requestBodyWriter)
      throws FirebaseAppDistributionException {
    HttpsURLConnection connection = null;
    try {
      connection = openHttpsUrlConnection(getTesterApiUrl(path), token);
      connection.setDoOutput(true);
      connection.setRequestMethod(REQUEST_METHOD_POST);
      connection.addRequestProperty(CONTENT_TYPE_HEADER_KEY, contentType);
      for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
        connection.addRequestProperty(e.getKey(), e.getValue());
      }
      try (OutputStream outputStream = connection.getOutputStream()) {
        requestBodyWriter.write(outputStream);
      } catch (IOException e) {
        throw getException(tag, "Error writing network request body", Status.UNKNOWN, e);
      }
      return readResponse(tag, connection);
    } catch (IOException e) {
      throw getException(tag, ErrorMessages.NETWORK_ERROR, Status.NETWORK_FAILURE, e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  private static String getTesterApiUrl(String path) {
    return String.format("https://%s/%s", APP_TESTERS_HOST, path);
  }

  private static JSONObject readResponse(String tag, HttpsURLConnection connection)
      throws IOException, FirebaseAppDistributionException {
    int responseCode = connection.getResponseCode();
    String responseBody = readResponseBody(connection);
    LogWrapper.v(tag, String.format("Response (%d): %s", responseCode, responseBody));
    if (!isResponseSuccess(responseCode)) {
      throw getExceptionForHttpResponse(tag, responseCode, responseBody);
    }
    return parseJson(tag, responseBody);
  }

  private static JSONObject parseJson(String tag, String json)
      throws FirebaseAppDistributionException {
    try {
      return new JSONObject(json);
    } catch (JSONException e) {
      throw getException(tag, ErrorMessages.JSON_PARSING_ERROR, Status.UNKNOWN, e);
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
      try (ByteArrayOutputStream result = new ByteArrayOutputStream()) {
        writeInputStreamToOutputStream(inputStream, result);
        return result.toString();
      }
    }
  }

  private static boolean isResponseSuccess(int responseCode) {
    return responseCode >= 200 && responseCode < 300;
  }

  private HttpsURLConnection openHttpsUrlConnection(String url, String authToken)
      throws IOException {
    LogWrapper.v(TAG, "Opening connection to " + url);
    HttpsURLConnection httpsURLConnection;
    httpsURLConnection = httpsUrlConnectionFactory.openConnection(url);
    httpsURLConnection.setRequestMethod(REQUEST_METHOD_GET);
    httpsURLConnection.setRequestProperty(API_KEY_HEADER, firebaseOptions.getApiKey());
    httpsURLConnection.setRequestProperty(INSTALLATION_AUTH_HEADER, authToken);
    httpsURLConnection.addRequestProperty(
        X_ANDROID_PACKAGE_HEADER_KEY, applicationContext.getPackageName());
    httpsURLConnection.addRequestProperty(
        X_ANDROID_CERT_HEADER_KEY, getFingerprintHashForPackage());
    httpsURLConnection.addRequestProperty(
        X_CLIENT_VERSION_HEADER_KEY, String.format("android-sdk/%s", BuildConfig.VERSION_NAME));
    return httpsURLConnection;
  }

  private static FirebaseAppDistributionException getExceptionForHttpResponse(
      String tag, int responseCode, String responseBody) {
    switch (responseCode) {
      case 400:
        return getException(tag, "Bad request", Status.UNKNOWN);
      case 401:
        return getException(tag, ErrorMessages.AUTHENTICATION_ERROR, Status.AUTHENTICATION_FAILURE);
      case 403:
        return getExceptionFor403(tag, responseBody);
      case 404:
        return getException(tag, ErrorMessages.NOT_FOUND_ERROR, Status.AUTHENTICATION_FAILURE);
      case 408:
      case 504:
        return getException(tag, ErrorMessages.TIMEOUT_ERROR, Status.NETWORK_FAILURE);
      default:
        return getException(tag, "Received error status: " + responseCode, Status.UNKNOWN);
    }
  }

  private static FirebaseAppDistributionException getExceptionFor403(
      String tag, String responseBody) {
    // Check if this is an API disabled error
    TesterApiDisabledErrorDetails apiDisabledErrorDetails =
        TesterApiDisabledErrorDetails.tryParse(responseBody);
    if (apiDisabledErrorDetails != null) {
      String messageWithHelpLinks =
          String.format(
              "%s\n\n%s", ErrorMessages.API_DISABLED, apiDisabledErrorDetails.formatLinks());
      return getException(tag, messageWithHelpLinks, Status.API_DISABLED);
    }

    // Otherwise return a basic 403 exception
    return getException(tag, ErrorMessages.AUTHORIZATION_ERROR, Status.AUTHENTICATION_FAILURE);
  }

  private static FirebaseAppDistributionException getException(
      String tag, String message, Status status) {
    return new FirebaseAppDistributionException(tagMessage(tag, message), status);
  }

  private static FirebaseAppDistributionException getException(
      String tag, String message, Status status, Throwable t) {
    return new FirebaseAppDistributionException(tagMessage(tag, message), status, t);
  }

  private static String tagMessage(String tag, String message) {
    return String.format("%s: %s", tag, message);
  }

  private static void writeInputStreamToOutputStream(InputStream is, OutputStream os)
      throws IOException {
    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
    int length;
    while ((length = is.read(buffer)) != -1) {
      os.write(buffer, 0, length);
    }
  }

  /** Gets the Android package's SHA-1 fingerprint. */
  private String getFingerprintHashForPackage() {
    byte[] hash;

    try {
      hash =
          AndroidUtilsLight.getPackageCertificateHashBytes(
              applicationContext, applicationContext.getPackageName());

      if (hash == null) {
        LogWrapper.e(
            TAG,
            "Could not get fingerprint hash for X-Android-Cert header. Package is not signed: "
                + applicationContext.getPackageName());
        return null;
      } else {
        return Hex.bytesToStringUppercase(hash, /* zeroTerminated= */ false);
      }
    } catch (PackageManager.NameNotFoundException e) {
      LogWrapper.e(
          TAG,
          "Could not get fingerprint hash for X-Android-Cert header. No such package: "
              + applicationContext.getPackageName(),
          e);
      return null;
    }
  }
}
