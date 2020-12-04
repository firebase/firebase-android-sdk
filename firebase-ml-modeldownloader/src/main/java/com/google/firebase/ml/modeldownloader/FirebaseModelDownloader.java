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
import com.google.android.datatransport.TransportFactory;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.ml.modeldownloader.internal.CustomModelDownloadService;
import com.google.firebase.ml.modeldownloader.internal.ModelFileDownloadService;
import com.google.firebase.ml.modeldownloader.internal.SharedPreferencesUtil;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FirebaseModelDownloader {

  private final FirebaseOptions firebaseOptions;
  private final SharedPreferencesUtil sharedPreferencesUtil;
  private final ModelFileDownloadService fileDownloadService;
  private final CustomModelDownloadService modelDownloadService;
  private final Executor executor;
  private final TransportFactory transportFactory;

  @RequiresApi(api = VERSION_CODES.KITKAT)
  FirebaseModelDownloader(
      FirebaseApp firebaseApp,
      FirebaseInstallationsApi firebaseInstallationsApi,
      TransportFactory transportFactory) {
    this.firebaseOptions = firebaseApp.getOptions();
    this.fileDownloadService = new ModelFileDownloadService(firebaseApp);
    this.sharedPreferencesUtil = new SharedPreferencesUtil(firebaseApp);
    this.modelDownloadService =
        new CustomModelDownloadService(firebaseOptions, firebaseInstallationsApi);
    this.executor = Executors.newCachedThreadPool();
    this.transportFactory = transportFactory;
  }

  @VisibleForTesting
  FirebaseModelDownloader(
      FirebaseOptions firebaseOptions,
      SharedPreferencesUtil sharedPreferencesUtil,
      ModelFileDownloadService fileDownloadService,
      CustomModelDownloadService modelDownloadService,
      Executor executor) {
    this.firebaseOptions = firebaseOptions;
    this.sharedPreferencesUtil = sharedPreferencesUtil;
    this.fileDownloadService = fileDownloadService;
    this.modelDownloadService = modelDownloadService;
    this.executor = executor;
    // todo update for testing...
    this.transportFactory = null;
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
    switch (downloadType) {
      case LOCAL_MODEL:
        if (localModel != null) {
          return Tasks.forResult(localModel);
        }
        Task<CustomModel> modelDetails =
            modelDownloadService.getCustomModelDetails(
                firebaseOptions.getProjectId(), modelName, null);

        // no local model - start download.
        return modelDetails.continueWithTask(
            executor,
            modelDetailTask -> {
              if (modelDetailTask.isSuccessful()) {
                // start download
                return fileDownloadService
                    .download(modelDetailTask.getResult(), conditions)
                    .continueWithTask(
                        executor,
                        downloadTask -> {
                          if (downloadTask.isSuccessful()) {
                            // read the updated model
                            CustomModel downloadedModel =
                                sharedPreferencesUtil.getCustomModelDetails(modelName);
                            // TODO(annz) trigger file move here as well... right now it's temp
                            // call loadNewlyDownloadedModelFile
                            return Tasks.forResult(downloadedModel);
                          }
                          return Tasks.forException(new Exception("File download failed."));
                        });
              }
              return Tasks.forException(modelDetailTask.getException());
            });
      case LATEST_MODEL:
        // check for latest model and download newest
        break;
      case LOCAL_MODEL_UPDATE_IN_BACKGROUND:
        // start download in back ground return current model if not null.
        break;
    }
    throw new UnsupportedOperationException("Not yet implemented.");
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
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  /**
   * Update the settings which allow logging to firelog.
   *
   * @param enabled
   */
  public void setStatsCollectionEnabled(boolean enabled) {
    sharedPreferencesUtil.setCustomModelStatsCollectionEnabled(enabled);
  }

  /** Returns the nick name of the {@link FirebaseApp} of this {@link FirebaseModelDownloader} */
  @VisibleForTesting
  String getApplicationId() {
    return firebaseOptions.getApplicationId();
  }
}
