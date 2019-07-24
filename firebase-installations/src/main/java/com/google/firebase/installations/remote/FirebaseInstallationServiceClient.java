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

import androidx.annotation.NonNull;
import java.io.IOException;
import java.net.URL;
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

    HTTP_CLIENT_ERROR,

    CONFLICT,

    NETWORK_ERROR,

    SERVER_ERROR,

    UNAUTHORIZED,
  }

  @NonNull
  public Code createFirebaseInstallation(
      long projectNumber,
      @NonNull String apiKey,
      @NonNull String firebaseInstallationId,
      @NonNull String appId) {
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
          return Code.OK;
        case 401:
          return Code.UNAUTHORIZED;
        case 409:
          return Code.CONFLICT;
        default:
          return Code.SERVER_ERROR;
      }
    } catch (IOException e) {
      return Code.NETWORK_ERROR;
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
  public Code generateAuthToken(
      long projectNumber,
      @NonNull String apiKey,
      @NonNull String fid,
      @NonNull String refreshToken) {
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
}
