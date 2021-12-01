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

package com.google.firebase.app.distribution;

import static com.google.firebase.app.distribution.FirebaseAppDistributionException.Status.AUTHENTICATION_FAILURE;
import static com.google.firebase.app.distribution.FirebaseAppDistributionException.Status.NETWORK_FAILURE;

import android.content.Context;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import com.google.android.gms.common.util.AndroidUtilsLight;
import com.google.android.gms.common.util.Hex;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

class FirebaseAppDistributionTesterApiClient {

  private static final String RELEASE_ENDPOINT_URL_FORMAT =
      "https://firebaseapptesters.googleapis.com/v1alpha/devices/-/testerApps/%s/installations/%s/releases";
  private static final String REQUEST_METHOD = "GET";
  private static final String API_KEY_HEADER = "x-goog-api-key";
  private static final String INSTALLATION_AUTH_HEADER = "X-Goog-Firebase-Installations-Auth";
  private static final String X_ANDROID_PACKAGE_HEADER_KEY = "X-Android-Package";
  private static final String X_ANDROID_CERT_HEADER_KEY = "X-Android-Cert";

  private static final String BUILD_VERSION_JSON_KEY = "buildVersion";
  private static final String DISPLAY_VERSION_JSON_KEY = "displayVersion";
  private static final String RELEASE_NOTES_JSON_KEY = "releaseNotes";
  private static final String BINARY_TYPE_JSON_KEY = "binaryType";
  private static final String CODE_HASH_KEY = "codeHash";
  private static final String APK_HASH_KEY = "apkHash";
  private static final String IAS_ARTIFACT_ID_KEY = "iasArtifactId";
  private static final String DOWNLOAD_URL_KEY = "downloadUrl";

  private static final String TAG = "TesterApiClient:";

  public static final int DEFAULT_BUFFER_SIZE = 8192;

  public @NonNull AppDistributionReleaseInternal fetchNewRelease(
      @NonNull String fid,
      @NonNull String appId,
      @NonNull String apiKey,
      @NonNull String authToken,
      @NonNull Context context)
      throws FirebaseAppDistributionException {

    AppDistributionReleaseInternal newRelease;
    HttpsURLConnection connection = openHttpsUrlConnection(appId, fid);
    try {
      connection.setRequestMethod(REQUEST_METHOD);
      connection.setRequestProperty(API_KEY_HEADER, apiKey);
      connection.setRequestProperty(INSTALLATION_AUTH_HEADER, authToken);
      connection.addRequestProperty(X_ANDROID_PACKAGE_HEADER_KEY, context.getPackageName());
      connection.addRequestProperty(
          X_ANDROID_CERT_HEADER_KEY, getFingerprintHashForPackage(context));

      InputStream inputStream = connection.getInputStream();
      JSONObject newReleaseJson = readFetchReleaseInputStream(inputStream);
      final String displayVersion = newReleaseJson.getString(DISPLAY_VERSION_JSON_KEY);
      final String buildVersion = newReleaseJson.getString(BUILD_VERSION_JSON_KEY);
      String releaseNotes = tryGetValue(newReleaseJson, RELEASE_NOTES_JSON_KEY);
      String codeHash = tryGetValue(newReleaseJson, CODE_HASH_KEY);
      String apkHash = tryGetValue(newReleaseJson, APK_HASH_KEY);
      String iasArtifactId = tryGetValue(newReleaseJson, IAS_ARTIFACT_ID_KEY);
      String downloadUrl = tryGetValue(newReleaseJson, DOWNLOAD_URL_KEY);

      final BinaryType binaryType =
          newReleaseJson.getString(BINARY_TYPE_JSON_KEY).equals("APK")
              ? BinaryType.APK
              : BinaryType.AAB;

      newRelease =
          AppDistributionReleaseInternal.builder()
              .setDisplayVersion(displayVersion)
              .setBuildVersion(buildVersion)
              .setReleaseNotes(releaseNotes)
              .setBinaryType(binaryType)
              .setIasArtifactId(iasArtifactId)
              .setCodeHash(codeHash)
              .setApkHash(apkHash)
              .setDownloadUrl(downloadUrl)
              .build();
      inputStream.close();

    } catch (IOException | JSONException e) {
      if (e instanceof JSONException) {
        LogWrapper.getInstance().e(TAG + "Error parsing the new release.", e);
        throw new FirebaseAppDistributionException(
            Constants.ErrorMessages.JSON_PARSING_ERROR, NETWORK_FAILURE, e);
      }
      throw getExceptionForHttpResponse(connection);
    } finally {
      connection.disconnect();
    }
    LogWrapper.getInstance().v("Zip hash for the new release " + newRelease.getApkHash());
    return newRelease;
  }

