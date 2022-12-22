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
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.modeldownloader.CustomModel;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;

/** @hide */
@Singleton
public class SharedPreferencesUtil {

  public static final String FIREBASE_MODELDOWNLOADER_COLLECTION_ENABLED =
      "firebase_model_downloader_collection_enabled";
  public static final String DOWNLOADING_MODEL_ID_MATCHER = "downloading_model_id_(.*?)_([^/]+)/?";

  public static final String PREFERENCES_PACKAGE_NAME = "com.google.firebase.ml.modelDownloader";

  // local model details
  private static final String LOCAL_MODEL_HASH_PATTERN = "current_model_hash_%s_%s";
  private static final String LOCAL_MODEL_FILE_PATH_PATTERN = "current_model_path_%s_%s";
  private static final String LOCAL_MODEL_FILE_PATH_MATCHER = "current_model_path_(.*?)_([^/]+)/?";
  private static final String LOCAL_MODEL_FILE_SIZE_PATTERN = "current_model_size_%s_%s";
  // details about model during download.
  private static final String DOWNLOADING_MODEL_HASH_PATTERN = "downloading_model_hash_%s_%s";
  private static final String DOWNLOADING_MODEL_SIZE_PATTERN = "downloading_model_size_%s_%s";
  private static final String DOWNLOADING_MODEL_ID_PATTERN = "downloading_model_id_%s_%s";
  private static final String DOWNLOAD_BEGIN_TIME_MS_PATTERN = "downloading_begin_time_%s_%s";
  private static final String DOWNLOADING_COMPLETE_TIME_MS_PATTERN =
      "downloading_complete_time_%s_%s";

  // logging keys
  private static final String EVENT_LOGGING_ENABLED_PATTERN = "logging_%s_%s";
  private static final String CUSTOM_MODEL_LIB = "custom_model";

  private final String persistenceKey;
  private final FirebaseApp firebaseApp;
  private final CustomModel.Factory modelFactory;

  @Inject
  public SharedPreferencesUtil(FirebaseApp firebaseApp, CustomModel.Factory modelFactory) {
    this.firebaseApp = firebaseApp;
    this.persistenceKey = firebaseApp.getPersistenceKey();
    this.modelFactory = modelFactory;
  }

  /**
   * Returns the Custom Model details currently associated with this model. If a fully downloaded
   * model is present - this returns the details of that model, including local file path. If an
   * update of an existing model is in progress, the local model plus the download id for the new
   * download is returned. To get only details related to the downloading model use {@link
   * #getDownloadingCustomModelDetails}. If this is the initial download of a local file - the
   * downloading model details are returned.
   *
   * @param modelName - name of the model
   * @return current version of the Custom Model
   */
  @Nullable
  public synchronized CustomModel getCustomModelDetails(@NonNull String modelName) {
    String modelHash =
        getSharedPreferences()
            .getString(String.format(LOCAL_MODEL_HASH_PATTERN, persistenceKey, modelName), null);

    if (modelHash == null || modelHash.isEmpty()) {
      // no model downloaded - check if model is being downloaded.
      return getDownloadingCustomModelDetails(modelName);
    }

    String filePath =
        getSharedPreferences()
            .getString(String.format(LOCAL_MODEL_FILE_PATH_PATTERN, persistenceKey, modelName), "");

    long fileSize =
        getSharedPreferences()
            .getLong(String.format(LOCAL_MODEL_FILE_SIZE_PATTERN, persistenceKey, modelName), 0);

    // if no-zero - local model is present and new model being downloaded
    long id =
        getSharedPreferences()
            .getLong(String.format(DOWNLOADING_MODEL_ID_PATTERN, persistenceKey, modelName), 0);

    return modelFactory.create(modelName, modelHash, fileSize, id, filePath);
  }

  /**
   * Returns the Custom Model details associated with this version of this model currently being
   * downloaded. If no download is in progress return null. Contains no information about local
   * model, only download status.
   *
   * @param modelName name of the model
   * @return Download version of CustomModel
   */
  @Nullable
  public synchronized CustomModel getDownloadingCustomModelDetails(@NonNull String modelName) {
    String modelHash =
        getSharedPreferences()
            .getString(
                String.format(DOWNLOADING_MODEL_HASH_PATTERN, persistenceKey, modelName), null);

    if (modelHash == null || modelHash.isEmpty()) {
      // no model hash means no download in progress
      return null;
    }

    long fileSize =
        getSharedPreferences()
            .getLong(String.format(DOWNLOADING_MODEL_SIZE_PATTERN, persistenceKey, modelName), 0);

    long id =
        getSharedPreferences()
            .getLong(String.format(DOWNLOADING_MODEL_ID_PATTERN, persistenceKey, modelName), 0);

    return modelFactory.create(modelName, modelHash, fileSize, id);
  }

