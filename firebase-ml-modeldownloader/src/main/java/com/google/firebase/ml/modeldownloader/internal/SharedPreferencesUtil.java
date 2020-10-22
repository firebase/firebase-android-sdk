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
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.modeldownloader.CustomModel;
import android.os.SystemClock;

/** @hide */
public class SharedPreferencesUtil {

  private static final String PREF_FILE = "com.google.firebase.ml.modeldownloader.internal";

  private static final String LOCAL_MODEL_HASH_PATTERN = "current_model_hash_%s_%s";
  private static final String LOCAL_MODEL_FILE_PATH_PATTERN = "current_model_path_%s_%s";
  private static final String LOCAL_MODEL_FILE_SIZE_PATTERN = "current_model_size_%s_%s";
  private static final String DOWNLOADING_MODEL_HASH_PATTERN = "downloading_model_hash_%s_%s";
  private static final String DOWNLOADING_MODEL_SIZE_PATTERN = "downloading_model_size_%s_%s";
  private static final String DOWNLOADING_MODEL_ID_PATTERN = "downloading_model_id_%s_%s";


  private static final String DOWNLOAD_BEGIN_TIME_MS_PATTERN = "downloading_begin_time_%s_%s";


  @VisibleForTesting
  static final String PREFERENCES_PACKAGE_NAME = "com.google.firebase.ml.modelDownloader";

  private final String persistenceKey;

  private final FirebaseApp firebaseApp;

  public SharedPreferencesUtil(FirebaseApp firebaseApp) {
    this.firebaseApp = firebaseApp;
    this.persistenceKey = firebaseApp.getPersistenceKey();
  }

  /**
   * Returns the {@link SharedPreferencesUtil} initialized with the default {@link FirebaseApp}.
   *
   * @return a {@link SharedPreferencesUtil} instance
   */
  @NonNull
  public static SharedPreferencesUtil getInstance() {
    FirebaseApp defaultFirebaseApp = FirebaseApp.getInstance();
    return getInstance(defaultFirebaseApp);
  }

  /**
   * Returns the {@link SharedPreferencesUtil} initialized with a custom {@link FirebaseApp}.
   *
   * @param app a custom {@link FirebaseApp}
   * @return a {@link SharedPreferencesUtil} instance
   */
  @NonNull
  public static SharedPreferencesUtil getInstance(@NonNull FirebaseApp app) {
    Preconditions.checkArgument(app != null, "Null is not a valid value of FirebaseApp.");
    return app.get(SharedPreferencesUtil.class);
  }

  /**
   * Returns the Custom Model details currently associated with this model.
   * If a fully downloaded model is present - this returns the details of that model, including local file path.
   * If an update of an existing model is in progress, the local model plus the download id for the new upload is returned.
   * To get only details related to the downloading model use getDownloadingCustomModelDetails.
   * If this is the initial download of a local file - the downloading model details are returned.
   * @param modelName
   * @return
   */
  @Nullable
  public synchronized CustomModel getCustomModelDetails(@NonNull String modelName) {
    String modelHash = getSharedPreferences()
        .getString(
            String.format(
                LOCAL_MODEL_HASH_PATTERN,
                persistenceKey,
                modelName),
            null);

    if (modelHash == null || modelHash.isEmpty()) {
      // check for downloading model, otherwise return null
      return getDownloadingCustomModelDetails(modelName);
    }

    String filePath = getSharedPreferences()
        .getString(
            String.format(
                LOCAL_MODEL_FILE_PATH_PATTERN,
                persistenceKey,
                modelName),
            null);

    long fileSize =
        getSharedPreferences()
            .getLong(
                String.format(
                    LOCAL_MODEL_FILE_SIZE_PATTERN,
                    persistenceKey,
                    modelName),
                0);

    long id =
        getSharedPreferences()
            .getLong(
                String.format(
                    DOWNLOADING_MODEL_ID_PATTERN,
                    persistenceKey,
                    modelName),
                0);

    return new CustomModel(modelName, id, fileSize, modelHash, filePath);
  }

