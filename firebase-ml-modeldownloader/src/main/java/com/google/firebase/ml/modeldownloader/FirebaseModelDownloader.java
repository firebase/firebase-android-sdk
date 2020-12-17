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
import com.google.firebase.ml.modeldownloader.internal.ModelFileDownloadService;
import com.google.firebase.ml.modeldownloader.internal.ModelFileManager;
import com.google.firebase.ml.modeldownloader.internal.SharedPreferencesUtil;
import java.io.File;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FirebaseModelDownloader {

  private final FirebaseOptions firebaseOptions;
  private final SharedPreferencesUtil sharedPreferencesUtil;
  private final ModelFileDownloadService fileDownloadService;
  private final ModelFileManager fileManager;
  private final CustomModelDownloadService modelDownloadService;
  private final Executor executor;

  @RequiresApi(api = VERSION_CODES.KITKAT)
  FirebaseModelDownloader(
      FirebaseApp firebaseApp, FirebaseInstallationsApi firebaseInstallationsApi) {
    this.firebaseOptions = firebaseApp.getOptions();
    this.fileDownloadService = new ModelFileDownloadService(firebaseApp);
    this.sharedPreferencesUtil = new SharedPreferencesUtil(firebaseApp);
    this.modelDownloadService =
        new CustomModelDownloadService(firebaseOptions, firebaseInstallationsApi);
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
      Executor executor) {
    this.firebaseOptions = firebaseOptions;
    this.sharedPreferencesUtil = sharedPreferencesUtil;
    this.fileDownloadService = fileDownloadService;
    this.modelDownloadService = modelDownloadService;
    this.fileManager = fileManager;
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
      @Nullable CustomModelDownloadConditions conditions)
      throws Exception {
    CustomModel localModel = sharedPreferencesUtil.getCustomModelDetails(modelName);
    if (localModel == null) {
      // no local model - get latest.
      return getCustomModelTask(modelName, conditions);
    }

    switch (downloadType) {
      case LOCAL_MODEL:
        return Tasks.forResult(localModel);
      case LATEST_MODEL:
        // check for latest model, wait for download if newer model exists
        return getCustomModelTask(modelName, conditions, localModel.getModelHash());
      case LOCAL_MODEL_UPDATE_IN_BACKGROUND:
        // start download in back ground, return local model
        getCustomModelTask(modelName, conditions, localModel.getModelHash());
        return Tasks.forResult(localModel);
    }
    throw new IllegalArgumentException(
        "Unsupported downloadType, please chose LOCAL_MODEL, LATEST_MODEL, or LOCAL_MODEL_UPDATE_IN_BACKGROUND");
  }

  // This version of getCustomModelTask will always call the modelDownloadService and upon
  // success will then trigger file download.
  private Task<CustomModel> getCustomModelTask(
      @NonNull String modelName, @Nullable CustomModelDownloadConditions conditions)
      throws Exception {
    return getCustomModelTask(modelName, conditions, null);
  }

  // This version of getCustomModelTask will call the modelDownloadService and upon
  // success will only trigger file download, if there is a new model hash value.
  private Task<CustomModel> getCustomModelTask(
      @NonNull String modelName,
      @Nullable CustomModelDownloadConditions conditions,
      @Nullable String modelHash)
      throws Exception {
    Task<CustomModel> incomingModelDetails =
        modelDownloadService.getCustomModelDetails(
            firebaseOptions.getProjectId(), modelName, modelHash);

    return incomingModelDetails.continueWithTask(
        executor,
        incomingModelDetailTask -> {
          if (incomingModelDetailTask.isSuccessful()) {
            CustomModel currentModel = sharedPreferencesUtil.getCustomModelDetails(modelName);
            // null means we have the latest model
            if (incomingModelDetailTask.getResult() == null) {
              return Tasks.forResult(currentModel);
            }

            // if modelHash matches current local model just return local model.
            // Should be handled by above case but just in case.
            if (currentModel != null
                && currentModel
                    .getModelHash()
                    .equals(incomingModelDetails.getResult().getModelHash())) {
              if (!currentModel.getLocalFilePath().isEmpty()
                  && new File(currentModel.getLocalFilePath()).exists()) {
                return Tasks.forResult(currentModel);
              }
              // todo(annzimmer) this shouldn't happen unless they are calling the sdk with multiple
              // sets of download types/conditions.
              //  this should be a download in progress - add appropriate handling.
            }

            // start download
            return fileDownloadService
                .download(incomingModelDetailTask.getResult(), conditions)
                .continueWithTask(
                    executor,
                    downloadTask -> {
                      if (downloadTask.isSuccessful()) {
                        // read the updated model
                        CustomModel downloadedModel =
                            sharedPreferencesUtil.getCustomModelDetails(modelName);
                        // trigger the file to be moved to permanent location.
                        fileDownloadService.loadNewlyDownloadedModelFile(downloadedModel);
                        return Tasks.forResult(downloadedModel);
                      }
                      return Tasks.forException(new Exception("File download failed."));
                    });
          }
          return Tasks.forException(incomingModelDetailTask.getException());
        });
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
    try {
      fileDownloadService.maybeCheckDownloadingComplete();
    } catch (Exception ex) {
      System.out.println("Error checking for in progress downloads: " + ex.getMessage());
    }

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
          fileManager.deleteAllModels(modelName);
          sharedPreferencesUtil.clearModelDetails(modelName);
          taskCompletionSource.setResult(null);
        });
    return taskCompletionSource.getTask();
  }

  /** Returns the nick name of the {@link FirebaseApp} of this {@link FirebaseModelDownloader} */
  @VisibleForTesting
  String getApplicationId() {
    return firebaseOptions.getApplicationId();
  }
}
