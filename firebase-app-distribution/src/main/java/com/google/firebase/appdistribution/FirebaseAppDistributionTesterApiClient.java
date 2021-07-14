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

package com.google.firebase.appdistribution;

import androidx.annotation.NonNull;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

public class FirebaseAppDistributionTesterApiClient {

  private static final String RELEASE_ENDPOINT_URL_FORMAT =
      "https://firebaseapptesters.googleapis.com/v1alpha/devices/-/testerApps/%s/installations/%s/releases";
  private static final String REQUEST_METHOD = "GET";
  private static final String API_KEY_HEADER = "x-goog-api-key";
  private static final String INSTALLATION_AUTH_HEADER = "X-Goog-Firebase-Installations-Auth";
  private static final String BUILD_VERSION_JSON_KEY = "buildVersion";
  private static final String DISPLAY_VERSION_JSON_KEY = "displayVersion";
  private static final String RELEASE_NOTES_JSON_KEY = "releaseNotes";
  private static final String BINARY_TYPE_JSON_KEY = "binaryType";
  private static final String CODE_HASH_KEY = "codeHash";
  private static final String IAS_ARTIFACT_ID_KEY = "iasArtifactId";
  private static final String DOWNLOAD_URL_KEY = "downloadUrl";
  public static final int DEFAULT_BUFFER_SIZE = 8192;

  public @NonNull AppDistributionReleaseInternal fetchLatestRelease(
      @NonNull String fid, @NonNull String appId, @NonNull String apiKey, @NonNull String authToken)
      throws FirebaseAppDistributionException, ProtocolException {

    AppDistributionReleaseInternal latestRelease;
    HttpsURLConnection conn = openHttpsUrlConnection(appId, fid);
    conn.setRequestMethod(REQUEST_METHOD);
    conn.setRequestProperty(API_KEY_HEADER, apiKey);
    conn.setRequestProperty(INSTALLATION_AUTH_HEADER, authToken);

    try {
      JSONObject latestReleaseJson = readFetchReleaseInputStream(conn.getInputStream());
      final String displayVersion = latestReleaseJson.getString(DISPLAY_VERSION_JSON_KEY);
      final String buildVersion = latestReleaseJson.getString(BUILD_VERSION_JSON_KEY);
      String releaseNotes = tryGetValue(latestReleaseJson, RELEASE_NOTES_JSON_KEY);
      String codeHash = tryGetValue(latestReleaseJson, CODE_HASH_KEY);
      String iasArtifactId = tryGetValue(latestReleaseJson, IAS_ARTIFACT_ID_KEY);
      String downloadUrl = tryGetValue(latestReleaseJson, DOWNLOAD_URL_KEY);

      final BinaryType binaryType =
          latestReleaseJson.getString(BINARY_TYPE_JSON_KEY).equals("APK")
              ? BinaryType.APK
              : BinaryType.AAB;

      latestRelease =
          AppDistributionReleaseInternal.builder()
              .setDisplayVersion(displayVersion)
              .setBuildVersion(buildVersion)
              .setReleaseNotes(releaseNotes)
              .setBinaryType(binaryType)
              .setIasArtifactId(iasArtifactId)
              .setCodeHash(codeHash)
              .setDownloadUrl(downloadUrl)
              .build();

    } catch (IOException | JSONException e) {
      // todo: change error status based on response code
      throw new FirebaseAppDistributionException(
          FirebaseAppDistributionException.Status.NETWORK_FAILURE);
    } finally {
      conn.disconnect();
    }

    return latestRelease;
  }

  private String tryGetValue(JSONObject jsonObject, String key) {
    try {
      return jsonObject.getString(key);
    } catch (JSONException e) {
      return "";
    }
  }

  private JSONObject readFetchReleaseInputStream(InputStream in)
      throws FirebaseAppDistributionException, IOException {
    JSONObject latestRelease;
    InputStream jsonIn = new BufferedInputStream(in);
    String result = convertInputStreamToString(jsonIn);
    try {
      JSONObject json = new JSONObject(result);
      latestRelease = json.getJSONArray("releases").getJSONObject(0);
    } catch (JSONException e) {
      throw new FirebaseAppDistributionException(
          e.getMessage(), FirebaseAppDistributionException.Status.UNKNOWN);
    }
    return latestRelease;
  }

  HttpsURLConnection openHttpsUrlConnection(String appId, String fid)
      throws FirebaseAppDistributionException {
    HttpsURLConnection httpsURLConnection;
    URL url = getReleasesEndpointUrl(appId, fid);
    try {
      httpsURLConnection = (HttpsURLConnection) url.openConnection();
    } catch (IOException e) {
      throw new FirebaseAppDistributionException(
          e.getMessage(), FirebaseAppDistributionException.Status.NETWORK_FAILURE);
    }
    return httpsURLConnection;
  }

  private URL getReleasesEndpointUrl(String appId, String fid)
      throws FirebaseAppDistributionException {
    try {
      return new URL(String.format(RELEASE_ENDPOINT_URL_FORMAT, appId, fid));
    } catch (MalformedURLException e) {
      throw new FirebaseAppDistributionException(
          e.getMessage(), FirebaseAppDistributionException.Status.UNKNOWN);
    }
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
}
