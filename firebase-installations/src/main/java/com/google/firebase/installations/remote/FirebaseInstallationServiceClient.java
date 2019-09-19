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

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.JsonReader;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.common.util.AndroidUtilsLight;
import com.google.android.gms.common.util.Hex;
import com.google.firebase.installations.InstallationTokenResult;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
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

  private static final String UNAUTHORIZED_ERROR_MESSAGE =
      "The request did not have the required credentials.";
  private static final String INTERNAL_SERVER_ERROR_MESSAGE = "There was an internal server error.";
  private static final String NETWORK_ERROR_MESSAGE = "The server returned an unexpected error:";

  private static final String X_ANDROID_PACKAGE_HEADER_KEY = "X-Android-Package";
  private static final String X_ANDROID_CERT_HEADER_KEY = "X-Android-Cert";

  private static final int NETWORK_TIMEOUT_MILLIS = 10000;

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
   * @return {@link InstallationResponse} generated from the response body
   */
  @NonNull
  public InstallationResponse createFirebaseInstallation(
      @NonNull String apiKey, @NonNull String fid, @NonNull String projectID, @NonNull String appId)
      throws FirebaseInstallationServiceException {
    String resourceName = String.format(CREATE_REQUEST_RESOURCE_NAME_FORMAT, projectID);
    try {
      URL url =
          new URL(
              String.format(
                  "https://%s/%s/%s?key=%s",
                  FIREBASE_INSTALLATIONS_API_DOMAIN,
                  FIREBASE_INSTALLATIONS_API_VERSION,
                  resourceName,
                  apiKey));

      HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
      httpsURLConnection.setConnectTimeout(NETWORK_TIMEOUT_MILLIS);
      httpsURLConnection.setReadTimeout(NETWORK_TIMEOUT_MILLIS);
      httpsURLConnection.setDoOutput(true);
      httpsURLConnection.setRequestMethod("POST");
      httpsURLConnection.addRequestProperty(CONTENT_TYPE_HEADER_KEY, JSON_CONTENT_TYPE);
      httpsURLConnection.addRequestProperty(ACCEPT_HEADER_KEY, JSON_CONTENT_TYPE);
      httpsURLConnection.addRequestProperty(CONTENT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);
      httpsURLConnection.addRequestProperty(X_ANDROID_PACKAGE_HEADER_KEY, context.getPackageName());
      httpsURLConnection.addRequestProperty(
          X_ANDROID_CERT_HEADER_KEY, getFingerprintHashForPackage());

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
      switch (httpResponseCode) {
        case 200:
          return readCreateResponse(httpsURLConnection);
        case 401:
          throw new FirebaseInstallationServiceException(
              UNAUTHORIZED_ERROR_MESSAGE, FirebaseInstallationServiceException.Status.UNAUTHORIZED);
        default:
          throw new FirebaseInstallationServiceException(
              INTERNAL_SERVER_ERROR_MESSAGE,
              FirebaseInstallationServiceException.Status.SERVER_ERROR);
      }
    } catch (IOException e) {
      throw new FirebaseInstallationServiceException(
          NETWORK_ERROR_MESSAGE + e.getMessage(),
          FirebaseInstallationServiceException.Status.NETWORK_ERROR);
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
      throws FirebaseInstallationServiceException {
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

      HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
      httpsURLConnection.setConnectTimeout(NETWORK_TIMEOUT_MILLIS);
      httpsURLConnection.setReadTimeout(NETWORK_TIMEOUT_MILLIS);
      httpsURLConnection.setDoOutput(true);
      httpsURLConnection.setRequestMethod("DELETE");
      httpsURLConnection.addRequestProperty("Authorization", "FIS_v2 " + refreshToken);
      httpsURLConnection.addRequestProperty(CONTENT_TYPE_HEADER_KEY, JSON_CONTENT_TYPE);
      httpsURLConnection.addRequestProperty(CONTENT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);

      int httpResponseCode = httpsURLConnection.getResponseCode();
      switch (httpResponseCode) {
        case 200:
          return;
        case 401:
          throw new FirebaseInstallationServiceException(
              UNAUTHORIZED_ERROR_MESSAGE, FirebaseInstallationServiceException.Status.UNAUTHORIZED);
        default:
          throw new FirebaseInstallationServiceException(
              INTERNAL_SERVER_ERROR_MESSAGE,
              FirebaseInstallationServiceException.Status.SERVER_ERROR);
      }
    } catch (IOException e) {
      throw new FirebaseInstallationServiceException(
          NETWORK_ERROR_MESSAGE + e.getMessage(),
          FirebaseInstallationServiceException.Status.NETWORK_ERROR);
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
      throws FirebaseInstallationServiceException {
    String resourceName =
        String.format(GENERATE_AUTH_TOKEN_REQUEST_RESOURCE_NAME_FORMAT, projectID, fid);
    try {
      URL url =
          new URL(
              String.format(
                  "https://%s/%s/%s?key=%s",
                  FIREBASE_INSTALLATIONS_API_DOMAIN,
                  FIREBASE_INSTALLATIONS_API_VERSION,
                  resourceName,
                  apiKey));

      HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
      httpsURLConnection.setConnectTimeout(NETWORK_TIMEOUT_MILLIS);
      httpsURLConnection.setReadTimeout(NETWORK_TIMEOUT_MILLIS);
      httpsURLConnection.setDoOutput(true);
      httpsURLConnection.setRequestMethod("POST");
      httpsURLConnection.addRequestProperty("Authorization", "FIS_v2 " + refreshToken);
      httpsURLConnection.addRequestProperty(CONTENT_TYPE_HEADER_KEY, JSON_CONTENT_TYPE);
      httpsURLConnection.addRequestProperty(ACCEPT_HEADER_KEY, JSON_CONTENT_TYPE);
      httpsURLConnection.addRequestProperty(CONTENT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);

      int httpResponseCode = httpsURLConnection.getResponseCode();
      switch (httpResponseCode) {
        case 200:
          return readGenerateAuthTokenResponse(httpsURLConnection);
        case 401:
          throw new FirebaseInstallationServiceException(
              UNAUTHORIZED_ERROR_MESSAGE, FirebaseInstallationServiceException.Status.UNAUTHORIZED);
        default:
          throw new FirebaseInstallationServiceException(
              INTERNAL_SERVER_ERROR_MESSAGE,
              FirebaseInstallationServiceException.Status.SERVER_ERROR);
      }
    } catch (IOException e) {
      throw new FirebaseInstallationServiceException(
          NETWORK_ERROR_MESSAGE + e.getMessage(),
          FirebaseInstallationServiceException.Status.NETWORK_ERROR);
    }
  }
  // Read the response from the createFirebaseInstallation API.
  private InstallationResponse readCreateResponse(HttpsURLConnection conn) throws IOException {
    JsonReader reader =
        new JsonReader(new InputStreamReader(conn.getInputStream(), Charset.defaultCharset()));
    InstallationTokenResult.Builder installationTokenResult = InstallationTokenResult.builder();
    InstallationResponse.Builder builder = InstallationResponse.builder();
    reader.beginObject();
    while (reader.hasNext()) {
      String name = reader.nextName();
      if (name.equals("name")) {
        builder.setName(reader.nextString());
      } else if (name.equals("refreshToken")) {
        builder.setRefreshToken(reader.nextString());
      } else if (name.equals("authToken")) {
        reader.beginObject();
        while (reader.hasNext()) {
          String key = reader.nextName();
          if (key.equals("token")) {
            installationTokenResult.setToken(reader.nextString());
          } else if (key.equals("expiresIn")) {
            installationTokenResult.setTokenExpirationInSecs(
                TimeUnit.MILLISECONDS.toSeconds(reader.nextLong()));
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

    return builder.build();
  }

  // Read the response from the generateAuthToken FirebaseInstallation API.
  private InstallationTokenResult readGenerateAuthTokenResponse(HttpsURLConnection conn)
      throws IOException {
    JsonReader reader =
        new JsonReader(new InputStreamReader(conn.getInputStream(), Charset.defaultCharset()));
    InstallationTokenResult.Builder builder = InstallationTokenResult.builder();
    reader.beginObject();
    while (reader.hasNext()) {
      String name = reader.nextName();
      if (name.equals("token")) {
        builder.setToken(reader.nextString());
      } else if (name.equals("expiresIn")) {
        builder.setTokenExpirationInSecs(TimeUnit.MILLISECONDS.toSeconds(reader.nextLong()));
      } else {
        reader.skipValue();
      }
    }
    reader.endObject();

    return builder.build();
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
}