  /**
   * The information about the new custom model download that need to be stored.
   *
   * @param customModel custom model details to be stored.
   */
  public synchronized void setDownloadingCustomModelDetails(@NonNull CustomModel customModel) {
    String modelName = customModel.getName();
    String modelHash = customModel.getModelHash();
    long downloadId = customModel.getDownloadId();
    long modelSize = customModel.getSize();
    getSharedPreferences()
        .edit()
        .putString(
            String.format(DOWNLOADING_MODEL_HASH_PATTERN, persistenceKey, modelName), modelHash)
        .putLong(
            String.format(DOWNLOADING_MODEL_SIZE_PATTERN, persistenceKey, modelName), modelSize)
        .putLong(String.format(DOWNLOADING_MODEL_ID_PATTERN, persistenceKey, modelName), downloadId)
        // The following assumes the download will finish before the system reboots.
        // If not, the download duration won't be correct, which isn't critical.
        .putLong(
            String.format(DOWNLOAD_BEGIN_TIME_MS_PATTERN, persistenceKey, modelName),
            SystemClock.elapsedRealtime())
        .commit();
  }

  /**
   * The information about a completed custom model download. Updates the local model information
   * and clears the download details associated with this model.
   *
   * @param customModel custom model details to be stored.
   */
  public synchronized void setLoadedCustomModelDetails(@NonNull CustomModel customModel)
      throws IllegalArgumentException {
    Long id = customModel.getDownloadId();
    // only call when download is completed and download id is reset to 0;
    if (!id.equals(0L)) {
      throw new IllegalArgumentException("Only call when Custom model has completed download.");
    }
    Editor editor = getSharedPreferences().edit();
    clearDownloadingModelDetails(editor, customModel.getName());

    String modelName = customModel.getName();
    String hash = customModel.getModelHash();
    long size = customModel.getSize();
    String filePath = customModel.getLocalFilePath();
    editor
        .putString(String.format(LOCAL_MODEL_HASH_PATTERN, persistenceKey, modelName), hash)
        .putLong(String.format(LOCAL_MODEL_FILE_SIZE_PATTERN, persistenceKey, modelName), size)
        .putString(
            String.format(LOCAL_MODEL_FILE_PATH_PATTERN, persistenceKey, modelName), filePath)
        .commit();
  }

  /**
   * Clears the download details associated with this model. Does not update the local file model.
   *
   * <p>Usually used during clean up of a completed model download.
   *
   * @param customModelName custom model details to be stored.
   * @hide
   */
  public synchronized void clearDownloadCustomModelDetails(@NonNull String customModelName)
      throws IllegalArgumentException {
    Editor editor = getSharedPreferences().edit();
    clearDownloadingModelDetails(editor, customModelName);
  }

  /**
   * Clears all stored data related to a local custom model, including download details.
   *
   * <p>Call ModelFileManager.deleteAllModels() before calling this to trigger successful clean up
   * of downloaded files.
   *
   * @param modelName - name of model
   */
  public synchronized void clearModelDetails(@NonNull String modelName) {
    Editor editor = getSharedPreferences().edit();

    clearDownloadingModelDetails(editor, modelName);

    editor
        .remove(String.format(LOCAL_MODEL_FILE_PATH_PATTERN, persistenceKey, modelName))
        .remove(String.format(LOCAL_MODEL_FILE_SIZE_PATTERN, persistenceKey, modelName))
        .remove(String.format(LOCAL_MODEL_HASH_PATTERN, persistenceKey, modelName))
        .commit();
  }

  /**
   * Set of all keys associated with this firebase app.
   *
   * @return all shared preference keys for this app
   */
  public Set<String> getSharedPreferenceKeySet() {
    return getSharedPreferences().getAll().keySet();
  }

  /**
   * Lists the current set of downloaded model, does not include downloads in progress. Call
   * ModelFileManager.maybeGetUpdatedModels() before calling this to trigger successful download
   * completions.
   *
   * @return list of Custom Models.
   */
  public synchronized Set<CustomModel> listDownloadedModels() {
    Set<CustomModel> customModels = new HashSet<>();
    Set<String> keySet = getSharedPreferences().getAll().keySet();

    for (String key : keySet) {
      // if a local file path is present - get model details.
      Matcher matcher = Pattern.compile(LOCAL_MODEL_FILE_PATH_MATCHER).matcher(key);
      if (matcher.find()) {
        String modelName = matcher.group(matcher.groupCount());
        CustomModel extractModel = getCustomModelDetails(modelName);
        if (extractModel != null) {
          customModels.add(extractModel);
        }
      }
    }
    return customModels;
  }

