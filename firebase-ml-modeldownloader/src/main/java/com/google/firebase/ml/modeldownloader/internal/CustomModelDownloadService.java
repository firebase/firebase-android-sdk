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

import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.gms.common.util.AndroidUtilsLight;
import com.google.android.gms.common.util.Hex;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.FirebaseMlException;
import com.google.firebase.ml.modeldownloader.FirebaseMlException.Code;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ErrorCode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;
import javax.inject.Inject;
import org.json.JSONObject;

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
  private static final String ERROR_RESPONSE_ERROR = "error";
  private static final String ERROR_RESPONSE_MESSAGE = "message";

  @VisibleForTesting
  static final String ETAG_HEADER = "etag";
  @VisibleForTesting static final String CONTENT_TYPE = "Content-Type";
  @VisibleForTesting static final String APPLICATION_JSON = "application/json; charset=UTF-8";
  @VisibleForTesting static final String IF_NONE_MATCH_HEADER_KEY = "If-None-Match";

  @VisibleForTesting
  static final String INSTALLATIONS_AUTH_TOKEN_HEADER = "X-Goog-Firebase-Installations-Auth";

  @VisibleForTesting static final String API_KEY_HEADER = "x-goog-api-key";
  @VisibleForTesting static final String X_ANDROID_PACKAGE_HEADER = "X-Android-Package";
  @VisibleForTesting static final String X_ANDROID_CERT_HEADER = "X-Android-Cert";

  @VisibleForTesting
  static final String DOWNLOAD_MODEL_REGEX = "%s/v1beta2/projects/%s/models/%s:download";

  private final Provider<FirebaseInstallationsApi> firebaseInstallations;
  private final FirebaseMlLogger eventLogger;
  private final String apiKey;
  @Nullable private final String fingerprintHashForPackage;
  private final Context context;
  private final CustomModel.Factory modelFactory;
  private String downloadHost = FIREBASE_DOWNLOAD_HOST;
  private final Executor blockingExecutor;

  @Inject
  public CustomModelDownloadService(
      Context context,
      FirebaseOptions options,
      Provider<FirebaseInstallationsApi> installationsApi,
      FirebaseMlLogger eventLogger,
      CustomModel.Factory modelFactory,
      @Blocking Executor blockingExecutor) {
    this.context = context;
    firebaseInstallations = installationsApi;
    apiKey = options.getApiKey();
    fingerprintHashForPackage = getFingerprintHashForPackage(context);
    this.blockingExecutor = blockingExecutor;
    this.eventLogger = eventLogger;
    this.modelFactory = modelFactory;
  }

  @VisibleForTesting
  CustomModelDownloadService(
      Context context,
      Provider<FirebaseInstallationsApi> firebaseInstallations,
      Executor blockingExecutor,
      String apiKey,
      String fingerprintHashForPackage,
      String downloadHost,
      FirebaseMlLogger eventLogger,
      CustomModel.Factory modelFactory) {
    this.context = context;
    this.firebaseInstallations = firebaseInstallations;
    this.blockingExecutor = blockingExecutor;
    this.apiKey = apiKey;
    this.fingerprintHashForPackage = fingerprintHashForPackage;
    this.downloadHost = downloadHost;
    this.eventLogger = eventLogger;
    this.modelFactory = modelFactory;
  }

  /**
   * Calls the Firebase ML Download Service to retrieve the download url for the modelName. Use when
   * a download attempt fails due to an expired timestamp.
   *
   * @param projectNumber - firebase project number
   * @param modelName - model name
   * @return - updated model with new download url and expiry time
   */
  @NonNull
  public Task<CustomModel> getNewDownloadUrlWithExpiry(String projectNumber, String modelName) {
    return getCustomModelDetails(projectNumber, modelName, null);
  }

  /**
   * Gets the download details for the custom model, returns task with null result if the current
   * model is the latest.
   *
   * @param projectNumber - firebase project number
   * @param modelName - model name
   * @param modelHash - current model hash - input empty string if no current download exists or to
   *     force retrieval of a new download url
   * @return The download details for the model or null if the current model hash matches the latest
   *     model.
   */
  @NonNull
  public Task<CustomModel> getCustomModelDetails(
      String projectNumber, String modelName, String modelHash) {
    try {

      if (TextUtils.isEmpty(modelName))
        throw new FirebaseMlException(
            "Error cannot retrieve model from reading an empty modelName",
            FirebaseMlException.INVALID_ARGUMENT);

      Task<InstallationTokenResult> installationAuthTokenTask =
          firebaseInstallations.get().getToken(false);
      return installationAuthTokenTask.continueWithTask(
          blockingExecutor,
          (CustomModelTask) -> {
            try {
              URL url =
                  new URL(
                      String.format(DOWNLOAD_MODEL_REGEX, downloadHost, projectNumber, modelName));
              HttpURLConnection connection = (HttpURLConnection) url.openConnection();
              connection.setConnectTimeout(CONNECTION_TIME_OUT_MS);
              connection.setRequestProperty(ACCEPT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);
              connection.setRequestProperty(CONTENT_TYPE, APPLICATION_JSON);
              if (modelHash != null && !modelHash.isEmpty()) {
                connection.setRequestProperty(IF_NONE_MATCH_HEADER_KEY, modelHash);
              }
              if (!installationAuthTokenTask.isSuccessful()) {
                ErrorCode errorCode = ErrorCode.MODEL_INFO_DOWNLOAD_CONNECTION_FAILED;
                String errorMessage = "Failed to get model due to authentication error";
                int exceptionCode = FirebaseMlException.UNAUTHENTICATED;
                if (installationAuthTokenTask.getException() != null
                    && (installationAuthTokenTask.getException() instanceof UnknownHostException
                        || installationAuthTokenTask.getException().getCause()
                            instanceof UnknownHostException)) {
                  errorCode = ErrorCode.NO_NETWORK_CONNECTION;
                  errorMessage = "Failed to retrieve model info due to no internet connection.";
                  exceptionCode = FirebaseMlException.NO_NETWORK_CONNECTION;
                }
                eventLogger.logDownloadFailureWithReason(
                    modelFactory.create(modelName, modelHash != null ? modelHash : "", 0, 0L),
                    false,
                    errorCode.getValue());
                return Tasks.forException(new FirebaseMlException(errorMessage, exceptionCode));
              }

              connection.setRequestProperty(
                  INSTALLATIONS_AUTH_TOKEN_HEADER,
                  installationAuthTokenTask.getResult().getToken());
              connection.setRequestProperty(API_KEY_HEADER, apiKey);

              // Headers required for Android API Key Restrictions.
              connection.setRequestProperty(X_ANDROID_PACKAGE_HEADER, context.getPackageName());

              if (fingerprintHashForPackage != null) {
                connection.setRequestProperty(X_ANDROID_CERT_HEADER, fingerprintHashForPackage);
              }

              return fetchDownloadDetails(modelName, connection);
            } catch (IOException e) {
              eventLogger.logDownloadFailureWithReason(
                  modelFactory.create(modelName, modelHash, 0, 0L),
                  false,
                  ErrorCode.MODEL_INFO_DOWNLOAD_CONNECTION_FAILED.getValue());

              return Tasks.forException(
                  new FirebaseMlException(
                      "Error reading custom model from download service: " + e.getMessage(),
                      FirebaseMlException.INVALID_ARGUMENT));
            }
          });

    } catch (FirebaseMlException e) {
      return Tasks.forException(e);
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

  private Task<CustomModel> fetchDownloadDetails(String modelName, HttpURLConnection connection) {
    try {
      connection.connect();
      int httpResponseCode = connection.getResponseCode();
      String errorMessage = getErrorStream(connection);

      switch (httpResponseCode) {
        case HttpURLConnection.HTTP_OK:
          return readCustomModelResponse(modelName, connection);
        case HttpURLConnection.HTTP_NOT_MODIFIED:
          return Tasks.forResult(null);
        case HttpURLConnection.HTTP_NOT_FOUND:
          return Tasks.forException(
              new FirebaseMlException(
                  String.format(Locale.getDefault(), "No model found with name: %s", modelName),
                  FirebaseMlException.NOT_FOUND));
        case HttpURLConnection.HTTP_BAD_REQUEST:
          return setAndLogException(
              modelName,
              httpResponseCode,
              String.format(
                  Locale.getDefault(),
                  "Bad http request for model (%s): %s",
                  modelName,
                  errorMessage),
              FirebaseMlException.INVALID_ARGUMENT);
        case 429: // too many requests
          return setAndLogException(
              modelName,
              httpResponseCode,
              String.format(
                  Locale.getDefault(),
                  "Too many requests to server please wait before trying again: %s",
                  errorMessage),
              FirebaseMlException.RESOURCE_EXHAUSTED);
        case HttpURLConnection.HTTP_INTERNAL_ERROR:
          return setAndLogException(
              modelName,
              httpResponseCode,
              String.format(
                  Locale.getDefault(),
                  "Server issue while fetching model (%s): %s",
                  modelName,
                  errorMessage),
              FirebaseMlException.INTERNAL);
        case HttpURLConnection.HTTP_UNAUTHORIZED:
        case HttpURLConnection.HTTP_FORBIDDEN:
          return setAndLogException(
              modelName,
              httpResponseCode,
              String.format(
                  Locale.getDefault(),
                  "Permission error while fetching model (%s): %s",
                  modelName,
                  errorMessage),
              FirebaseMlException.PERMISSION_DENIED);
        default:
          return setAndLogException(
              modelName,
              httpResponseCode,
              String.format(
                  Locale.getDefault(),
                  "Failed to connect to Firebase ML download server: %s",
                  errorMessage),
              FirebaseMlException.INTERNAL);
      }
    } catch (IOException e) {
      ErrorCode errorCode = ErrorCode.MODEL_INFO_DOWNLOAD_CONNECTION_FAILED;
      String errorMessage = "Failed to get model URL";
      int exceptionCode = FirebaseMlException.INTERNAL;
      if (e instanceof UnknownHostException) {
        errorCode = ErrorCode.NO_NETWORK_CONNECTION;
        errorMessage = "Failed to retrieve model info due to no internet connection.";
        exceptionCode = FirebaseMlException.NO_NETWORK_CONNECTION;
      }
      eventLogger.logModelInfoRetrieverFailure(modelFactory.create(modelName, "", 0, 0), errorCode);
      return Tasks.forException(new FirebaseMlException(errorMessage, exceptionCode));
    }
  }

  private Task<CustomModel> setAndLogException(
      String modelName, int httpResponseCode, String errorMessage, @Code int invalidArgument) {
    eventLogger.logModelInfoRetrieverFailure(
        modelFactory.create(modelName, "", 0, 0),
        ErrorCode.MODEL_INFO_DOWNLOAD_UNSUCCESSFUL_HTTP_STATUS,
        httpResponseCode);
    return Tasks.forException(new FirebaseMlException(errorMessage, invalidArgument));
  }

  private Task<CustomModel> readCustomModelResponse(
      @NonNull String modelName, HttpURLConnection connection) throws IOException {
    String encodingKey = connection.getHeaderField(CONTENT_ENCODING_HEADER_KEY);
    InputStream inputStream = maybeUnGzip(connection.getInputStream(), encodingKey);
    JsonReader reader = new JsonReader(new InputStreamReader(inputStream, UTF_8));
    long fileSize = 0L;
    String downloadUrl = "";
    long expireTime = 0L;

    String modelHash = maybeUnGzipHeader(connection.getHeaderField(ETAG_HEADER), encodingKey);

    if (modelHash == null || modelHash.isEmpty()) {
      eventLogger.logDownloadFailureWithReason(
          modelFactory.create(modelName, modelHash, 0, 0L),
          false,
          ErrorCode.MODEL_INFO_DOWNLOAD_CONNECTION_FAILED.getValue());
      return Tasks.forException(
          new FirebaseMlException(
              "Model hash not set in download response.", FirebaseMlException.INTERNAL));
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
      } else if (name.equals("modelFormat")) {
        String modelFormat = reader.nextString();
        if (modelFormat.equals("MODEL_FORMAT_UNSPECIFIED")) {
          // log error but continue... this shouldn't happen
          Log.w(TAG, "Ignoring unexpected model type: " + modelFormat);
        }
      } else {
        reader.skipValue();
      }
    }
    reader.endObject();
    reader.close();
    inputStream.close();

    if (!downloadUrl.isEmpty() && expireTime > 0L) {
      CustomModel model =
          modelFactory.create(modelName, modelHash, fileSize, downloadUrl, expireTime);
      eventLogger.logModelInfoRetrieverSuccess(model);
      return Tasks.forResult(model);
    }
    eventLogger.logDownloadFailureWithReason(
        modelFactory.create(modelName, modelHash, 0, 0L),
        false,
        ErrorCode.MODEL_INFO_DOWNLOAD_CONNECTION_FAILED.getValue());
    return Tasks.forException(
        new FirebaseMlException(
            "Model info could not be extracted from download response.",
            FirebaseMlException.INTERNAL));
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

  private String getErrorStreamString(HttpURLConnection connection) {
    InputStream errorStream = connection.getErrorStream();
    if (errorStream == null) {
      return null;
    }
    String encodingKey = connection.getHeaderField(CONTENT_ENCODING_HEADER_KEY);
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(maybeUnGzip(errorStream, encodingKey), UTF_8))) {
      StringBuilder response = new StringBuilder();
      for (String input = reader.readLine(); input != null; input = reader.readLine()) {
        response.append(input).append('\n');
      }
      return response.toString();
    } catch (IOException ex) {
      Log.d(TAG, "Error extracting errorStream from failed connection attempt", ex);
      return null;
    }
  }

  private String getErrorStream(HttpURLConnection connection) {
    String errorStreamString = getErrorStreamString(connection);
    if (errorStreamString != null) {
      try {
        JSONObject responseData = new JSONObject(errorStreamString);
        JSONObject responseError = responseData.getJSONObject(ERROR_RESPONSE_ERROR);
        if (responseError != null && responseError.has(ERROR_RESPONSE_MESSAGE)) {
          errorStreamString = responseError.getString(ERROR_RESPONSE_MESSAGE);

          return String.format(
              Locale.ENGLISH,
              "HTTP response from Firebase Download Service: [%d - %s: %s]",
              connection.getResponseCode(),
              connection.getResponseMessage(),
              errorStreamString);
        }
      } catch (Exception ex) {
        Log.d(TAG, "Error extracting errorStream from failed connection attempt", ex);
      }
    }
    return errorStreamString;
  }

  /** Gets the Android package's SHA-1 fingerprint. */
  @Nullable
  private static String getFingerprintHashForPackage(Context context) {
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
