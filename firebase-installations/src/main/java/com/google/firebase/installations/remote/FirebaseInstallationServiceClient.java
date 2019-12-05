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

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.JsonReader;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.util.AndroidUtilsLight;
import com.google.android.gms.common.util.Hex;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.FirebaseException;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.heartbeatinfo.HeartBeatInfo.HeartBeat;
import com.google.firebase.installations.FirebaseInstallationsException;
import com.google.firebase.installations.FirebaseInstallationsException.Status;
import com.google.firebase.installations.remote.InstallationResponse.ResponseCode;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

/** Http client that sends request to Firebase Installations backend API. */
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

  /** Heartbeat tag for firebase installations. */
  private static final String FIREBASE_INSTALLATIONS_ID_HEARTBEAT_TAG = "fire-installations-id";

  private static final String HEART_BEAT_HEADER = "x-firebase-client-log-type";
  private static final String USER_AGENT_HEADER = "x-firebase-client";

  private static final String X_ANDROID_PACKAGE_HEADER_KEY = "X-Android-Package";
  private static final String X_ANDROID_CERT_HEADER_KEY = "X-Android-Cert";

  private static final int NETWORK_TIMEOUT_MILLIS = 10000;

  private static final Pattern EXPIRATION_TIMESTAMP_PATTERN = Pattern.compile("[0-9]+s");

  private static final int MAX_RETRIES = 1;
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  @VisibleForTesting
  static final String PARSING_EXPIRATION_TIME_ERROR_MESSAGE = "Invalid Expiration Timestamp.";

  private final Context context;
  private final UserAgentPublisher userAgentPublisher;
  private final HeartBeatInfo heartbeatInfo;

  public FirebaseInstallationServiceClient(
      @NonNull Context context,
      @Nullable UserAgentPublisher publisher,
      @Nullable HeartBeatInfo heartBeatInfo) {
    this.context = context;
    this.userAgentPublisher = publisher;
    this.heartbeatInfo = heartBeatInfo;
  }

  /**
   * Creates a FID on the FIS Servers by calling FirebaseInstallations API create method.
   *
   * @param apiKey API Key that has access to FIS APIs
   * @param fid Firebase Installation Identifier
   * @param projectID Project Id
   * @param appId the identifier of a Firebase application
   * @return {@link InstallationResponse} generated from the response body
   *     <ul>
   *       <li>400: return response with status BAD_CONFIG
   *       <li>403: return response with status BAD_CONFIG
   *       <li>403: return response with status BAD_CONFIG
   *       <li>429: throw IOException
   *       <li>500: throw IOException
   *     </ul>
   */
  @NonNull
  public InstallationResponse createFirebaseInstallation(
      @NonNull String apiKey, @NonNull String fid, @NonNull String projectID, @NonNull String appId)
      throws IOException {
    String resourceName = String.format(CREATE_REQUEST_RESOURCE_NAME_FORMAT, projectID);
    int retryCount = 0;
    URL url =
        new URL(
            String.format(
                "https://%s/%s/%s?key=%s",
                FIREBASE_INSTALLATIONS_API_DOMAIN,
                FIREBASE_INSTALLATIONS_API_VERSION,
                resourceName,
                apiKey));
    while (retryCount <= MAX_RETRIES) {
      HttpsURLConnection httpsURLConnection = openHttpsURLConnection(url);
      httpsURLConnection.setRequestMethod("POST");
      httpsURLConnection.setDoOutput(true);

      GZIPOutputStream gzipOutputStream =
          new GZIPOutputStream(httpsURLConnection.getOutputStream());
      try {
        gzipOutputStream.write(
            buildCreateFirebaseInstallationRequestBody(fid, appId).toString().getBytes("UTF-8"));
      } catch (JSONException e) {
        throw new IllegalStateException(e);
      } finally {
        gzipOutputStream.close();
      }

      int httpResponseCode = httpsURLConnection.getResponseCode();

      if (httpResponseCode == 200) {
        return readCreateResponse(httpsURLConnection);
      }

      if (httpResponseCode == 429 || (httpResponseCode >= 500 && httpResponseCode < 600)) {
        retryCount++;
        continue;
      }

      // Return empty installation response with BAD_CONFIG response code after max retries
      return InstallationResponse.builder().setResponseCode(ResponseCode.BAD_CONFIG).build();
    }

    throw new IOException();
  }

  private static JSONObject buildCreateFirebaseInstallationRequestBody(String fid, String appId)
      throws JSONException {
    JSONObject firebaseInstallationData = new JSONObject();
    firebaseInstallationData.put("fid", fid);
    firebaseInstallationData.put("appId", appId);
    firebaseInstallationData.put("authVersion", FIREBASE_INSTALLATION_AUTH_VERSION);
    firebaseInstallationData.put("sdkVersion", "t.1.1.0");
    return firebaseInstallationData;
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
      throws FirebaseException, IOException {
    String resourceName = String.format(DELETE_REQUEST_RESOURCE_NAME_FORMAT, projectID, fid);
    URL url =
        new URL(
            String.format(
                "https://%s/%s/%s?key=%s",
                FIREBASE_INSTALLATIONS_API_DOMAIN,
                FIREBASE_INSTALLATIONS_API_VERSION,
                resourceName,
                apiKey));

    int retryCount = 0;
    while (retryCount <= MAX_RETRIES) {
      HttpsURLConnection httpsURLConnection = openHttpsURLConnection(url);
      httpsURLConnection.setRequestMethod("DELETE");
      httpsURLConnection.addRequestProperty("Authorization", "FIS_v2 " + refreshToken);

      int httpResponseCode = httpsURLConnection.getResponseCode();

      if (httpResponseCode == 200 || httpResponseCode == 401 || httpResponseCode == 404) {
        return;
      }

      if (httpResponseCode == 429 || (httpResponseCode >= 500 && httpResponseCode < 600)) {
        retryCount++;
        continue;
      }

      throw new FirebaseInstallationsException(
          "bad config while trying to delete FID", Status.BAD_CONFIG);
    }

    throw new IOException();
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
   *       <li>429: throw IOException
   *       <li>500: throw IOException
   *     </ul>
   */
  @NonNull
  public TokenResult generateAuthToken(
      @NonNull String apiKey,
      @NonNull String fid,
      @NonNull String projectID,
      @NonNull String refreshToken)
      throws IOException {
    String resourceName =
        String.format(GENERATE_AUTH_TOKEN_REQUEST_RESOURCE_NAME_FORMAT, projectID, fid);
    int retryCount = 0;
    URL url =
        new URL(
            String.format(
                "https://%s/%s/%s?key=%s",
                FIREBASE_INSTALLATIONS_API_DOMAIN,
                FIREBASE_INSTALLATIONS_API_VERSION,
                resourceName,
                apiKey));
    while (retryCount <= MAX_RETRIES) {
      HttpsURLConnection httpsURLConnection = openHttpsURLConnection(url);
      httpsURLConnection.setRequestMethod("POST");
      httpsURLConnection.addRequestProperty("Authorization", "FIS_v2 " + refreshToken);

      int httpResponseCode = httpsURLConnection.getResponseCode();

      if (httpResponseCode == 200) {
        return readGenerateAuthTokenResponse(httpsURLConnection);
      }

      if (httpResponseCode == 401 || httpResponseCode == 404) {
        return TokenResult.builder().setResponseCode(TokenResult.ResponseCode.AUTH_ERROR).build();
      }

      if (httpResponseCode == 429 || (httpResponseCode >= 500 && httpResponseCode < 600)) {
        retryCount++;
        continue;
      }

      return TokenResult.builder().setResponseCode(TokenResult.ResponseCode.BAD_CONFIG).build();
    }
    throw new IOException();
  }

  private HttpsURLConnection openHttpsURLConnection(URL url) throws IOException {
    HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
    httpsURLConnection.setConnectTimeout(NETWORK_TIMEOUT_MILLIS);
    httpsURLConnection.setReadTimeout(NETWORK_TIMEOUT_MILLIS);
    httpsURLConnection.addRequestProperty(CONTENT_TYPE_HEADER_KEY, JSON_CONTENT_TYPE);
    httpsURLConnection.addRequestProperty(ACCEPT_HEADER_KEY, JSON_CONTENT_TYPE);
    httpsURLConnection.addRequestProperty(CONTENT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);
    httpsURLConnection.addRequestProperty(X_ANDROID_PACKAGE_HEADER_KEY, context.getPackageName());
    if (heartbeatInfo != null && userAgentPublisher != null) {
      HeartBeat heartbeat = heartbeatInfo.getHeartBeatCode(FIREBASE_INSTALLATIONS_ID_HEARTBEAT_TAG);
      if (heartbeat != HeartBeat.NONE) {
        httpsURLConnection.addRequestProperty(USER_AGENT_HEADER, userAgentPublisher.getUserAgent());
        httpsURLConnection.addRequestProperty(
            HEART_BEAT_HEADER, Integer.toString(heartbeat.getCode()));
      }
    }
    httpsURLConnection.addRequestProperty(
        X_ANDROID_CERT_HEADER_KEY, getFingerprintHashForPackage());
    return httpsURLConnection;
  }

  // Read the response from the createFirebaseInstallation API.
  private InstallationResponse readCreateResponse(HttpsURLConnection conn) throws IOException {
    JsonReader reader = new JsonReader(new InputStreamReader(conn.getInputStream(), UTF_8));
    TokenResult.Builder tokenResult = TokenResult.builder();
    InstallationResponse.Builder builder = InstallationResponse.builder();
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

    return builder.setResponseCode(ResponseCode.OK).build();
  }

  // Read the response from the generateAuthToken FirebaseInstallation API.
  private TokenResult readGenerateAuthTokenResponse(HttpsURLConnection conn) throws IOException {
    JsonReader reader = new JsonReader(new InputStreamReader(conn.getInputStream(), UTF_8));
    TokenResult.Builder builder = TokenResult.builder();
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

    return builder.setResponseCode(TokenResult.ResponseCode.OK).build();
  }

  // Read the error message from the response.
  private String readErrorResponse(HttpsURLConnection conn) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), UTF_8));
    StringBuilder response = new StringBuilder();
    for (String input = reader.readLine(); input != null; input = reader.readLine()) {
      response.append(input).append('\n');
    }
    return String.format(
        "The server responded with an error. HTTP response: [%d %s %s]",
        conn.getResponseCode(), conn.getResponseMessage(), response);
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
}
