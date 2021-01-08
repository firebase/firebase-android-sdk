// Copyright 2020 Google LLC
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

package com.google.firebase.ml.modeldownloader.internal;

import android.util.JsonReader;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.ml.modeldownloader.CustomModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * Calls the Download Service API and returns the information related to the current status of a
 * custom model. If new model available returns 200 and new model details such as the download url;
 * If same model is available returns 304 (not modified) Otherwise returns an error.
 *
 * @hide
 */
public class CustomModelDownloadService {
  private static final String TAG = "CustomModelDownloadSer";
  private static final int CONNECTION_TIME_OUT_MS = 2000; // 2 seconds.
  private static final Charset UTF_8 = Charset.forName("UTF-8");
  private static final String ISO_DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
  private static final String ACCEPT_ENCODING_HEADER_KEY = "Accept-Encoding";
  private static final String CONTENT_ENCODING_HEADER_KEY = "Content-Encoding";
  private static final String GZIP_CONTENT_ENCODING = "gzip";
  private static final String FIREBASE_DOWNLOAD_HOST = "https://firebaseml.googleapis.com";
  @VisibleForTesting static final String ETAG_HEADER = "etag";
  @VisibleForTesting static final String CONTENT_TYPE = "Content-Type";
  @VisibleForTesting static final String APPLICATION_JSON = "application/json; charset=UTF-8";
  @VisibleForTesting static final String IF_NONE_MATCH_HEADER_KEY = "If-None-Match";

  @VisibleForTesting
  static final String INSTALLATIONS_AUTH_TOKEN_HEADER = "X-Goog-Firebase-Installations-Auth";

  @VisibleForTesting static final String API_KEY_HEADER = "x-goog-api-key";

  @VisibleForTesting
  static final String DOWNLOAD_MODEL_REGEX = "%s/v1beta2/projects/%s/models/%s:download";

  private final ExecutorService executorService;
  private final FirebaseInstallationsApi firebaseInstallations;
  private final String apiKey;
  private String downloadHost = FIREBASE_DOWNLOAD_HOST;

  public CustomModelDownloadService(
      FirebaseOptions firebaseOptions, FirebaseInstallationsApi installationsApi) {
    firebaseInstallations = installationsApi;
    apiKey = firebaseOptions.getApiKey();
    executorService = Executors.newCachedThreadPool();
  }

  @VisibleForTesting
  CustomModelDownloadService(
      FirebaseInstallationsApi firebaseInstallations,
      ExecutorService executorService,
      String apiKey,
      String downloadHost) {
    this.firebaseInstallations = firebaseInstallations;
    this.executorService = executorService;
    this.apiKey = apiKey;
    this.downloadHost = downloadHost;
  }

  /**
   * Calls the Firebase ML Download Service to retrieve the download url for the modelName. Use when
   * a download attempt fails due to an expired timestamp.
   *
   * @param projectNumber - firebase project number
   * @param modelName - model name
   * @return - updated model with new download url and expiry time
   * @throws Exception - errors when Firebase ML Download Service call fails.
   */
  @Nullable
  public Task<CustomModel> getNewDownloadUrlWithExpiry(String projectNumber, String modelName)
      throws Exception {
    return getCustomModelDetails(projectNumber, modelName, "");
  }

  /**
   * Gets the download details for the custom model, returns null if the current model is the
   * latest.
   *
   * @param projectNumber - firebase project number
   * @param modelName - model name
   * @param modelHash - current model hash - input empty string if no current download exists or to
   *     force retrieval of a new download url
   * @return The download details for the model or null if the current model hash matches the latest
   *     model.
   * @throws Exception -errors when call to API fails.
   */
  @Nullable
  public Task<CustomModel> getCustomModelDetails(
      String projectNumber, String modelName, String modelHash) throws Exception {
    try {
      URL url =
          new URL(String.format(DOWNLOAD_MODEL_REGEX, downloadHost, projectNumber, modelName));

      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setConnectTimeout(CONNECTION_TIME_OUT_MS);
      connection.setRequestProperty(ACCEPT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);
      connection.setRequestProperty(CONTENT_TYPE, APPLICATION_JSON);
      if (modelHash != null && !modelHash.isEmpty()) {
        connection.setRequestProperty(IF_NONE_MATCH_HEADER_KEY, modelHash);
      }

      Task<InstallationTokenResult> installationAuthTokenTask =
          firebaseInstallations.getToken(false);
      return installationAuthTokenTask.continueWithTask(
          executorService,
          (CustomModelTask) -> {
            if (!installationAuthTokenTask.isSuccessful()) {
              // TODO(annz) update to better error handling (use FirebaseMLExceptions)
              return Tasks.forException(
                  new Exception(
                      "Firebase Installations failed to get installation auth token for fetch.",
                      installationAuthTokenTask.getException()));
            }

            connection.setRequestProperty(
                INSTALLATIONS_AUTH_TOKEN_HEADER, installationAuthTokenTask.getResult().getToken());
            connection.setRequestProperty(API_KEY_HEADER, apiKey);

            return fetchDownloadDetails(modelName, connection);
          });

    } catch (Exception e) {
      // TODO(annz) update to better error handling (use FirebaseMLExceptions)
      throw new Exception("Error reading custom model from download service: " + e.getMessage(), e);
    }
  }

