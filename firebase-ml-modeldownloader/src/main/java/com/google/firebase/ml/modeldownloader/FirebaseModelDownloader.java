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
package com.google.firebase.ml.modeldownloader;

import android.os.Build.VERSION_CODES;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.ml.modeldownloader.internal.CustomModelDownloadService;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.DownloadStatus;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ErrorCode;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogger;
import com.google.firebase.ml.modeldownloader.internal.ModelFileDownloadService;
import com.google.firebase.ml.modeldownloader.internal.ModelFileManager;
import com.google.firebase.ml.modeldownloader.internal.SharedPreferencesUtil;
import java.io.File;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FirebaseModelDownloader {

  private static final String TAG = "FirebaseModelDownld";
  private final FirebaseOptions firebaseOptions;
  private final SharedPreferencesUtil sharedPreferencesUtil;
  private final ModelFileDownloadService fileDownloadService;
  private final ModelFileManager fileManager;
  private final CustomModelDownloadService modelDownloadService;
  private final Executor executor;

  private final FirebaseMlLogger eventLogger;

  @RequiresApi(api = VERSION_CODES.KITKAT)
  FirebaseModelDownloader(
      FirebaseApp firebaseApp, FirebaseInstallationsApi firebaseInstallationsApi) {
    this.firebaseOptions = firebaseApp.getOptions();
    this.sharedPreferencesUtil = new SharedPreferencesUtil(firebaseApp);
    this.eventLogger = FirebaseMlLogger.getInstance();
    this.fileDownloadService = new ModelFileDownloadService(firebaseApp);
    this.modelDownloadService =
        new CustomModelDownloadService(firebaseApp, firebaseInstallationsApi);

    this.executor = Executors.newSingleThreadExecutor();
    fileManager = ModelFileManager.getInstance();
  }

  @VisibleForTesting
  FirebaseModelDownloader(
      FirebaseOptions firebaseOptions,
      SharedPreferencesUtil sharedPreferencesUtil,
      ModelFileDownloadService fileDownloadService,
      CustomModelDownloadService modelDownloadService,
      ModelFileManager fileManager,
      FirebaseMlLogger eventLogger,
      Executor executor) {
    this.firebaseOptions = firebaseOptions;
    this.sharedPreferencesUtil = sharedPreferencesUtil;
    this.fileDownloadService = fileDownloadService;
    this.modelDownloadService = modelDownloadService;
    this.fileManager = fileManager;
    this.eventLogger = eventLogger;
    this.executor = executor;
  }

  /**
   * Returns the {@link FirebaseModelDownloader} initialized with the default {@link FirebaseApp}.
   *
   * @return a {@link FirebaseModelDownloader} instance
   */
  @NonNull
  public static FirebaseModelDownloader getInstance() {
    FirebaseApp defaultFirebaseApp = FirebaseApp.getInstance();
    return getInstance(defaultFirebaseApp);
  }

  /**
   * Returns the {@link FirebaseModelDownloader} initialized with a custom {@link FirebaseApp}.
   *
   * @param app a custom {@link FirebaseApp}
   * @return a {@link FirebaseModelDownloader} instance
   */
  @NonNull
  public static FirebaseModelDownloader getInstance(@NonNull FirebaseApp app) {
    Preconditions.checkArgument(app != null, "Null is not a valid value of FirebaseApp.");
    return app.get(FirebaseModelDownloader.class);
  }

  /**
   * Get the downloaded model file based on download type and conditions. DownloadType behaviours:
   *
   * <ul>
   *   <li>{@link DownloadType#LOCAL_MODEL}: returns the current model if present, otherwise
   *       triggers new download (or finds one in progress) and only completes when download is
   *       finished
   *   <li>{@link DownloadType#LOCAL_MODEL_UPDATE_IN_BACKGROUND}: returns the current model if
   *       present and triggers an update to fetch a new version in the background. If no local
   *       model is present triggers a new download (or finds one in progress) and only completes
   *       when download is finished.
   *   <li>{@link DownloadType#LATEST_MODEL}: check for latest model, if different from local model,
   *       trigger new download, task only completes when download finishes
   * </ul>
   *
   * @param modelName - model name
   * @param downloadType - download type
   * @param conditions - download conditions
   * @return Custom model
   */
  @NonNull
  public Task<CustomModel> getModel(
      @NonNull String modelName,
      @NonNull DownloadType downloadType,
      @Nullable CustomModelDownloadConditions conditions) {
    CustomModel localModelDetails = getLocalModelDetails(modelName);
    if (localModelDetails == null) {
      // no local model - get latest.
      return getCustomModelTask(modelName, conditions);
    }

    switch (downloadType) {
      case LOCAL_MODEL:
        return getCompletedLocalCustomModelTask(localModelDetails);
      case LATEST_MODEL:
        // check for latest model, wait for download if newer model exists
        return getCustomModelTask(modelName, conditions, localModelDetails.getModelHash());
      case LOCAL_MODEL_UPDATE_IN_BACKGROUND:
        // start download in background, if newer model exists
        getCustomModelTask(modelName, conditions, localModelDetails.getModelHash());
        return getCompletedLocalCustomModelTask(localModelDetails);
    }
    return Tasks.forException(
        new FirebaseMlException(
            "Unsupported downloadType, please chose LOCAL_MODEL, LATEST_MODEL, or LOCAL_MODEL_UPDATE_IN_BACKGROUND",
            FirebaseMlException.INVALID_ARGUMENT));
  }

  /**
   * Checks the local model, if a completed download exists - returns this model. Else if a download
   * is in progress returns the downloading model version. Otherwise, this model is in a bad state -
   * clears the model and return null
   *
   * @param modelName - name of the model
   * @return the local model with file downloaded details or null if no local model.
   */
  @Nullable
  private CustomModel getLocalModelDetails(@NonNull String modelName) {
    CustomModel localModel = sharedPreferencesUtil.getCustomModelDetails(modelName);
    if (localModel == null) {
      return null;
    }

    // valid model file exists when local file path is set
    if (localModel.getLocalFilePath() != null && localModel.isModelFilePresent()) {
      return localModel;
    }

    // download is in progress - return downloading model details
    if (localModel.getDownloadId() != 0) {
      return sharedPreferencesUtil.getDownloadingCustomModelDetails(modelName);
    }

    // bad model state - delete all existing details and return null
    deleteModelDetails(localModel.getName());
    return null;
  }

  // Given a model, if the local file path is present, return model.
  // Else if there is a file download is in progress, returns the download task.
  // Otherwise reset model and return null - this should not happen.
  private Task<CustomModel> getCompletedLocalCustomModelTask(@NonNull CustomModel model) {
    // model file exists - use this
    if (model.isModelFilePresent()) {
      return Tasks.forResult(model);
    }

    // download in progress - return the downloading task.
    if (model.getDownloadId() != 0) {

      // download in progress - find existing download task and wait for it to complete.
      Task<Void> downloadInProgressTask =
          fileDownloadService.getExistingDownloadTask(model.getDownloadId());

      if (downloadInProgressTask != null) {
        return downloadInProgressTask.continueWithTask(
            executor,
            downloadTask -> {
              if (downloadTask.isSuccessful()) {
                return finishModelDownload(model.getName());
              } else if (downloadTask.getException() instanceof FirebaseMlException) {
                return Tasks.forException((FirebaseMlException) downloadTask.getException());
              }
              return Tasks.forException(
                  new FirebaseMlException(
                      "Model download failed for " + model.getName(),
                      FirebaseMlException.INTERNAL));
            });
      }

      // maybe download just completed - fetch latest model to check.
      CustomModel latestModel = sharedPreferencesUtil.getCustomModelDetails(model.getName());
      if (latestModel != null && latestModel.isModelFilePresent()) {
        return Tasks.forResult(latestModel);
      }
    }

    // bad model state - delete all existing model details and return exception
    return deleteDownloadedModel(model.getName())
        .continueWithTask(
            executor,
            deletionTask ->
                Tasks.forException(
                    new FirebaseMlException(
                        "Model download in bad state - please retry",
                        FirebaseMlException.INTERNAL)));
  }

  // This version of getCustomModelTask will always call the modelDownloadService and upon
  // success will then trigger file download.
  private Task<CustomModel> getCustomModelTask(
      @NonNull String modelName, @Nullable CustomModelDownloadConditions conditions) {
    return getCustomModelTask(modelName, conditions, null);
  }

  // This version of getCustomModelTask will call the modelDownloadService and upon
  // success will only trigger file download, if there is a new model hash value.
  private Task<CustomModel> getCustomModelTask(
      @NonNull String modelName,
      @Nullable CustomModelDownloadConditions conditions,
      @Nullable String modelHash) {
    CustomModel currentModel = sharedPreferencesUtil.getCustomModelDetails(modelName);

    if (currentModel == null && modelHash != null) {
      Log.d(TAG, "Model hash provided but no current model; triggering fresh download.");
      modelHash = null;
    }

    Task<CustomModel> incomingModelDetails =
        modelDownloadService.getCustomModelDetails(
            firebaseOptions.getProjectId(), modelName, modelHash);

    return incomingModelDetails.continueWithTask(
        executor,
        incomingModelDetailTask -> {
          if (incomingModelDetailTask.isSuccessful()) {
            // null means we have the latest model or we failed to connect.
            if (incomingModelDetailTask.getResult() == null) {
              if (currentModel != null) {
                return getCompletedLocalCustomModelTask(currentModel);
              }
              // double check due to timing.
              CustomModel updatedModel = sharedPreferencesUtil.getCustomModelDetails(modelName);
              if (updatedModel != null) {
                return getCompletedLocalCustomModelTask(updatedModel);
              }
              // clean up model internally
              deleteModelDetails(modelName);
              return Tasks.forException(
                  new FirebaseMlException(
                      "Possible caching issues: no model associated with " + modelName + ".",
                      FirebaseMlException.INTERNAL));
            }

            // if modelHash matches current local model just return local model.
            // Should be handled by above case but just in case.
            if (currentModel != null) {
              // is this the same model?
              if (currentModel
                      .getModelHash()
                      .equals(incomingModelDetails.getResult().getModelHash())
                  && currentModel.getLocalFilePath() != null
                  && !currentModel.getLocalFilePath().isEmpty()
                  && new File(currentModel.getLocalFilePath()).exists()) {
                return getCompletedLocalCustomModelTask(currentModel);
              }

              // update is available
              if (!currentModel
                  .getModelHash()
                  .equals(incomingModelDetails.getResult().getModelHash())) {
                eventLogger.logDownloadEventWithErrorCode(
                    incomingModelDetails.getResult(),
                    false,
                    DownloadStatus.UPDATE_AVAILABLE,
                    ErrorCode.NO_ERROR);
              }

              // is download already in progress for this hash?
              if (currentModel.getDownloadId() != 0) {
                CustomModel downloadingModel =
                    sharedPreferencesUtil.getDownloadingCustomModelDetails(modelName);
                if (downloadingModel != null) {
                  if (downloadingModel
                      .getModelHash()
                      .equals(incomingModelDetails.getResult().getModelHash())) {
                    return Tasks.forResult(downloadingModel);
                  }
                  Log.d(
                      TAG, "Hash does not match with expected: " + downloadingModel.getModelHash());
                  // Note we log "DownloadStatus.SUCCEEDED" because the model file's download itself
                  // succeeded. Just the hash validation failed.
                  eventLogger.logDownloadEventWithErrorCode(
                      downloadingModel,
                      true,
                      DownloadStatus.SUCCEEDED,
                      ErrorCode.MODEL_HASH_MISMATCH);
                  return Tasks.forException(
                      new FirebaseMlException(
                          "Hash does not match with expected",
                          FirebaseMlException.MODEL_HASH_MISMATCH));
                }
                Log.d(TAG, "Download details missing for model");
                // Note we log "DownloadStatus.SUCCEEDED" because the model file's download itself
                // succeeded. Just the file copy failed.
                eventLogger.logDownloadEventWithErrorCode(
                    downloadingModel, true, DownloadStatus.SUCCEEDED, ErrorCode.DOWNLOAD_FAILED);
                return Tasks.forException(
                    new FirebaseMlException(
                        "Download details missing for model", FirebaseMlException.INTERNAL));
              }
            }

            // start download
            return fileDownloadService
                .download(incomingModelDetailTask.getResult(), conditions)
                .continueWithTask(
                    executor,
                    downloadTask -> {
                      if (downloadTask.isSuccessful()) {
                        return finishModelDownload(modelName);
                      } else {
                        return retryExpiredUrlDownload(modelName, conditions, downloadTask, 2);
                      }
                    });
          }
          return Tasks.forException(incomingModelDetailTask.getException());
        });
  }

  private Task<CustomModel> retryExpiredUrlDownload(
      @NonNull String modelName,
      @Nullable CustomModelDownloadConditions conditions,
      Task<Void> downloadTask,
      int retryCounter) {
    if (downloadTask.getException() instanceof FirebaseMlException
        && ((FirebaseMlException) downloadTask.getException()).getCode()
            == FirebaseMlException.DOWNLOAD_URL_EXPIRED) {
      // this is likely an expired url - retry.
      Task<CustomModel> retryModelDetails =
          modelDownloadService.getNewDownloadUrlWithExpiry(
              firebaseOptions.getProjectId(), modelName);
      // no local model - start download.
      return retryModelDetails.continueWithTask(
          executor,
          retryModelDetailTask -> {
            if (retryModelDetailTask.isSuccessful()) {
              // start download
              return fileDownloadService
                  .download(retryModelDetailTask.getResult(), conditions)
                  .continueWithTask(
                      executor,
                      retryDownloadTask -> {
                        if (retryDownloadTask.isSuccessful()) {
                          return finishModelDownload(modelName);
                        }
                        if (retryCounter > 1) {
                          return retryExpiredUrlDownload(
                              modelName, conditions, downloadTask, retryCounter - 1);
                        }
                        return Tasks.forException(
                            new FirebaseMlException(
                                "File download failed after multiple attempts, possible expired url.",
                                FirebaseMlException.DOWNLOAD_URL_EXPIRED));
                      });
            }
            return Tasks.forException(retryModelDetailTask.getException());
          });
    } else if (downloadTask.getException() instanceof FirebaseMlException) {
      return Tasks.forException(downloadTask.getException());
    }
    return Tasks.forException(
        new FirebaseMlException("File download failed.", FirebaseMlException.INTERNAL));
  }

  private Task<CustomModel> finishModelDownload(@NonNull String modelName) {
    // read the updated model
    CustomModel downloadedModel = sharedPreferencesUtil.getDownloadingCustomModelDetails(modelName);
    if (downloadedModel == null) {
      // check if latest download completed - if so use current.
      downloadedModel = sharedPreferencesUtil.getCustomModelDetails(modelName);
      if (downloadedModel == null) {
        return Tasks.forException(
            new FirebaseMlException(
                "File for model, "
                    + modelName
                    + ", expected and not found during download completion.",
                FirebaseMlException.INTERNAL));
      }
    }
    // trigger the file to be moved to permanent location.
    fileDownloadService.loadNewlyDownloadedModelFile(downloadedModel);
    downloadedModel = sharedPreferencesUtil.getCustomModelDetails(modelName);
    return Tasks.forResult(downloadedModel);
  }

  /**
   * Triggers the move to permanent storage of successful model downloads and lists all models
   * downloaded to device.
   *
   * @return The set of all models that are downloaded to this device, triggers completion of file
   *     moves for completed model downloads.
   */
  @NonNull
  public Task<Set<CustomModel>> listDownloadedModels() {
    // trigger completion of file moves for download files.
    fileDownloadService.maybeCheckDownloadingComplete();

    TaskCompletionSource<Set<CustomModel>> taskCompletionSource = new TaskCompletionSource<>();
    executor.execute(
        () -> taskCompletionSource.setResult(sharedPreferencesUtil.listDownloadedModels()));
    return taskCompletionSource.getTask();
  }

  /**
   * Delete old local models, when no longer in use.
   *
   * @param modelName - name of the model
   */
  @NonNull
  public Task<Void> deleteDownloadedModel(@NonNull String modelName) {

    TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();
    executor.execute(
        () -> {
          // remove all files associated with this model and then clean up model references.
          deleteModelDetails(modelName);
          taskCompletionSource.setResult(null);
        });
    return taskCompletionSource.getTask();
  }

  private void deleteModelDetails(@NonNull String modelName) {
    fileManager.deleteAllModels(modelName);
    sharedPreferencesUtil.clearModelDetails(modelName);
  }

  /**
   * Update the settings which allow logging to firelog.
   *
   * @param enabled - is statistics logging enabled
   */
  public void setStatsCollectionEnabled(boolean enabled) {
    sharedPreferencesUtil.setCustomModelStatsCollectionEnabled(enabled);
  }

  /**
   * Get the current models' download id (returns background download id when applicable). This id
   * can be used to create a progress bar to track file download progress.
   *
   * <p>If no model exists or there is no download in progress, return 0.
   *
   * <p>If 0 is returned immediately after starting a download via getModel, then
   *
   * <ul>
   *   <li>the enqueuing wasn't needed: the getModel task already completed and/or no background
   *       update.
   *   <li>the enqueuing hasn't completed: the download id hasn't generated yet - try again.
   * </ul>
   *
   * @param modelName - model name
   * @return id associated with Android Download Manager.
   */
  @NonNull
  public Task<Long> getModelDownloadId(
      @NonNull String modelName, @Nullable Task<CustomModel> getModelTask) {
    if (getModelTask == null) {
      CustomModel localModel = sharedPreferencesUtil.getDownloadingCustomModelDetails(modelName);
      if (localModel != null) {
        return Tasks.forResult(localModel.getDownloadId());
      }
      return Tasks.forResult(0L);
    }

    long downloadId = 0;
    while (downloadId == 0 && !getModelTask.isComplete()) {
      CustomModel localModel = sharedPreferencesUtil.getDownloadingCustomModelDetails(modelName);
      if (localModel != null) {
        downloadId = localModel.getDownloadId();
      }
    }

    return Tasks.forResult(downloadId);
  }

  /** Returns the nick name of the {@link FirebaseApp} of this {@link FirebaseModelDownloader} */
  @VisibleForTesting
  String getApplicationId() {
    return firebaseOptions.getApplicationId();
  }
}
