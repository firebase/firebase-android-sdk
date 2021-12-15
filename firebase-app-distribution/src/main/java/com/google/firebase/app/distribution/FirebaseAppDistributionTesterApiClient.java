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

import android.content.Context;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.util.AndroidUtilsLight;
import com.google.android.gms.common.util.Hex;
import com.google.firebase.app.distribution.Constants.ErrorMessages;
import com.google.firebase.app.distribution.FirebaseAppDistributionException.Status;
import com.google.firebase.app.distribution.internal.LogWrapper;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
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

  /**
   * Fetches and returns the lastest release for the app that the tester has access to, or null if
   * the tester doesn't have access to any releases.
   */
  @Nullable
  public AppDistributionReleaseInternal fetchNewRelease(
      @NonNull String fid,
      @NonNull String appId,
      @NonNull String apiKey,
      @NonNull String authToken,
      @NonNull Context context)
      throws FirebaseAppDistributionException {
    HttpsURLConnection connection = openHttpsUrlConnection(appId, fid, apiKey, authToken, context);
    String responseBody;
    try (BufferedInputStream inputStream = new BufferedInputStream(connection.getInputStream())) {
      responseBody = convertInputStreamToString(inputStream);
    } catch (IOException e) {
      throw getExceptionForHttpResponse(connection, e);
    } finally {
      connection.disconnect();
    }
    return parseNewRelease(responseBody);
  }

  AppDistributionReleaseInternal parseNewRelease(String responseBody)
      throws FirebaseAppDistributionException {
    try {
      JSONObject responseJson = new JSONObject(responseBody);
      if (!responseJson.has("releases")) {
        return null;
      }
      JSONArray releasesJson = responseJson.getJSONArray("releases");
      if (releasesJson.length() == 0) {
        return null;
      }
      JSONObject newReleaseJson = releasesJson.getJSONObject(0);
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

      AppDistributionReleaseInternal newRelease =
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

      LogWrapper.getInstance().v("Zip hash for the new release " + newRelease.getApkHash());
      return newRelease;
    } catch (JSONException e) {
      LogWrapper.getInstance().e(TAG + "Error parsing the new release.", e);
      throw new FirebaseAppDistributionException(
          ErrorMessages.JSON_PARSING_ERROR, Status.UNKNOWN, e);
    }
  }

  private FirebaseAppDistributionException getExceptionForHttpResponse(
      HttpsURLConnection connection, Exception cause) {
    // TODO(lkellogg): this try-catch should be unnecessary because it will only throw an
    // IOException here if we couldn't connect to the server, in which case getInputStream() would
    // have already failed with the same exception. We also weirdly have to choose one of the two
    // thrown exceptions to set as the cause. We can avoid this by checking the response code
    // first, and then catching any unexpected exceptions when reading the input stream, essentially
    // combining the "default" case below with this try-catch.
    int responseCode;
    try {
      responseCode = connection.getResponseCode();
    } catch (IOException e) {
      return new FirebaseAppDistributionException(
          ErrorMessages.NETWORK_ERROR, Status.NETWORK_FAILURE, e);
    }
    LogWrapper.getInstance().e(TAG + "Failed due to " + responseCode);
    switch (responseCode) {
      case 401:
        return new FirebaseAppDistributionException(
            ErrorMessages.AUTHENTICATION_ERROR, Status.AUTHENTICATION_FAILURE, cause);
      case 403:
      case 400:
        return new FirebaseAppDistributionException(
            ErrorMessages.AUTHORIZATION_ERROR, Status.AUTHENTICATION_FAILURE, cause);
      case 404:
        return new FirebaseAppDistributionException(
            ErrorMessages.NOT_FOUND_ERROR, Status.AUTHENTICATION_FAILURE, cause);
      case 408:
      case 504:
        return new FirebaseAppDistributionException(
            ErrorMessages.TIMEOUT_ERROR, Status.NETWORK_FAILURE, cause);
      default:
        return new FirebaseAppDistributionException(
            ErrorMessages.UNKNOWN_ERROR, Status.UNKNOWN, cause);
    }
  }

  private String tryGetValue(JSONObject jsonObject, String key) {
    try {
      return jsonObject.getString(key);
    } catch (JSONException e) {
      return "";
    }
  }

  HttpsURLConnection openHttpsUrlConnection(
      String appId, String fid, String apiKey, String authToken, Context context)
      throws FirebaseAppDistributionException {
    HttpsURLConnection httpsURLConnection;
    URL url = getReleasesEndpointUrl(appId, fid);
    try {
      httpsURLConnection = (HttpsURLConnection) url.openConnection();
    } catch (IOException e) {
      throw new FirebaseAppDistributionException(
          ErrorMessages.NETWORK_ERROR, Status.NETWORK_FAILURE, e);
    }
    try {
      httpsURLConnection.setRequestMethod(REQUEST_METHOD);
    } catch (ProtocolException e) {
      throw new FirebaseAppDistributionException(ErrorMessages.UNKNOWN_ERROR, Status.UNKNOWN, e);
    }
    httpsURLConnection.setRequestProperty(API_KEY_HEADER, apiKey);
    httpsURLConnection.setRequestProperty(INSTALLATION_AUTH_HEADER, authToken);
    httpsURLConnection.addRequestProperty(X_ANDROID_PACKAGE_HEADER_KEY, context.getPackageName());
    httpsURLConnection.addRequestProperty(
        X_ANDROID_CERT_HEADER_KEY, getFingerprintHashForPackage(context));
    return httpsURLConnection;
  }

  private URL getReleasesEndpointUrl(String appId, String fid)
      throws FirebaseAppDistributionException {
    try {
      return new URL(String.format(RELEASE_ENDPOINT_URL_FORMAT, appId, fid));
    } catch (MalformedURLException e) {
      throw new FirebaseAppDistributionException(
          ErrorMessages.UNKNOWN_ERROR, FirebaseAppDistributionException.Status.UNKNOWN, e);
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

  /** Gets the Android package's SHA-1 fingerprint. */
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