  @VisibleForTesting
  static long parseTokenExpirationTimestamp(String expiresIn) {
    if (expiresIn == null || expiresIn.length() == 0) {
      return 0;
    }

    try {
      SimpleDateFormat iso8601Format = new SimpleDateFormat(ISO_DATE_PATTERN, Locale.US);
      iso8601Format.setTimeZone(TimeZone.getTimeZone("UTC"));
      Date date = iso8601Format.parse(expiresIn);
      return date.getTime();
    } catch (ParseException pe) {
      // log error and maybe throw an error
      Log.w(TAG, "unable to parse datetime:" + expiresIn, pe);
      return 0;
    }
  }

  private Task<CustomModel> fetchDownloadDetails(String modelName, HttpURLConnection connection)
      throws Exception {
    connection.connect();
    int httpResponseCode = connection.getResponseCode();

    if (httpResponseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
      return Tasks.forResult(null);
    } else if (httpResponseCode == HttpURLConnection.HTTP_OK) {
      return Tasks.forResult(readCustomModelResponse(modelName, connection));
    }

    String errorMessage = getErrorStream(connection);

    // todo(annz) add more specific error handling. NOT_FOUND, etc.
    return Tasks.forException(
        new Exception(
            String.format(
                Locale.getDefault(),
                "Failed to connect to Firebase ML download server with HTTP status code: %d"
                    + " and error message: %s",
                connection.getResponseCode(),
                errorMessage)));
  }

  private CustomModel readCustomModelResponse(
      @NonNull String modelName, HttpURLConnection connection) throws IOException {

    String encodingKey = connection.getHeaderField(CONTENT_ENCODING_HEADER_KEY);
    InputStream inputStream = maybeUnGzip(connection.getInputStream(), encodingKey);
    JsonReader reader = new JsonReader(new InputStreamReader(inputStream, UTF_8));
    long fileSize = 0L;
    String downloadUrl = "";
    long expireTime = 0L;

    String modelHash = maybeUnGzipHeader(connection.getHeaderField(ETAG_HEADER), encodingKey);

    if (modelHash == null || modelHash.isEmpty()) {
      // todo(annz) replace this...
      modelHash = connection.getResponseMessage();
    }

    // JsonReader.peek will sometimes throw AssertionErrors in Android 8.0 and above. See
    // b/79920590 for details.
    reader.beginObject();
    while (reader.hasNext()) {
      String name = reader.nextName();
      if (name.equals("downloadUri")) {
        downloadUrl = reader.nextString();
      } else if (name.equals("expireTime")) {
        expireTime = parseTokenExpirationTimestamp(reader.nextString());
      } else if (name.equals("sizeBytes")) {
        fileSize = reader.nextLong();
      } else {
        reader.skipValue();
      }
    }
    reader.endObject();
    reader.close();
    inputStream.close();

    if (!downloadUrl.isEmpty() && expireTime > 0L) {
      return new CustomModel(modelName, modelHash, fileSize, downloadUrl, expireTime);
    }
    return null;
  }

  private static InputStream maybeUnGzip(InputStream input, String contentEncoding)
      throws IOException {
    if (GZIP_CONTENT_ENCODING.equals(contentEncoding)) {
      return new GZIPInputStream(input);
    }
    return input;
  }

  private static String maybeUnGzipHeader(String header, String contentEncoding) {
    // fix to remove --gzip when content header is gzip for mockwire
    if (GZIP_CONTENT_ENCODING.equals(contentEncoding) && header.endsWith("--gzip")) {
      return header.substring(0, header.lastIndexOf("--gzip"));
    }
    return header;
  }

  private String getErrorStream(HttpURLConnection connection) {
    InputStream errorStream = connection.getErrorStream();
    if (errorStream == null) {
      return null;
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, UTF_8))) {
      StringBuilder response = new StringBuilder();
      for (String input = reader.readLine(); input != null; input = reader.readLine()) {
        response.append(input).append('\n');
      }
      return String.format(
          Locale.ENGLISH,
          "Error when communicating with Firebase Download Service. HTTP response: [%d %s: %s]",
          connection.getResponseCode(),
          connection.getResponseMessage(),
          response);
    } catch (IOException ignored) {
      return null;
    }
  }
}