  /**
   * Returns the Custom Model details associated with this version of this model currently being downloaded.
   * If no download is in progress return null.
   * @param modelName
   * @return Download version of CustomModel
   */
  @Nullable
  public synchronized CustomModel getDownloadingCustomModelDetails(@NonNull String modelName) {
    String modelHash = getSharedPreferences()
        .getString(
            String.format(
                DOWNLOADING_MODEL_HASH_PATTERN,
                persistenceKey,
                modelName),
            null);

    if (modelHash == null || modelHash.isEmpty()) {
      // check for downloading model, otherwise return null
      return null;
    }

    long fileSize =
        getSharedPreferences()
            .getLong(
                String.format(
                    DOWNLOADING_MODEL_SIZE_PATTERN,
                    persistenceKey,
                    modelName),
                0);

    long id =
        getSharedPreferences()
            .getLong(
                String.format(
                    DOWNLOADING_MODEL_ID_PATTERN,
                    persistenceKey,
                    modelName),
                0);

    return new CustomModel(modelName, id, fileSize, modelHash);
  }

  public synchronized void setDownloadingCustomModelDetails(@NonNull CustomModel customModel) {
    String modelName = customModel.getName();
    String modelHash = customModel.getModelHash();
    Long id = customModel.getDownloadId();
    Long size = customModel.getSize();
    getSharedPreferences()
        .edit()
        .putString(
            String.format(DOWNLOADING_MODEL_HASH_PATTERN, persistenceKey, modelName),
            modelHash)
        .putLong(
            String.format(DOWNLOADING_MODEL_SIZE_PATTERN, persistenceKey, modelName),
            size)
        .putLong(
            String.format(DOWNLOADING_MODEL_ID_PATTERN, persistenceKey, modelName), id)
        // The following assumes the download will finish before the system reboots.
        // If not, the download duration won't be correct, which isn't critical.
        .putLong(
            String.format(DOWNLOAD_BEGIN_TIME_MS_PATTERN, persistenceKey, modelName),
            SystemClock.elapsedRealtime())
        .apply();
  }

  public synchronized void setUploadedCustomModelDetails(
      @NonNull CustomModel customModel, boolean clearDownloadDetails) {
    Long id = customModel.getDownloadId();
    // only call when download is completed and download id is reset to 0;
    assert(id).equals(0);
    String modelName = customModel.getName();
    String hash = customModel.getModelHash();
    Long size = customModel.getSize();
    getSharedPreferences()
        .edit()
        .putString(
            String.format(LOCAL_MODEL_HASH_PATTERN, persistenceKey, modelName),
            hash)
        .putLong(
            String.format(LOCAL_MODEL_FILE_SIZE_PATTERN, persistenceKey, modelName),
            size)
        .putString(
            String.format(LOCAL_MODEL_FILE_PATH_PATTERN, persistenceKey, modelName),
            hash)
        .putLong(
            String.format(DOWNLOADING_MODEL_ID_PATTERN, persistenceKey, modelName), id)
        .apply();

    if (clearDownloadDetails) {
      clearDownloadingModelDetails(customModel);
    }
  }

  public synchronized void clearDownloadingModelDetails(@NonNull CustomModel remoteModel) {
    getSharedPreferences()
        .edit()
        .remove(
            String.format(
                DOWNLOADING_MODEL_ID_PATTERN,
                persistenceKey,
                remoteModel.getName()))
        .remove(
            String.format(
                DOWNLOADING_MODEL_HASH_PATTERN,
                persistenceKey,
                remoteModel.getName()))
        .remove(
            String.format(
                DOWNLOADING_MODEL_SIZE_PATTERN,
                persistenceKey,
                remoteModel.getName()))
        .remove(
            String.format(
                DOWNLOAD_BEGIN_TIME_MS_PATTERN,
                persistenceKey,
                remoteModel.getName()))
        .apply();
  }

  public synchronized void clearModelDetails(@NonNull CustomModel remoteModel) {
    clearDownloadingModelDetails(remoteModel);
    getSharedPreferences()
        .edit()
        .remove(
            String.format(
                LOCAL_MODEL_FILE_PATH_PATTERN,
                persistenceKey,
                remoteModel.getName()))
        .remove(
            String.format(
                LOCAL_MODEL_FILE_SIZE_PATTERN,
                persistenceKey,
                remoteModel.getName()))
        .remove(
            String.format(
                LOCAL_MODEL_HASH_PATTERN,
                persistenceKey,
                remoteModel.getName()))
        .apply();
  }


  private SharedPreferences getSharedPreferences() {
    return firebaseApp.getApplicationContext().getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
  }

}
