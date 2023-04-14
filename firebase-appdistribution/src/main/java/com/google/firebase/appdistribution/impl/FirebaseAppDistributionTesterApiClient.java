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

package com.google.firebase.appdistribution.impl;

import static com.google.firebase.appdistribution.impl.TaskUtils.runAsyncInTask;

import android.net.Uri;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.appdistribution.BinaryType;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Client that makes requests to the App Distribution Tester API. */
class FirebaseAppDistributionTesterApiClient {
  private static final String TAG = "TesterApiClient";

  /** A potentially long-running job that requires a FID and a FIS auth token */
  private interface FidDependentJob<TResult> {
    TResult run(String fid, String token) throws FirebaseAppDistributionException;
  }

  private static final String FIND_RELEASE_APK_HASH_PARAM = "apkHash";
  private static final String FIND_RELEASE_IAS_ARTIFACT_ID_PARAM = "iasArtifactId";

  private static final String BUILD_VERSION_JSON_KEY = "buildVersion";
  private static final String DISPLAY_VERSION_JSON_KEY = "displayVersion";
  private static final String RELEASE_NOTES_JSON_KEY = "releaseNotes";
  private static final String BINARY_TYPE_JSON_KEY = "binaryType";
  private static final String CODE_HASH_KEY = "codeHash";
  private static final String APK_HASH_KEY = "apkHash";
  private static final String IAS_ARTIFACT_ID_KEY = "iasArtifactId";
  private static final String DOWNLOAD_URL_KEY = "downloadUrl";

  private static final String FETCH_NEW_RELEASE_TAG = "Fetching new release";
  private static final String FIND_RELEASE_TAG = "Finding installed release";
  private static final String CREATE_FEEDBACK_TAG = "Creating feedback";
  private static final String COMMIT_FEEDBACK_TAG = "Committing feedback";
  private static final String UPLOAD_SCREENSHOT_TAG = "Uploading screenshot";
  private static final String X_APP_DISTRO_FEEDBACK_TRIGGER = "X-APP-DISTRO-FEEDBACK-TRIGGER";

  private final FirebaseOptions firebaseOptions;
  private final Provider<FirebaseInstallationsApi> firebaseInstallationsApiProvider;
  private final TesterApiHttpClient testerApiHttpClient;
  private final Executor blockingExecutor;

  @Inject
  FirebaseAppDistributionTesterApiClient(
      @NonNull FirebaseOptions firebaseOptions,
      @NonNull Provider<FirebaseInstallationsApi> firebaseInstallationsApiProvider,
      @NonNull TesterApiHttpClient testerApiHttpClient,
      @NonNull @Blocking Executor blockingExecutor) {
    this.firebaseOptions = firebaseOptions;
    this.firebaseInstallationsApiProvider = firebaseInstallationsApiProvider;
    this.testerApiHttpClient = testerApiHttpClient;
    this.blockingExecutor = blockingExecutor;
  }

  /**
   * Fetches and returns a {@link Task} that will complete with the latest release for the app that
   * the tester has access to, or {@code null} if the tester doesn't have access to any releases.
   */
  @NonNull
  Task<AppDistributionReleaseInternal> fetchNewRelease() {
    return runWithFidAndToken(
        (fid, token) -> {
          String path =
              String.format(
                  "v1alpha/devices/-/testerApps/%s/installations/%s/releases",
                  firebaseOptions.getApplicationId(), fid);
          JSONObject responseBody =
              testerApiHttpClient.makeGetRequest(FETCH_NEW_RELEASE_TAG, path, token);
          return parseNewRelease(responseBody);
        });
  }

  /**
   * Fetches and returns the name of the installed release given the hash of the installed APK.
   *
   * <p>The returned {@link Task} will fail with {@link Status#AUTHENTICATION_FAILURE} if the tester
   * does not have access to the release, or if it doesn't exist.
   */
  @NonNull
  Task<String> findReleaseUsingApkHash(String apkHash) {
    return runWithFidAndToken(
        (fid, token) -> findRelease(fid, token, FIND_RELEASE_APK_HASH_PARAM, apkHash));
  }

  /**
   * Fetches and returns the name of the installed release given the IAS artifact ID of the
   * installed app bundle.
   *
   * <p>The returned {@link Task} will fail with {@link Status#AUTHENTICATION_FAILURE} if the tester
   * does not have access to the release, or if it doesn't exist.
   */
  @NonNull
  Task<String> findReleaseUsingIasArtifactId(String iasArtifactId) {
    return runWithFidAndToken(
        (fid, token) -> findRelease(fid, token, FIND_RELEASE_IAS_ARTIFACT_ID_PARAM, iasArtifactId));
  }

  private String findRelease(String fid, String token, String binaryIdParam, String binaryIdValue)
      throws FirebaseAppDistributionException {
    String path =
        String.format(
            "v1alpha/projects/%s/installations/%s/releases:find?%s=%s",
            firebaseOptions.getGcmSenderId(), // Project number
            fid,
            binaryIdParam,
            binaryIdValue);
    JSONObject responseBody = testerApiHttpClient.makeGetRequest(FIND_RELEASE_TAG, path, token);
    return parseJsonFieldFromResponse(responseBody, "release");
  }

