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

package com.google.firebase.segmentation.remote;

import static com.google.firebase.segmentation.FirebaseSegmentation.TAG;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.common.util.AndroidUtilsLight;
import com.google.android.gms.common.util.Hex;
import java.io.IOException;
import java.net.URL;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Http client that sends request to Firebase Segmentation backend API. To be implemented
 *
 * @hide
 */
public class SegmentationServiceClient {

  private static final String FIREBASE_SEGMENTATION_API_DOMAIN =
      "firebasesegmentation.googleapis.com";
  private static final String UPDATE_REQUEST_RESOURCE_NAME_FORMAT =
      "projects/%s/installations/%s/customSegmentationData";
  private static final String CLEAR_REQUEST_RESOURCE_NAME_FORMAT =
      "projects/%s/installations/%s/customSegmentationData:clear";
  private static final String FIREBASE_SEGMENTATION_API_VERSION = "v1alpha";

  private static final String CONTENT_TYPE_HEADER_KEY = "Content-Type";
  private static final String JSON_CONTENT_TYPE = "application/json";
  private static final String CONTENT_ENCODING_HEADER_KEY = "Content-Encoding";
  private static final String GZIP_CONTENT_ENCODING = "gzip";
  private static final String X_ANDROID_PACKAGE_HEADER_KEY = "X-Android-Package";
  private static final String X_ANDROID_CERT_HEADER_KEY = "X-Android-Cert";

  private final Context context;

  public SegmentationServiceClient(@NonNull Context context) {
    this.context = context;
  }

  public enum Code {
    OK,

    CONFLICT,

    UNAUTHORIZED,

    NETWORK_ERROR,

    HTTP_CLIENT_ERROR,

    SERVER_ERROR,
  }

  @NonNull
  public Code updateCustomInstallationId(
      long projectNumber,
      @NonNull String apiKey,
      @NonNull String customInstallationId,
      @NonNull String firebaseInstanceId,
      @NonNull String firebaseInstanceIdToken) {
    String resourceName =
        String.format(UPDATE_REQUEST_RESOURCE_NAME_FORMAT, projectNumber, firebaseInstanceId);
    try {
      URL url =
          new URL(
              String.format(
                  "https://%s/%s/%s?key=%s",
                  FIREBASE_SEGMENTATION_API_DOMAIN,
                  FIREBASE_SEGMENTATION_API_VERSION,
                  resourceName,
                  apiKey));

      HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
      httpsURLConnection.setDoOutput(true);
      httpsURLConnection.setRequestMethod("PATCH");
      httpsURLConnection.addRequestProperty(
          "Authorization", "FIREBASE_INSTALLATIONS_AUTH " + firebaseInstanceIdToken);
      httpsURLConnection.addRequestProperty(CONTENT_TYPE_HEADER_KEY, JSON_CONTENT_TYPE);
      httpsURLConnection.addRequestProperty(CONTENT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);
      httpsURLConnection.addRequestProperty(X_ANDROID_PACKAGE_HEADER_KEY, context.getPackageName());
      httpsURLConnection.addRequestProperty(
          X_ANDROID_CERT_HEADER_KEY, getFingerprintHashForPackage());
      GZIPOutputStream gzipOutputStream =
          new GZIPOutputStream(httpsURLConnection.getOutputStream());
      try {
        gzipOutputStream.write(
            buildUpdateCustomSegmentationDataRequestBody(resourceName, customInstallationId)
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
          if (httpResponseCode / 100 == 4) {
            return Code.HTTP_CLIENT_ERROR;
          }
          return Code.SERVER_ERROR;
      }
    } catch (IOException e) {
      return Code.NETWORK_ERROR;
    }
  }

  private static JSONObject buildUpdateCustomSegmentationDataRequestBody(
      String resourceName, String customInstallationId) throws JSONException {
    JSONObject customSegmentationData = new JSONObject();
    customSegmentationData.put("name", resourceName);
    customSegmentationData.put("custom_installation_id", customInstallationId);
    return customSegmentationData;
  }

  @NonNull
  public Code clearCustomInstallationId(
      long projectNumber,
      @NonNull String apiKey,
      @NonNull String firebaseInstanceId,
      @NonNull String firebaseInstanceIdToken) {
    String resourceName =
        String.format(CLEAR_REQUEST_RESOURCE_NAME_FORMAT, projectNumber, firebaseInstanceId);
    try {
      URL url =
          new URL(
              String.format(
                  "https://%s/%s/%s?key=%s",
                  FIREBASE_SEGMENTATION_API_DOMAIN,
                  FIREBASE_SEGMENTATION_API_VERSION,
                  resourceName,
                  apiKey));

      HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
      httpsURLConnection.setDoOutput(true);
      httpsURLConnection.setRequestMethod("POST");
      httpsURLConnection.addRequestProperty(
          "Authorization", "FIREBASE_INSTALLATIONS_AUTH " + firebaseInstanceIdToken);
      httpsURLConnection.addRequestProperty(CONTENT_TYPE_HEADER_KEY, JSON_CONTENT_TYPE);
      httpsURLConnection.addRequestProperty(CONTENT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);
      httpsURLConnection.addRequestProperty(X_ANDROID_PACKAGE_HEADER_KEY, context.getPackageName());
      httpsURLConnection.addRequestProperty(
          X_ANDROID_CERT_HEADER_KEY, getFingerprintHashForPackage());
      GZIPOutputStream gzipOutputStream =
          new GZIPOutputStream(httpsURLConnection.getOutputStream());
      try {
        gzipOutputStream.write(
            buildClearCustomSegmentationDataRequestBody(resourceName).toString().getBytes("UTF-8"));
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
        default:
          if (httpResponseCode / 100 == 4) {
            return Code.HTTP_CLIENT_ERROR;
          }
          return Code.SERVER_ERROR;
      }
    } catch (IOException e) {
      return Code.NETWORK_ERROR;
    }
  }

  private static JSONObject buildClearCustomSegmentationDataRequestBody(String resourceName)
      throws JSONException {
    return new JSONObject().put("name", resourceName);
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
        String cert = Hex.bytesToStringUppercase(hash, /* zeroTerminated= */ false);
        return Hex.bytesToStringUppercase(hash, /* zeroTerminated= */ false);
      }
    } catch (PackageManager.NameNotFoundException e) {
      Log.e(TAG, "No such package: " + context.getPackageName(), e);
      return null;
    }
  }
}
