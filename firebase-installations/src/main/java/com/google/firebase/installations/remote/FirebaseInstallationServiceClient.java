// Copyright 2019 Google LLC
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

package com.google.firebase.installations.remote;

import static android.content.ContentValues.TAG;
import static com.google.android.gms.common.internal.Preconditions.checkArgument;
import static com.google.firebase.installations.BuildConfig.VERSION_NAME;

import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.util.AndroidUtilsLight;
import com.google.android.gms.common.util.Hex;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.heartbeatinfo.HeartBeatInfo.HeartBeat;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsException;
import com.google.firebase.installations.FirebaseInstallationsException.Status;
import com.google.firebase.installations.remote.InstallationResponse.ResponseCode;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Http client that sends request to Firebase Installations backend API.
 *
 * @hide
 */
public class FirebaseInstallationServiceClient {
  private static final String FIREBASE_INSTALLATIONS_API_DOMAIN =
      "firebaseinstallations.googleapis.com";
  private static final String CREATE_REQUEST_RESOURCE_NAME_FORMAT = "projects/%s/installations";
  private static final String GENERATE_AUTH_TOKEN_REQUEST_RESOURCE_NAME_FORMAT =
      "projects/%s/installations/%s/authTokens:generate";
  private static final String DELETE_REQUEST_RESOURCE_NAME_FORMAT = "projects/%s/installations/%s";
  private static final String FIREBASE_INSTALLATIONS_API_VERSION = "v1";
  private static final String FIREBASE_INSTALLATION_AUTH_VERSION = "FIS_v2";

  private static final String CONTENT_TYPE_HEADER_KEY = "Content-Type";
  private static final String ACCEPT_HEADER_KEY = "Accept";
  private static final String JSON_CONTENT_TYPE = "application/json";
  private static final String CONTENT_ENCODING_HEADER_KEY = "Content-Encoding";
  private static final String GZIP_CONTENT_ENCODING = "gzip";

  private static final String CACHE_CONTROL_HEADER_KEY = "Cache-Control";
  private static final String CACHE_CONTROL_DIRECTIVE = "no-cache";

  /** Heartbeat tag for firebase installations. */
  private static final String FIREBASE_INSTALLATIONS_ID_HEARTBEAT_TAG = "fire-installations-id";

  private static final String HEART_BEAT_HEADER = "x-firebase-client-log-type";
  private static final String USER_AGENT_HEADER = "x-firebase-client";

  private static final String X_ANDROID_PACKAGE_HEADER_KEY = "X-Android-Package";
  private static final String X_ANDROID_CERT_HEADER_KEY = "X-Android-Cert";

  private static final String X_ANDROID_IID_MIGRATION_KEY = "x-goog-fis-android-iid-migration-auth";

  private static final String API_KEY_HEADER = "x-goog-api-key";

  private static final int NETWORK_TIMEOUT_MILLIS = 10000;

  private static final Pattern EXPIRATION_TIMESTAMP_PATTERN = Pattern.compile("[0-9]+s");

  private static final int MAX_RETRIES = 1;
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private static final String SDK_VERSION_PREFIX = "a:";

  private static final String FIS_TAG = "Firebase-Installations";
  private boolean shouldServerErrorRetry;

  @VisibleForTesting
  static final String PARSING_EXPIRATION_TIME_ERROR_MESSAGE = "Invalid Expiration Timestamp.";

  private final Context context;
  private final Provider<UserAgentPublisher> userAgentPublisher;
  private final Provider<HeartBeatInfo> heartbeatInfo;
  private final RequestLimiter requestLimiter;

  public FirebaseInstallationServiceClient(
      @NonNull Context context,
      @NonNull Provider<UserAgentPublisher> publisher,
      @NonNull Provider<HeartBeatInfo> heartbeatInfo) {
    this.context = context;
    this.userAgentPublisher = publisher;
    this.heartbeatInfo = heartbeatInfo;
    this.requestLimiter = new RequestLimiter();
  }

