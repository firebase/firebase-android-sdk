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

import static com.google.android.gms.common.internal.Preconditions.checkArgument;

import android.os.Build.VERSION_CODES;
import android.util.JsonReader;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.ml.modeldownloader.CustomModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

/**
 * Calls the Download Service API and returns the information related to the current status of a
 * custom model. If new model available returns 200 and new model details such as the download url;
 * If same model is available returns 304 (not modified) Otherwise returns an error.
 */
@RequiresApi(api = VERSION_CODES.KITKAT)
@WorkerThread
class CustomModelDownloadService {

  private static final int CONNECTION_TIME_OUT_MS = 2000; // 2 seconds.
  private static final Charset UTF_8 = StandardCharsets.UTF_8;
  private static final Pattern EXPIRATION_TIMESTAMP_PATTERN = Pattern.compile("[0-9]+s");
  private static final String ETAG_HEADER = "ETag";

  private final String DOWNLOAD_MODEL_REGEX =
      "https://firebaseml.googleapis.com/Model/v1beta2/projects/%s/models/%s:download";

  @VisibleForTesting
  static final String PARSING_EXPIRATION_TIME_ERROR_MESSAGE = "Invalid Expiration Timestamp.";

  @Nullable
  public CustomModel getNewDownloadUrlWithExpiry(String projectNumber, String modelName)
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
  public CustomModel getCustomModelDetails(String projectNumber, String modelName, String modelHash)
      throws Exception {
    try {
      URL url = new URL(String.format(DOWNLOAD_MODEL_REGEX, projectNumber, modelName));
      HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
      connection.setConnectTimeout(CONNECTION_TIME_OUT_MS);
      if (modelHash != null && !modelHash.isEmpty()) {
        connection.setRequestProperty("If-None-Match", modelHash);
      }
      connection.connect();
      int httpResponseCode = connection.getResponseCode();

      if ((httpResponseCode != HttpURLConnection.HTTP_OK)
          && (httpResponseCode != HttpURLConnection.HTTP_NOT_MODIFIED)) {
        String errorMessage = getErrorStream(connection);
        throw new Exception(
            String.format(
                Locale.getDefault(),
                "Failed to connect to Firebase ML download server with HTTP status code: %d"
                    + " and error message: %s",
                connection.getResponseCode(),
                errorMessage));
      } else if (httpResponseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
        return null;
      }

      return readCustomModelResponse(modelName, connection);
    } catch (Exception e) {
      // TODO(annz) update to better error handling (use FirebaseMLExceptions)
      throw new Exception("Error reading custom model from download service: ", e);
    }
  }

  private CustomModel readCustomModelResponse(
      @NonNull String modelName, HttpsURLConnection connection) throws IOException {
    InputStream inputStream = connection.getInputStream();
    JsonReader reader = new JsonReader(new InputStreamReader(inputStream, UTF_8));
    long fileSize = 0L;
    String downloadUrl = "";
    long expireTime = 0L;

    // get model hash from header
    String modelHash = connection.getHeaderField(ETAG_HEADER);

    // JsonReader.peek will sometimes throw AssertionErrors in Android 8.0 and above. See
    // https://b.corp.google.com/issues/79920590 for details.
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

  private String getErrorStream(HttpsURLConnection connection) {
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