  private FirebaseAppDistributionException getExceptionForHttpResponse(
      HttpsURLConnection connection) {
    try {
      LogWrapper.getInstance().e(TAG + "Failed due to " + connection.getResponseCode());
      switch (connection.getResponseCode()) {
        case 401:
          return new FirebaseAppDistributionException(
              Constants.ErrorMessages.AUTHENTICATION_ERROR, AUTHENTICATION_FAILURE);
        case 403:
        case 400:
          return new FirebaseAppDistributionException(
              Constants.ErrorMessages.AUTHORIZATION_ERROR, AUTHENTICATION_FAILURE);
        case 404:
          return new FirebaseAppDistributionException(
              Constants.ErrorMessages.NOT_FOUND_ERROR, AUTHENTICATION_FAILURE);
        case 408:
        case 504:
          return new FirebaseAppDistributionException(
              Constants.ErrorMessages.TIMEOUT_ERROR, NETWORK_FAILURE);
        default:
          return new FirebaseAppDistributionException(
              Constants.ErrorMessages.NETWORK_ERROR, NETWORK_FAILURE);
      }
    } catch (IOException ex) {
      return new FirebaseAppDistributionException(
          Constants.ErrorMessages.NETWORK_ERROR, NETWORK_FAILURE, ex);
    }
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
    JSONObject newRelease;
    InputStream jsonIn = new BufferedInputStream(in);
    String result = convertInputStreamToString(jsonIn);
    try {
      JSONObject json = new JSONObject(result);
      newRelease = json.getJSONArray("releases").getJSONObject(0);
    } catch (JSONException e) {
      throw new FirebaseAppDistributionException(
          Constants.ErrorMessages.JSON_PARSING_ERROR,
          FirebaseAppDistributionException.Status.UNKNOWN,
          e);
    }
    return newRelease;
  }

  HttpsURLConnection openHttpsUrlConnection(String appId, String fid)
      throws FirebaseAppDistributionException {
    HttpsURLConnection httpsURLConnection;
    URL url = getReleasesEndpointUrl(appId, fid);
    try {
      httpsURLConnection = (HttpsURLConnection) url.openConnection();
    } catch (IOException e) {
      throw new FirebaseAppDistributionException(
          Constants.ErrorMessages.NETWORK_ERROR, NETWORK_FAILURE, e);
    }
    return httpsURLConnection;
  }

  private URL getReleasesEndpointUrl(String appId, String fid)
      throws FirebaseAppDistributionException {
    try {
      return new URL(String.format(RELEASE_ENDPOINT_URL_FORMAT, appId, fid));
    } catch (MalformedURLException e) {
      throw new FirebaseAppDistributionException(
          Constants.ErrorMessages.UNKNOWN_ERROR,
          FirebaseAppDistributionException.Status.UNKNOWN,
          e);
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

  /**
   * Gets the Android package's SHA-1 fingerprint.
   *
   * @param context
   */
  private String getFingerprintHashForPackage(Context context) {
    byte[] hash;

    try {
      hash = AndroidUtilsLight.getPackageCertificateHashBytes(context, context.getPackageName());

      if (hash == null) {
        return null;
      } else {
        return Hex.bytesToStringUppercase(hash, /* zeroTerminated= */ false);
      }
    } catch (PackageManager.NameNotFoundException e) {
      LogWrapper.getInstance().e(TAG + "No such package: " + context.getPackageName(), e);
      return null;
    }
  }
}