  /**
   * Creates a FID on the FIS Servers by calling FirebaseInstallations API create method.
   *
   * @param apiKey API Key that has access to FIS APIs
   * @param fid Firebase Installation Identifier
   * @param projectID Project Id
   * @param appId the identifier of a Firebase application
   * @param iidToken the identifier token of a Firebase application with instance id. It is set to
   *     null for a FID.
   * @return {@link InstallationResponse} generated from the response body
   *     <ul>
   *       <li>400: return response with status BAD_CONFIG
   *       <li>403: return response with status BAD_CONFIG
   *       <li>403: return response with status BAD_CONFIG
   *       <li>429: throw FirebaseInstallationsException
   *       <li>500: throw FirebaseInstallationsException
   *     </ul>
   */
  @NonNull
  public InstallationResponse createFirebaseInstallation(
      @NonNull String apiKey,
      @Nullable String fid,
      @NonNull String projectID,
      @NonNull String appId,
      @Nullable String iidToken)
      throws FirebaseInstallationsException {
    if (!requestLimiter.isRequestAllowed()) {
      throw new FirebaseInstallationsException(
          "Firebase Installations Service is unavailable. Please try again later.",
          Status.UNAVAILABLE);
    }

    String resourceName = String.format(CREATE_REQUEST_RESOURCE_NAME_FORMAT, projectID);
    URL url = getFullyQualifiedRequestUri(resourceName);
    for (int retryCount = 0; retryCount <= MAX_RETRIES; retryCount++) {

      HttpURLConnection httpURLConnection = openHttpURLConnection(url, apiKey);

      try {
        httpURLConnection.setRequestMethod("POST");
        httpURLConnection.setDoOutput(true);

        // Note: Set the iid token header for authenticating the Instance-ID migrating to FIS.
        if (iidToken != null) {
          httpURLConnection.addRequestProperty(X_ANDROID_IID_MIGRATION_KEY, iidToken);
        }

        writeFIDCreateRequestBodyToOutputStream(httpURLConnection, fid, appId);

        int httpResponseCode = httpURLConnection.getResponseCode();
        requestLimiter.setNextRequestTime(httpResponseCode);

        if (isSuccessfulResponseCode(httpResponseCode)) {
          return readCreateResponse(httpURLConnection);
        }

        logFisCommunicationError(httpURLConnection, appId, apiKey, projectID);

        if (httpResponseCode == 429) {
          throw new FirebaseInstallationsException(
              "Firebase servers have received too many requests from this client in a short "
                  + "period of time. Please try again later.",
              Status.TOO_MANY_REQUESTS);
        }

        if (httpResponseCode >= 500 && httpResponseCode < 600) {
          continue;
        }

        logBadConfigError();

        // Return empty installation response with BAD_CONFIG response code after max retries
        return InstallationResponse.builder().setResponseCode(ResponseCode.BAD_CONFIG).build();
      } catch (AssertionError | IOException ignored) {
        continue;
      } finally {
        httpURLConnection.disconnect();
      }
    }

    throw new FirebaseInstallationsException(
        "Firebase Installations Service is unavailable. Please try again later.",
        Status.UNAVAILABLE);
  }

  private void writeFIDCreateRequestBodyToOutputStream(
      HttpURLConnection httpURLConnection, @Nullable String fid, @NonNull String appId)
      throws IOException {
    writeRequestBodyToOutputStream(
        httpURLConnection, getJsonBytes(buildCreateFirebaseInstallationRequestBody(fid, appId)));
  }

  /**
   * Encodes {@link JSONObject} String representation into a sequence of bytes using the UTF-8
   * charset.
   *
   * @return The resulting byte array.
   * @throws java.io.UnsupportedEncodingException A type of {@link IOException}, if the named
   *     charset is not supported.
   */
  private static byte[] getJsonBytes(JSONObject jsonObject) throws IOException {
    return jsonObject.toString().getBytes("UTF-8");
  }