  /** Creates a new feedback from the given text, and returns the feedback name. */
  @NonNull
  Task<String> createFeedback(String testerReleaseName, String feedbackText) {
    return runWithFidAndToken(
        (unused, token) -> {
          LogWrapper.i(TAG, "Creating feedback for release: " + testerReleaseName);
          String path = String.format("v1alpha/%s/feedbackReports", testerReleaseName);
          String requestBody = buildCreateFeedbackBody(feedbackText).toString();
          JSONObject responseBody =
              testerApiHttpClient.makeJsonPostRequest(
                  CREATE_FEEDBACK_TAG, path, token, requestBody);
          return parseJsonFieldFromResponse(responseBody, "name");
        });
  }

  /**
   * Attaches a screenshot to feedback.
   *
   * @return a {@link Task} containing the feedback name, for convenience when chaining subsequent
   *     requests off of this task
   */
  Task<String> attachScreenshot(
      String feedbackName, Uri screenshotUri, String filename, String contentType) {
    return runWithFidAndToken(
        (unused, token) -> {
          LogWrapper.i(TAG, "Uploading screenshot for feedback: " + feedbackName);
          String path =
              String.format("upload/v1alpha/%s:uploadArtifact?type=SCREENSHOT", feedbackName);
          testerApiHttpClient.makeUploadRequest(
              UPLOAD_SCREENSHOT_TAG, path, token, filename, contentType, screenshotUri);
          return feedbackName;
        });
  }

  /** Commits the feedback with the given name. */
  @NonNull
  Task<Void> commitFeedback(String feedbackName, FeedbackTrigger trigger) {
    return runWithFidAndToken(
        (unused, token) -> {
          LogWrapper.i(TAG, "Committing feedback: " + feedbackName);
          String path = "v1alpha/" + feedbackName + ":commit";
          Map<String, String> extraHeaders = new HashMap<>();
          extraHeaders.put(X_APP_DISTRO_FEEDBACK_TRIGGER, trigger.toString());
          testerApiHttpClient.makeJsonPostRequest(
              COMMIT_FEEDBACK_TAG, path, token, /* requestBody= */ "", extraHeaders);
          return null;
        });
  }

  private static JSONObject buildCreateFeedbackBody(String feedbackText)
      throws FirebaseAppDistributionException {
    JSONObject feedbackJsonObject = new JSONObject();
    try {
      feedbackJsonObject.put("text", feedbackText);
    } catch (JSONException e) {
      throw new FirebaseAppDistributionException(
          ErrorMessages.JSON_SERIALIZATION_ERROR, Status.UNKNOWN, e);
    }
    return feedbackJsonObject;
  }

  private AppDistributionReleaseInternal parseNewRelease(JSONObject responseJson)
      throws FirebaseAppDistributionException {
    if (!responseJson.has("releases")) {
      return null;
    }
    try {
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

      LogWrapper.v(TAG, "Zip hash for the new release " + newRelease.getApkHash());
      return newRelease;
    } catch (JSONException e) {
      LogWrapper.e(TAG, "Error parsing the new release.", e);
      throw new FirebaseAppDistributionException(
          ErrorMessages.JSON_PARSING_ERROR, Status.UNKNOWN, e);
    }
  }

  private String parseJsonFieldFromResponse(JSONObject responseJson, String fieldName)
      throws FirebaseAppDistributionException {
    try {
      return responseJson.getString(fieldName);
    } catch (JSONException e) {
      LogWrapper.e(TAG, String.format("Field '%s' missing from response", fieldName), e);
      throw new FirebaseAppDistributionException(
          ErrorMessages.JSON_PARSING_ERROR, Status.UNKNOWN, e);
    }
  }

  private String tryGetValue(JSONObject jsonObject, String key) {
    try {
      return jsonObject.getString(key);
    } catch (JSONException e) {
      return "";
    }
  }

  /**
   * Asynchronously runs a potentially long-running job that depends on a FID and a FIS auth token.
   */
  private <TResult> Task<TResult> runWithFidAndToken(FidDependentJob<TResult> job) {
    Task<String> installationIdTask = firebaseInstallationsApiProvider.get().getId();
    // forceRefresh is false to get locally cached token if available
    Task<InstallationTokenResult> installationAuthTokenTask =
        firebaseInstallationsApiProvider.get().getToken(/* forceRefresh= */ false);

    return Tasks.whenAllSuccess(installationIdTask, installationAuthTokenTask)
        .continueWithTask(blockingExecutor, TaskUtils::handleTaskFailure)
        .onSuccessTask(
            blockingExecutor,
            resultsList -> {
              // Tasks.whenAllSuccess combines task results into a list
              if (resultsList.size() != 2) {
                throw new FirebaseAppDistributionException(
                    "Expected 2 task results, got " + resultsList.size(), Status.UNKNOWN);
              }
              String fid = (String) resultsList.get(0);
              InstallationTokenResult tokenResult = (InstallationTokenResult) resultsList.get(1);
              return runAsyncInTask(blockingExecutor, () -> job.run(fid, tokenResult.getToken()));
            });
  }
}
