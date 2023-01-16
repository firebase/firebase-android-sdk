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
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Blocking;
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
import javax.inject.Inject;

public class FirebaseModelDownloader {

  private static final String TAG = "FirebaseModelDownld";
  private final FirebaseOptions firebaseOptions;
  private final SharedPreferencesUtil sharedPreferencesUtil;
  private final ModelFileDownloadService fileDownloadService;
  private final ModelFileManager fileManager;
  private final CustomModelDownloadService modelDownloadService;
  private final Executor bgExecutor;
  private final Executor blockingExecutor;

  private final FirebaseMlLogger eventLogger;
  private final CustomModel.Factory modelFactory;

  @Inject
  @VisibleForTesting
  @RequiresApi(api = VERSION_CODES.KITKAT)
  FirebaseModelDownloader(
      FirebaseOptions firebaseOptions,
      SharedPreferencesUtil sharedPreferencesUtil,
      ModelFileDownloadService fileDownloadService,
      CustomModelDownloadService modelDownloadService,
      ModelFileManager fileManager,
      FirebaseMlLogger eventLogger,
      @Background Executor bgExecutor,
      @Blocking Executor blockingExecutor,
      CustomModel.Factory modelFactory) {
    this.firebaseOptions = firebaseOptions;
    this.sharedPreferencesUtil = sharedPreferencesUtil;
    this.fileDownloadService = fileDownloadService;
    this.modelDownloadService = modelDownloadService;
    this.fileManager = fileManager;
    this.eventLogger = eventLogger;
    this.bgExecutor = bgExecutor;
    this.blockingExecutor = blockingExecutor;
    this.modelFactory = modelFactory;
  }

  /**
   * Returns the {@link FirebaseModelDownloader} initialized with the default {@link FirebaseApp}.
   *
   * @return A {@link FirebaseModelDownloader} instance
   */
  @NonNull
  public static FirebaseModelDownloader getInstance() {
    FirebaseApp defaultFirebaseApp = FirebaseApp.getInstance();
    return getInstance(defaultFirebaseApp);
  }

  /**
   * Returns the {@link FirebaseModelDownloader} initialized with a custom {@link FirebaseApp}.
   *
   * @param app A custom {@link FirebaseApp}
   * @return A {@link FirebaseModelDownloader} instance
   */
  @NonNull
  public static FirebaseModelDownloader getInstance(@NonNull FirebaseApp app) {
    Preconditions.checkArgument(app != null, "Null is not a valid value of FirebaseApp.");
    return app.get(FirebaseModelDownloader.class);
  }