  private static void writeRequestBodyToOutputStream(URLConnection urlConnection, byte[] jsonBytes)
      throws IOException {
    OutputStream outputStream = urlConnection.getOutputStream();
    if (outputStream == null) {
      throw new IOException("Cannot send request to FIS servers. No OutputStream available.");
    }
    GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
    try {
      gzipOutputStream.write(jsonBytes);
    } finally {
      try {
        gzipOutputStream.close();
        outputStream.close();
      } catch (IOException ignored) {

      }
    }
  }

  /**
   * Builds a {@link JSONObject} with the required name & value to send as a request body for FID
   * creation.
   *
   * @return A {@link JSONObject} with name & value required for FID creation.
   * @throws IllegalStateException If {@link JSONException} is thrown due to non-null names while
   *     {@link JSONObject} creation.
   */
  private static JSONObject buildCreateFirebaseInstallationRequestBody(
      @Nullable String fid, @NonNull String appId) {
    try {
      JSONObject firebaseInstallationData = new JSONObject();
      firebaseInstallationData.put("fid", fid);
      firebaseInstallationData.put("appId", appId);
      firebaseInstallationData.put("authVersion", FIREBASE_INSTALLATION_AUTH_VERSION);
      firebaseInstallationData.put("sdkVersion", SDK_VERSION_PREFIX + VERSION_NAME);
      return firebaseInstallationData;
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  private void writeGenerateAuthTokenRequestBodyToOutputStream(HttpURLConnection httpURLConnection)
      throws IOException {
    writeRequestBodyToOutputStream(
        httpURLConnection, getJsonBytes(buildGenerateAuthTokenRequestBody()));
  }

  /**
   * Builds a {@link JSONObject} with the required name & value to send as a request body for
   * generating auth token.
   *
   * @return A {@link JSONObject} with name & value required for generating auth token.
   * @throws IllegalStateException If {@link JSONException} is thrown due to non-null names while
   *     {@link JSONObject} creation.
   */
  private static JSONObject buildGenerateAuthTokenRequestBody() {
    try {
      JSONObject sdkVersionData = new JSONObject();
      sdkVersionData.put("sdkVersion", SDK_VERSION_PREFIX + VERSION_NAME);

      JSONObject firebaseInstallationData = new JSONObject();
      firebaseInstallationData.put("installation", sdkVersionData);
      return firebaseInstallationData;
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Deletes a FID on the FIS Servers by calling FirebaseInstallations API delete method.
   *
   * @param apiKey API Key that has access to FIS APIs
   * @param fid Firebase Installation Identifier
   * @param projectID Project Id
   * @param refreshToken a token used to authenticate FIS requests
   */
  @NonNull
  public void deleteFirebaseInstallation(
      @NonNull String apiKey,
      @NonNull String fid,
      @NonNull String projectID,
      @NonNull String refreshToken)
      throws FirebaseInstallationsException {
    String resourceName = String.format(DELETE_REQUEST_RESOURCE_NAME_FORMAT, projectID, fid);
    URL url = getFullyQualifiedRequestUri(resourceName);

    int retryCount = 0;
    while (retryCount <= MAX_RETRIES) {
      HttpURLConnection httpURLConnection = openHttpURLConnection(url, apiKey);
      try {
        httpURLConnection.setRequestMethod("DELETE");
        httpURLConnection.addRequestProperty("Authorization", "FIS_v2 " + refreshToken);

        int httpResponseCode = httpURLConnection.getResponseCode();

        if (httpResponseCode == 200 || httpResponseCode == 401 || httpResponseCode == 404) {
          return;
        }

        logFisCommunicationError(httpURLConnection, null, apiKey, projectID);

        if (httpResponseCode == 429 || (httpResponseCode >= 500 && httpResponseCode < 600)) {
          retryCount++;
          continue;
        }

        logBadConfigError();

        throw new FirebaseInstallationsException(
            "Bad config while trying to delete FID", Status.BAD_CONFIG);
      } catch (IOException ignored) {
        retryCount++;
      } finally {
        httpURLConnection.disconnect();
      }
    }

    throw new FirebaseInstallationsException(
        "Firebase Installations Service is unavailable. Please try again later.",
        Status.UNAVAILABLE);
  }

  private URL getFullyQualifiedRequestUri(String resourceName)
      throws FirebaseInstallationsException {
    try {
      return new URL(
          String.format(
              "https://%s/%s/%s",
              FIREBASE_INSTALLATIONS_API_DOMAIN, FIREBASE_INSTALLATIONS_API_VERSION, resourceName));
    } catch (MalformedURLException e) {
      throw new FirebaseInstallationsException(e.getMessage(), Status.UNAVAILABLE);
    }
  }

  /**
   * Generates a new auth token for a FID on the FIS Servers by calling FirebaseInstallations API
   * generateAuthToken method.
   *
   * @param apiKey API Key that has access to FIS APIs
   * @param fid Firebase Installation Identifier
   * @param projectID Project Id
   * @param refreshToken a token used to authenticate FIS requests
   *     <ul>
   *       <li>400: return response with status BAD_CONFIG
   *       <li>401: return response with status INVALID_AUTH
   *       <li>403: return response with status BAD_CONFIG
   *       <li>404: return response with status INVALID_AUTH
   *       <li>429: throw FirebaseInstallationsException
   *       <li>500: throw FirebaseInstallationsException
   *     </ul>
   */
  @NonNull
  public TokenResult generateAuthToken(
      @NonNull String apiKey,
      @NonNull String fid,
      @NonNull String projectID,
      @NonNull String refreshToken)
      throws FirebaseInstallationsException {
    if (!requestLimiter.isRequestAllowed()) {
      throw new FirebaseInstallationsException(
          "Firebase Installations Service is unavailable. Please try again later.",
          Status.UNAVAILABLE);
    }

    String resourceName =
        String.format(GENERATE_AUTH_TOKEN_REQUEST_RESOURCE_NAME_FORMAT, projectID, fid);
    URL url = getFullyQualifiedRequestUri(resourceName);
    for (int retryCount = 0; retryCount <= MAX_RETRIES; retryCount++) {

      HttpURLConnection httpURLConnection = openHttpURLConnection(url, apiKey);
      try {
        httpURLConnection.setRequestMethod("POST");
        httpURLConnection.addRequestProperty("Authorization", "FIS_v2 " + refreshToken);
        httpURLConnection.setDoOutput(true);

        writeGenerateAuthTokenRequestBodyToOutputStream(httpURLConnection);

        int httpResponseCode = httpURLConnection.getResponseCode();
        requestLimiter.setNextRequestTime(httpResponseCode);

        if (isSuccessfulResponseCode(httpResponseCode)) {
          return readGenerateAuthTokenResponse(httpURLConnection);
        }

        logFisCommunicationError(httpURLConnection, null, apiKey, projectID);

        if (httpResponseCode == 401 || httpResponseCode == 404) {
          return TokenResult.builder().setResponseCode(TokenResult.ResponseCode.AUTH_ERROR).build();
        }

        if (httpResponseCode == 429) {
          throw new FirebaseInstallationsException(
              "Firebase servers have received too many requests from this client in a short "
                  + "period of time. Please try again later.",
              Status.TOO_MANY_REQUESTS);
        }

        if (httpResponseCode >= 500 && httpResponseCode < 600) {
          continue;
        }

        logBadConfigError();

        return TokenResult.builder().setResponseCode(TokenResult.ResponseCode.BAD_CONFIG).build();
        // TODO(b/166168291): Remove code duplication and clean up this class.
      } catch (AssertionError | IOException ignored) {
        continue;
      } finally {
        httpURLConnection.disconnect();
      }
    }
    throw new FirebaseInstallationsException(
        "Firebase Installations Service is unavailable. Please try again later.",
        Status.UNAVAILABLE);
  }

  private static boolean isSuccessfulResponseCode(int responseCode) {
    return responseCode >= 200 && responseCode < 300;
  }

  private static void logBadConfigError() {
    Log.e(
        FIS_TAG,
        "Firebase Installations can not communicate with Firebase server APIs due "
            + "to invalid configuration. Please update your Firebase initialization process"
            + " and set valid Firebase options (API key, Project ID, Application ID) when"
            + " initializing Firebase.");
  }

  private HttpURLConnection openHttpURLConnection(URL url, String apiKey)
      throws FirebaseInstallationsException {
    HttpURLConnection httpURLConnection;
    try {
      httpURLConnection = (HttpURLConnection) url.openConnection();
    } catch (IOException ignored) {
      throw new FirebaseInstallationsException(
          "Firebase Installations Service is unavailable. Please try again later.",
          Status.UNAVAILABLE);
    }
    httpURLConnection.setConnectTimeout(NETWORK_TIMEOUT_MILLIS);
    httpURLConnection.setUseCaches(false);
    httpURLConnection.setReadTimeout(NETWORK_TIMEOUT_MILLIS);
    httpURLConnection.addRequestProperty(CONTENT_TYPE_HEADER_KEY, JSON_CONTENT_TYPE);
    httpURLConnection.addRequestProperty(ACCEPT_HEADER_KEY, JSON_CONTENT_TYPE);
    httpURLConnection.addRequestProperty(CONTENT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);
    httpURLConnection.addRequestProperty(CACHE_CONTROL_HEADER_KEY, CACHE_CONTROL_DIRECTIVE);
    httpURLConnection.addRequestProperty(X_ANDROID_PACKAGE_HEADER_KEY, context.getPackageName());
    if ((heartbeatInfo.get() != null) && (userAgentPublisher.get() != null)) {
      HeartBeat heartbeat =
          heartbeatInfo.get().getHeartBeatCode(FIREBASE_INSTALLATIONS_ID_HEARTBEAT_TAG);
      if (heartbeat != HeartBeat.NONE) {
        httpURLConnection.addRequestProperty(
            USER_AGENT_HEADER, userAgentPublisher.get().getUserAgent());
        httpURLConnection.addRequestProperty(
            HEART_BEAT_HEADER, Integer.toString(heartbeat.getCode()));
      }
    }
    httpURLConnection.addRequestProperty(X_ANDROID_CERT_HEADER_KEY, getFingerprintHashForPackage());
    httpURLConnection.addRequestProperty(API_KEY_HEADER, apiKey);
    return httpURLConnection;
  }

  // Read the response from the createFirebaseInstallation API.
  private InstallationResponse readCreateResponse(HttpURLConnection conn)
      throws AssertionError, IOException {
    InputStream inputStream = conn.getInputStream();
    JsonReader reader = new JsonReader(new InputStreamReader(inputStream, UTF_8));
    TokenResult.Builder tokenResult = TokenResult.builder();
    InstallationResponse.Builder builder = InstallationResponse.builder();
    // JsonReader.peek will sometimes throw AssertionErrors in Android 8.0 and above. See
    // https://b.corp.google.com/issues/79920590 for details.
    reader.beginObject();
    while (reader.hasNext()) {
      String name = reader.nextName();
      if (name.equals("name")) {
        builder.setUri(reader.nextString());
      } else if (name.equals("fid")) {
        builder.setFid(reader.nextString());
      } else if (name.equals("refreshToken")) {
        builder.setRefreshToken(reader.nextString());
      } else if (name.equals("authToken")) {
        reader.beginObject();
        while (reader.hasNext()) {
          String key = reader.nextName();
          if (key.equals("token")) {
            tokenResult.setToken(reader.nextString());
          } else if (key.equals("expiresIn")) {
            tokenResult.setTokenExpirationTimestamp(
                parseTokenExpirationTimestamp(reader.nextString()));
          } else {
            reader.skipValue();
          }
        }
        builder.setAuthToken(tokenResult.build());
        reader.endObject();
      } else {
        reader.skipValue();
      }
    }
    reader.endObject();
    reader.close();
    inputStream.close();
    return builder.setResponseCode(ResponseCode.OK).build();
  }

  // Read the response from the generateAuthToken FirebaseInstallation API.
  private TokenResult readGenerateAuthTokenResponse(HttpURLConnection conn)
      throws AssertionError, IOException {
    InputStream inputStream = conn.getInputStream();
    JsonReader reader = new JsonReader(new InputStreamReader(inputStream, UTF_8));
    TokenResult.Builder builder = TokenResult.builder();
    // JsonReader.peek will sometimes throw AssertionErrors in Android 8.0 and above. See
    // https://b.corp.google.com/issues/79920590 for details.
    reader.beginObject();
    while (reader.hasNext()) {
      String name = reader.nextName();
      if (name.equals("token")) {
        builder.setToken(reader.nextString());
      } else if (name.equals("expiresIn")) {
        builder.setTokenExpirationTimestamp(parseTokenExpirationTimestamp(reader.nextString()));
      } else {
        reader.skipValue();
      }
    }
    reader.endObject();
    reader.close();
    inputStream.close();

    return builder.setResponseCode(TokenResult.ResponseCode.OK).build();
  }

  /** Gets the Android package's SHA-1 fingerprint. */
  private String getFingerprintHashForPackage() {
    byte[] hash;

    try {
      hash = AndroidUtilsLight.getPackageCertificateHashBytes(context, context.getPackageName());

      if (hash == null) {
        Log.e(TAG, "Could not get fingerprint hash for package: " + context.getPackageName());
        return null;
      } else {
        return Hex.bytesToStringUppercase(hash, /* zeroTerminated= */ false);
      }
    } catch (PackageManager.NameNotFoundException e) {
      Log.e(TAG, "No such package: " + context.getPackageName(), e);
      return null;
    }
  }

  /**
   * Returns parsed token expiration timestamp in seconds.
   *
   * @param expiresIn is expiration timestamp in String format: 604800s
   */
  @VisibleForTesting
  static long parseTokenExpirationTimestamp(String expiresIn) {
    checkArgument(
        EXPIRATION_TIMESTAMP_PATTERN.matcher(expiresIn).matches(),
        PARSING_EXPIRATION_TIME_ERROR_MESSAGE);
    return (expiresIn == null || expiresIn.length() == 0)
        ? 0L
        : Long.parseLong(expiresIn.substring(0, expiresIn.length() - 1));
  }

  private static void logFisCommunicationError(
      HttpURLConnection conn,
      @Nullable String appId,
      @NonNull String apiKey,
      @NonNull String projectId) {
    String logString = readErrorResponse(conn);
    if (!TextUtils.isEmpty(logString)) {
      Log.w(FIS_TAG, logString);
      Log.w(FIS_TAG, availableFirebaseOptions(appId, apiKey, projectId));
    }
  }

  private static String availableFirebaseOptions(
      @Nullable String appId, @NonNull String apiKey, @NonNull String projectId) {
    return String.format(
        "Firebase options used while communicating with Firebase server APIs: %s, %s%s",
        apiKey, projectId, TextUtils.isEmpty(appId) ? "" : ", " + appId);
  }

  // Read the error message from the response.
  @Nullable
  private static String readErrorResponse(HttpURLConnection conn) {
    InputStream errorStream = conn.getErrorStream();
    if (errorStream == null) {
      return null;
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, UTF_8));
    try {
      StringBuilder response = new StringBuilder();
      for (String input = reader.readLine(); input != null; input = reader.readLine()) {
        response.append(input).append('\n');
      }
      return String.format(
          "Error when communicating with the Firebase Installations server API. HTTP response: [%d %s: %s]",
          conn.getResponseCode(), conn.getResponseMessage(), response);
    } catch (IOException ignored) {
      return null;
    } finally {
      try {
        reader.close();
      } catch (IOException ignored) {

      }
    }
  }
}
