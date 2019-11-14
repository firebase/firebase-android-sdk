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
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.installations.remote.InstallationResponse.ResponseCode;
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

  private static final String INTERNAL_SERVER_ERROR_MESSAGE = "There was an internal server error.";
  private static final String NETWORK_ERROR_MESSAGE = "The server returned an unexpected error: %s";

  private static final String X_ANDROID_PACKAGE_HEADER_KEY = "X-Android-Package";
  private static final String X_ANDROID_CERT_HEADER_KEY = "X-Android-Cert";
  private static final String X_ANDROID_IID_MIGRATION_KEY = "X-Goog-Fis-Android-Iid-Migration-Auth";

  private static final int NETWORK_TIMEOUT_MILLIS = 10000;

  private static final Pattern EXPIRATION_TIMESTAMP_PATTERN = Pattern.compile("[0-9]+s");

  private static final int MAX_RETRIES = 1;
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  @VisibleForTesting
  static final String PARSING_EXPIRATION_TIME_ERROR_MESSAGE = "Invalid Expiration Timestamp.";

  private final Context context;

  public FirebaseInstallationServiceClient(@NonNull Context context) {
    this.context = context;
  }

  /**
   * Creates a FID on the FIS Servers by calling FirebaseInstallations API create method.
   *
   * @param apiKey API Key that has access to FIS APIs
   * @param fid Firebase Installation Identifier
   * @param projectID Project Id
   * @param appId the identifier of a Firebase application
   * @param iidToken the identifier of a Firebase application
   * @return {@link InstallationResponse} generated from the response body
   */
  @NonNull
  public InstallationResponse createFirebaseInstallation(
      @NonNull String apiKey,
      @NonNull String fid,
      @NonNull String projectID,
      @NonNull String appId,
      @Nullable String iidToken)
      throws FirebaseException {
    String resourceName = String.format(CREATE_REQUEST_RESOURCE_NAME_FORMAT, projectID);
    try {
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

        // Set the iid token header for authenticating the iid migration to FIS.
        if (iidToken != null) {
          httpsURLConnection.addRequestProperty(X_ANDROID_IID_MIGRATION_KEY, iidToken);
        }

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
        // Usually the FIS server recovers from errors: retry one time before giving up.
        if (httpResponseCode >= 500 && httpResponseCode < 600) {
          retryCount++;
          continue;
        }

        // Unrecoverable server response or unknown error
        throw new FirebaseException(readErrorResponse(httpsURLConnection));
      }
      // Return empty installation response with SERVER_ERROR response code after max retries
      return InstallationResponse.builder().setResponseCode(ResponseCode.SERVER_ERROR).build();
    } catch (IOException e) {
      throw new FirebaseException(String.format(NETWORK_ERROR_MESSAGE, e.getMessage()));
    }
  }

  private static JSONObject buildCreateFirebaseInstallationRequestBody(String fid, String appId)
      throws JSONException {
    JSONObject firebaseInstallationData = new JSONObject();
    firebaseInstallationData.put("fid", fid);
    firebaseInstallationData.put("appId", appId);
    firebaseInstallationData.put("authVersion", FIREBASE_INSTALLATION_AUTH_VERSION);
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
      throws FirebaseException {
    String resourceName = String.format(DELETE_REQUEST_RESOURCE_NAME_FORMAT, projectID, fid);
    try {
      URL url =
          new URL(
              String.format(
                  "https://%s/%s/%s?key=%s",
                  FIREBASE_INSTALLATIONS_API_DOMAIN,
                  FIREBASE_INSTALLATIONS_API_VERSION,
                  resourceName,
                  apiKey));

      HttpsURLConnection httpsURLConnection = openHttpsURLConnection(url);
      httpsURLConnection.setRequestMethod("DELETE");
      httpsURLConnection.addRequestProperty("Authorization", "FIS_v2 " + refreshToken);

      int httpResponseCode = httpsURLConnection.getResponseCode();
      switch (httpResponseCode) {
        case 200:
          return;
        default:
          throw new FirebaseException(readErrorResponse(httpsURLConnection));
      }
    } catch (IOException e) {
      throw new FirebaseException(String.format(NETWORK_ERROR_MESSAGE, e.getMessage()));
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
   */
  @NonNull
  public InstallationTokenResult generateAuthToken(
      @NonNull String apiKey,
      @NonNull String fid,
      @NonNull String projectID,
      @NonNull String refreshToken)
      throws FirebaseException {
    String resourceName =
        String.format(GENERATE_AUTH_TOKEN_REQUEST_RESOURCE_NAME_FORMAT, projectID, fid);
    try {
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
        // Usually the FIS server recovers from errors: retry one time before giving up.
        if (httpResponseCode >= 500 && httpResponseCode < 600) {
          retryCount++;
          continue;
        }

        // Unrecoverable server response or unknown error
        throw new FirebaseException(readErrorResponse(httpsURLConnection));
      }
      throw new FirebaseException(INTERNAL_SERVER_ERROR_MESSAGE);
    } catch (IOException e) {
      throw new FirebaseException(String.format(NETWORK_ERROR_MESSAGE, e.getMessage()));
    }
  }

  private HttpsURLConnection openHttpsURLConnection(URL url) throws IOException {
    HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
    httpsURLConnection.setConnectTimeout(NETWORK_TIMEOUT_MILLIS);
    httpsURLConnection.setReadTimeout(NETWORK_TIMEOUT_MILLIS);
    httpsURLConnection.addRequestProperty(CONTENT_TYPE_HEADER_KEY, JSON_CONTENT_TYPE);
    httpsURLConnection.addRequestProperty(ACCEPT_HEADER_KEY, JSON_CONTENT_TYPE);
    httpsURLConnection.addRequestProperty(CONTENT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);
    httpsURLConnection.addRequestProperty(X_ANDROID_PACKAGE_HEADER_KEY, context.getPackageName());
    httpsURLConnection.addRequestProperty(
        X_ANDROID_CERT_HEADER_KEY, getFingerprintHashForPackage());
    return httpsURLConnection;
  }

  // Read the response from the createFirebaseInstallation API.
  private InstallationResponse readCreateResponse(HttpsURLConnection conn) throws IOException {
    JsonReader reader = new JsonReader(new InputStreamReader(conn.getInputStream(), UTF_8));
    InstallationTokenResult.Builder installationTokenResult = InstallationTokenResult.builder();
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
            installationTokenResult.setToken(reader.nextString());
          } else if (key.equals("expiresIn")) {
            installationTokenResult.setTokenExpirationTimestamp(
                parseTokenExpirationTimestamp(reader.nextString()));
          } else {
            reader.skipValue();
          }
        }
        builder.setAuthToken(installationTokenResult.build());
        reader.endObject();
      } else {
        reader.skipValue();
      }
    }
    reader.endObject();

    return builder.setResponseCode(ResponseCode.OK).build();
  }

  // Read the response from the generateAuthToken FirebaseInstallation API.
  private InstallationTokenResult readGenerateAuthTokenResponse(HttpsURLConnection conn)
      throws IOException {
    JsonReader reader = new JsonReader(new InputStreamReader(conn.getInputStream(), UTF_8));
    InstallationTokenResult.Builder builder = InstallationTokenResult.builder();
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

    return builder.build();
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