  /**
   * Gets the downloaded model file based on download type and conditions. DownloadType behaviours:
   *
   * <ul>
   *   <li>{@link DownloadType#LOCAL_MODEL}: returns the current model if present, otherwise
   *       triggers new download (or finds one in progress) and only completes when download is
   *       finished
   *   <li>{@link DownloadType#LOCAL_MODEL_UPDATE_IN_BACKGROUND}: returns the current model if
   *       present and triggers an update to fetch a new version in the background. If no local
   *       model is present triggers a new download (or finds one in progress) and only completes
   *       when download is finished.
   *   <li>{@link DownloadType#LATEST_MODEL}: returns the latest model. Checks if latest model is
   *       different from local model. If the models are the same, returns the current model.
   *       Otherwise, triggers a new model download and returns when this download finishes.
   * </ul>
   *
   * Most common exceptions include:
   *
   * <ul>
   *   <li>{@link FirebaseMlException#NO_NETWORK_CONNECTION}: Error connecting to the network.
   *   <li>{@link FirebaseMlException#NOT_FOUND}: No model found with the given name.
   *   <li>{@link FirebaseMlException#NOT_ENOUGH_SPACE}: Not enough space on device to download
   *       model.
   *   <li>{@link FirebaseMlException#DOWNLOAD_URL_EXPIRED}: URL used to fetch model expired before
   *       model download completed. (This return is rare; these calls are retried internally before
   *       being raised.)
   * </ul>
   *
   * @param modelName Model name.
   * @param downloadType {@link DownloadType} to determine which model to return.
   * @param conditions {@link CustomModelDownloadConditions} to be used during file download.
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
   * @param modelName Name of the model.
   * @return The local model with file downloaded details or null if no local model.
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
            bgExecutor,
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
            bgExecutor,
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
        bgExecutor,
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
                    blockingExecutor,
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
    if (retryCounter <= 0) {
      return Tasks.forException(
          new FirebaseMlException(
              "File download failed after multiple attempts, possible expired url.",
              FirebaseMlException.DOWNLOAD_URL_EXPIRED));
    }
    if (downloadTask.getException() instanceof FirebaseMlException
        && ((FirebaseMlException) downloadTask.getException()).getCode()
            == FirebaseMlException.DOWNLOAD_URL_EXPIRED) {
      // this is likely an expired url - retry.
      Task<CustomModel> retryModelDetails =
          modelDownloadService.getNewDownloadUrlWithExpiry(
              firebaseOptions.getProjectId(), modelName);
      // no local model - start download.
      return retryModelDetails.continueWithTask(
          bgExecutor,
          retryModelDetailTask -> {
            if (retryModelDetailTask.isSuccessful()) {
              // start download
              return fileDownloadService
                  .download(retryModelDetailTask.getResult(), conditions)
                  .continueWithTask(
                      bgExecutor,
                      retryDownloadTask -> {
                        if (retryDownloadTask.isSuccessful()) {
                          return finishModelDownload(modelName);
                        }
                        return retryExpiredUrlDownload(
                            modelName, conditions, downloadTask, retryCounter - 1);
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
   * Lists all models downloaded to device.
   *
   * @return The set of all models that are downloaded to this device.
   */
  @NonNull
  public Task<Set<CustomModel>> listDownloadedModels() {
    // trigger completion of file moves for download files.
    fileDownloadService.maybeCheckDownloadingComplete();

    TaskCompletionSource<Set<CustomModel>> taskCompletionSource = new TaskCompletionSource<>();
    bgExecutor.execute(
        () -> taskCompletionSource.setResult(sharedPreferencesUtil.listDownloadedModels()));
    return taskCompletionSource.getTask();
  }

  /**
   * Deletes the local model. Removes any information and files associated with the model name.
   *
   * @param modelName Name of the model.
   */
  @NonNull
  public Task<Void> deleteDownloadedModel(@NonNull String modelName) {

    TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();
    bgExecutor.execute(
        () -> {
          // remove all files associated with this model and then clean up model references.
          boolean isSuccessful = deleteModelDetails(modelName);
          taskCompletionSource.setResult(null);
          eventLogger.logDeleteModel(isSuccessful);
        });
    return taskCompletionSource.getTask();
  }

  private boolean deleteModelDetails(@NonNull String modelName) {
    boolean isSuccessful = fileManager.deleteAllModels(modelName);
    sharedPreferencesUtil.clearModelDetails(modelName);
    return isSuccessful;
  }

  /**
   * Enables stats collection in Firebase ML ModelDownloader via Firelog. The stats include API
   * calls counts, errors, API call durations, options, etc. No personally identifiable information
   * is logged.
   *
   * <p>The setting is set by the initialization of <code>FirebaseApp</code>, and it is persistent
   * together with the app's private data. It means that if the user uninstalls the app or clears
   * all app data, the setting will be erased. The best practice is to set the flag in each
   * initialization.
   *
   * <p>By default, the logging matches the Firebase-wide data collection switch.
   *
   * @param enabled Turns the logging state on or off. To revert to using the Firebase-wide data
   *     collection switch, set this value to <code>null</code>.
   */
  public void setModelDownloaderCollectionEnabled(@Nullable Boolean enabled) {
    sharedPreferencesUtil.setCustomModelStatsCollectionEnabled(enabled);
  }

  /**
   * Gets the current model's download ID (returns background download ID when applicable). This ID
   * can be used to create a progress bar to track file download progress.
   *
   * <p>[Preferred] If getModelTask is not null, then this task returns when the download ID is not
   * 0 (download has been enqueued) or when the getModelTask completes (returning 0).
   *
   * <p>If getModelTask is null, then this task immediately returns the download ID of the model.
   * This will be 0 if the model doesn't exist, the model has completed downloading, or the download
   * hasn't been enqueued.
   *
   * @param modelName Model name.
   * @param getModelTask The most recent getModel task associated with the model name.
   * @return Download ID associated with Android <code>DownloadManager</code>.
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

  /** @hide */
  @VisibleForTesting
  public CustomModel.Factory getModelFactory() {
    return modelFactory;
  }
}