  /**
   * Should Firelog logging be enabled.
   *
   * @return whether or not firelog events should be logged. Checks shared preference, then
   *     manifest, finally defaults to Firebase wide data collection switch.
   */
  public synchronized boolean getCustomModelStatsCollectionFlag() {
    if (getSharedPreferences()
        .contains(String.format(EVENT_LOGGING_ENABLED_PATTERN, CUSTOM_MODEL_LIB, persistenceKey))) {
      return getSharedPreferences()
          .getBoolean(
              String.format(EVENT_LOGGING_ENABLED_PATTERN, CUSTOM_MODEL_LIB, persistenceKey), true);
    }
    Boolean manifestFlag =
        readModelDownloaderCollectionEnabledFromManifest(firebaseApp.getApplicationContext());
    if (manifestFlag != null) {
      return manifestFlag;
    }
    return firebaseApp.isDataCollectionDefaultEnabled();
  }

  @Nullable
  private static Boolean readModelDownloaderCollectionEnabledFromManifest(
      Context applicationContext) {
    try {
      final PackageManager packageManager = applicationContext.getPackageManager();
      if (packageManager != null) {
        final ApplicationInfo applicationInfo =
            packageManager.getApplicationInfo(
                applicationContext.getPackageName(), PackageManager.GET_META_DATA);
        if (applicationInfo != null
            && applicationInfo.metaData != null
            && applicationInfo.metaData.containsKey(FIREBASE_MODELDOWNLOADER_COLLECTION_ENABLED)) {
          return applicationInfo.metaData.getBoolean(FIREBASE_MODELDOWNLOADER_COLLECTION_ENABLED);
        }
      }
    } catch (PackageManager.NameNotFoundException e) {
      // This shouldn't happen since it's this app's package, but fall through to default
      // if so.
    }
    return null;
  }

  /**
   * Set whether firelog logging should be enabled. When not explicitly set, uses the Firebase wide
   * data collection switch.
   *
   * @param enable - False to turn off logging. True to turn on logging.
   */
  public synchronized void setCustomModelStatsCollectionEnabled(Boolean enable) {
    if (enable == null) {
      getSharedPreferences()
          .edit()
          .remove(String.format(EVENT_LOGGING_ENABLED_PATTERN, CUSTOM_MODEL_LIB, persistenceKey))
          .commit();
    } else {
      getSharedPreferences()
          .edit()
          .putBoolean(
              String.format(EVENT_LOGGING_ENABLED_PATTERN, CUSTOM_MODEL_LIB, persistenceKey),
              enable)
          .commit();
    }
  }

  /**
   * Gets the start time (in ms) of the model download attempt.
   *
   * @param customModel model
   * @return time in ms
   */
  public synchronized long getModelDownloadBeginTimeMs(@NonNull CustomModel customModel) {
    return getSharedPreferences()
        .getLong(
            String.format(DOWNLOAD_BEGIN_TIME_MS_PATTERN, persistenceKey, customModel.getName()),
            0L);
  }

  /**
   * Gets the estimated completion time of successful or failed download attempts.
   *
   * @param customModel - model
   * @return time in ms
   */
  public synchronized long getModelDownloadCompleteTimeMs(@NonNull CustomModel customModel) {
    return getSharedPreferences()
        .getLong(
            String.format(
                DOWNLOADING_COMPLETE_TIME_MS_PATTERN, persistenceKey, customModel.getName()),
            0L);
  }

  /**
   * Sets the estimated completion time of successful or failed download attempts.
   *
   * @param customModel - model
   * @param completionTimeInMs - time in ms
   */
  public synchronized void setModelDownloadCompleteTimeMs(
      @NonNull CustomModel customModel, long completionTimeInMs) {
    getSharedPreferences()
        .edit()
        .putLong(
            String.format(
                DOWNLOADING_COMPLETE_TIME_MS_PATTERN, persistenceKey, customModel.getName()),
            completionTimeInMs)
        .apply();
  }

  /**
   * Clears all stored data related to a custom model download.
   *
   * @param modelName - name of model
   */
  @VisibleForTesting
  synchronized void clearDownloadingModelDetails(Editor editor, @NonNull String modelName) {
    editor
        .remove(String.format(DOWNLOADING_MODEL_ID_PATTERN, persistenceKey, modelName))
        .remove(String.format(DOWNLOADING_MODEL_HASH_PATTERN, persistenceKey, modelName))
        .remove(String.format(DOWNLOADING_MODEL_SIZE_PATTERN, persistenceKey, modelName))
        .remove(String.format(DOWNLOAD_BEGIN_TIME_MS_PATTERN, persistenceKey, modelName))
        .remove(String.format(DOWNLOADING_COMPLETE_TIME_MS_PATTERN, persistenceKey, modelName))
        .apply();
  }

  @VisibleForTesting
  SharedPreferences getSharedPreferences() {
    return firebaseApp
        .getApplicationContext()
        .getSharedPreferences(PREFERENCES_PACKAGE_NAME, Context.MODE_PRIVATE);
  }
}
