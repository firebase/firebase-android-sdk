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

import android.util.JsonReader;
import androidx.annotation.NonNull;
import com.google.firebase.FirebaseException;
import com.google.firebase.installations.InstallationTokenResult;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
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
      "projects/%s/installations/%s/auth:generate";
  private static final String DELETE_REQUEST_RESOURCE_NAME_FORMAT = "projects/%s/installations/%s";
  private static final String FIREBASE_INSTALLATIONS_API_VERSION = "v1";
  private static final String FIREBASE_INSTALLATION_AUTH_VERSION = "FIS_V2";

  private static final String CONTENT_TYPE_HEADER_KEY = "Content-Type";
  private static final String JSON_CONTENT_TYPE = "application/json";
  private static final String CONTENT_ENCODING_HEADER_KEY = "Content-Encoding";
  private static final String GZIP_CONTENT_ENCODING = "gzip";

  public enum Code {
    OK,

    NETWORK_ERROR,

    SERVER_ERROR,

    UNAUTHORIZED,
  }

  @NonNull
  public InstallationResponse createFirebaseInstallation(
      long projectNumber,
      @NonNull String apiKey,
      @NonNull String firebaseInstallationId,
      @NonNull String appId)
      throws FirebaseException {
    String resourceName = String.format(CREATE_REQUEST_RESOURCE_NAME_FORMAT, projectNumber);
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
      httpsURLConnection.setDoOutput(true);
      httpsURLConnection.setRequestMethod("POST");
      httpsURLConnection.addRequestProperty(CONTENT_TYPE_HEADER_KEY, JSON_CONTENT_TYPE);
      httpsURLConnection.addRequestProperty(CONTENT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);
      GZIPOutputStream gzipOutputStream =
          new GZIPOutputStream(httpsURLConnection.getOutputStream());
      try {
        gzipOutputStream.write(
            buildCreateFirebaseInstallationRequestBody(firebaseInstallationId, appId)
                .toString()
                .getBytes("UTF-8"));
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
          throw new FirebaseException("The request did not have the required credentials.");
        default:
          throw new FirebaseException("There was an internal server error.");
      }
    } catch (IOException e) {
      throw new FirebaseException("The server returned an unexpected error.: " + e.getMessage());
    }
  }

  private static JSONObject buildCreateFirebaseInstallationRequestBody(String fid, String appId)
      throws JSONException {
    JSONObject firebaseInstallationData = new JSONObject();
    firebaseInstallationData.put("fid", fid);
    firebaseInstallationData.put("appId", appId);
    firebaseInstallationData.put("appVersion", FIREBASE_INSTALLATION_AUTH_VERSION);
    return firebaseInstallationData;
  }

  @NonNull
  public Code deleteFirebaseInstallation(
      long projectNumber,
      @NonNull String apiKey,
      @NonNull String fid,
      @NonNull String refreshToken) {
    String resourceName = String.format(DELETE_REQUEST_RESOURCE_NAME_FORMAT, projectNumber, fid);
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
      httpsURLConnection.setDoOutput(true);
      httpsURLConnection.setRequestMethod("DELETE");
      httpsURLConnection.addRequestProperty("Authorization", "FIS_V2 " + refreshToken);
      httpsURLConnection.addRequestProperty(CONTENT_TYPE_HEADER_KEY, JSON_CONTENT_TYPE);
      httpsURLConnection.addRequestProperty(CONTENT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);

      int httpResponseCode = httpsURLConnection.getResponseCode();
      switch (httpResponseCode) {
        case 200:
          return Code.OK;
        case 401:
          return Code.UNAUTHORIZED;
        default:
          return Code.SERVER_ERROR;
      }
    } catch (IOException e) {
      return Code.NETWORK_ERROR;
    }
  }

  @NonNull
  public InstallationTokenResult generateAuthToken(
      long projectNumber, @NonNull String apiKey, @NonNull String fid, @NonNull String refreshToken)
      throws FirebaseException {
    String resourceName =
        String.format(GENERATE_AUTH_TOKEN_REQUEST_RESOURCE_NAME_FORMAT, projectNumber, fid);
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
      httpsURLConnection.setDoOutput(true);
      httpsURLConnection.setRequestMethod("POST");
      httpsURLConnection.addRequestProperty("Authorization", "FIS_V2 " + refreshToken);
      httpsURLConnection.addRequestProperty(CONTENT_TYPE_HEADER_KEY, JSON_CONTENT_TYPE);
      httpsURLConnection.addRequestProperty(CONTENT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);

      int httpResponseCode = httpsURLConnection.getResponseCode();
      switch (httpResponseCode) {
        case 200:
          return readGenerateAuthTokenResponse(httpsURLConnection);
        case 401:
          throw new FirebaseException("The request did not have the required credentials.");
        default:
          throw new FirebaseException("There was an internal server error.");
      }
    } catch (IOException e) {
      throw new FirebaseException("The server returned an unexpected error.: " + e.getMessage());
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
            installationTokenResult.setAuthToken(reader.nextString());
          } else if (key.equals("expiresIn")) {
            installationTokenResult.setTokenExpirationTimestampMillis(reader.nextLong());
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
        builder.setAuthToken(reader.nextString());
      } else if (name.equals("expiresIn")) {
        builder.setTokenExpirationTimestampMillis(reader.nextLong());
      } else {
        reader.skipValue();
      }
    }
    reader.endObject();

    return builder.build();
  }
}
