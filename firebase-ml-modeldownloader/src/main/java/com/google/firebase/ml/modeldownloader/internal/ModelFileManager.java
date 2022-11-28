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
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.FirebaseMlException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Model File Manager is used to move the downloaded file to the appropriate locations.
 *
 * @hide
 */
@Singleton
public class ModelFileManager {

  public static final String CUSTOM_MODEL_ROOT_PATH = "com.google.firebase.ml.custom.models";
  private static final String TAG = "FirebaseModelFileManage";
  private static final int INVALID_INDEX = -1;
  private final Context context;
  private final String persistenceKey;
  private final SharedPreferencesUtil sharedPreferencesUtil;

  @Inject
  public ModelFileManager(
      Context applicationContext,
      @Named("persistenceKey") String persistenceKey,
      SharedPreferencesUtil sharedPreferencesUtil) {
    this.context = applicationContext;
    this.persistenceKey = persistenceKey;
    this.sharedPreferencesUtil = sharedPreferencesUtil;
  }

  void deleteNonLatestCustomModels() throws FirebaseMlException {
    File root = getDirImpl("");

    if (root.isDirectory()) {
      for (File f : root.listFiles()) {
        // for each custom model sub directory - extract customModelName and clean up old models.
        String modelName = f.getName();

        CustomModel model = sharedPreferencesUtil.getCustomModelDetails(modelName);
        if (model != null) {
          deleteOldModels(modelName, model.getLocalFilePath());
        }
      }
    }
  }

  /**
   * Get the directory where the model is supposed to reside. This method does not ensure that the
   * directory specified does exist. If you need to ensure its existence, you should call
   * getDirImpl.
   */
  @Nullable
  private File getModelDirUnsafe(@NonNull String modelName) {
    String modelTypeSpecificRoot = CUSTOM_MODEL_ROOT_PATH;
    File root;
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      root = new File(context.getNoBackupFilesDir(), modelTypeSpecificRoot);
    } else {
      root = context.getApplicationContext().getDir(modelTypeSpecificRoot, Context.MODE_PRIVATE);
    }
    File firebaseAppDir = new File(root, persistenceKey);
    return new File(firebaseAppDir, modelName);
  }

  /**
   * Gets the directory in the following schema:
   * app_root/model_type_specific_root/[temp]/firebase_app_persistence_key/model_name.
   */
  @VisibleForTesting
  @WorkerThread
  File getDirImpl(@NonNull String modelName) throws FirebaseMlException {
    File modelDir = getModelDirUnsafe(modelName);
    if (!modelDir.exists()) {
      if (!modelDir.mkdirs()) {
        throw new FirebaseMlException(
            "Failed to create model folder: " + modelDir, FirebaseMlException.INTERNAL);
      }
    } else if (!modelDir.isDirectory()) {
      throw new FirebaseMlException(
          "Can not create model folder, since an existing file has the same name: " + modelDir,
          FirebaseMlException.ALREADY_EXISTS);
    }
    return modelDir;
  }

  /**
   * Since the model files under the model folder are named with numbers, and the later one is the
   * newer, the latest model is the file name with largest number.
   */
  @WorkerThread
  private int getLatestCachedModelVersion(@NonNull File modelDir) {
    File[] modelFiles = modelDir.listFiles();
    if (modelFiles == null || modelFiles.length == 0) {
      return INVALID_INDEX;
    }

    int index = INVALID_INDEX;
    for (File modelFile : modelFiles) {
      try {
        index = Math.max(index, Integer.parseInt(modelFile.getName()));
      } catch (NumberFormatException e) {
        Log.d(TAG, String.format("Contains non-integer file name %s", modelFile.getName()));
      }
    }
    return index;
  }

  @VisibleForTesting
  @Nullable
  File getModelFileDestination(@NonNull CustomModel model) throws FirebaseMlException {
    File destFolder = getDirImpl(model.getName());

    int index = getLatestCachedModelVersion(destFolder);
    return new File(destFolder, String.valueOf(index + 1));
  }

  /**
   * Moves a downloaded file from external storage to private folder.
   *
   * <p>The private file path pattern is /%private_folder%/%firebaseapp_persistentkey%/%model_name%/
   *
   * <p>The model file under the model folder are named with numbers starting from 0. The larger one
   * is the newer model downloaded from cloud.
   *
   * <p>The caller is supposed to cleanup the previous downloaded files after this call, even when
   * this call throws exception.
   *
   * @return null if the movement failed. Otherwise, return the destination file.
   */
  @Nullable
  @WorkerThread
  public synchronized File moveModelToDestinationFolder(
      @NonNull CustomModel customModel, @NonNull ParcelFileDescriptor modelFileDescriptor)
      throws FirebaseMlException {
    File modelFileDestination = getModelFileDestination(customModel);
    // why would this ever be true?
    File modelFolder = modelFileDestination.getParentFile();
    if (!modelFolder.exists()) {
      modelFolder.mkdirs();
    }

    // Moves to the final destination file in app private folder to avoid the downloaded file from
    // being changed by
    // other apps.
    try (FileInputStream fis = new AutoCloseInputStream(modelFileDescriptor);
        FileOutputStream fos = new FileOutputStream(modelFileDestination)) {
      byte[] buffer = new byte[4096];
      int read;
      while ((read = fis.read(buffer)) != -1) {
        fos.write(buffer, 0, read);
      }
      // Let's be extra sure it is all written before we return.
      fos.getFD().sync();
    } catch (IOException e) {
      // Failed to copy to destination - clean up.
      Log.d(TAG, "Failed to copy downloaded model file to destination folder: " + e.toString());
      modelFileDestination.delete();
      return null;
    }

    return modelFileDestination;
  }

  /**
   * Deletes old models in the custom model directory, except the {@code latestModelFilePath}. This
   * should only be called when no files are in use or more specifically when the first
   * initialization, otherwise it may remove a model that is in use.
   *
   * @param latestModelFilePath The file path to the latest custom model.
   */
  @WorkerThread
  public synchronized void deleteOldModels(
      @NonNull String modelName, @NonNull String latestModelFilePath) {
    File modelFolder = getModelDirUnsafe(modelName);
    if (!modelFolder.exists() || latestModelFilePath.isEmpty()) {
      return;
    }

    File latestFile = new File(latestModelFilePath);
    int latestIndex = Integer.parseInt(latestFile.getName());
    File[] modelFiles = modelFolder.listFiles();

    boolean isAllDeleted = true;
    int fileInt;
    for (File modelFile : modelFiles) {
      try {
        fileInt = Integer.parseInt(modelFile.getName());
      } catch (NumberFormatException ex) {
        // unexpected file - ignore
        fileInt = Integer.MAX_VALUE;
      }
      if (fileInt < latestIndex) {
        isAllDeleted = isAllDeleted && modelFile.delete();
      }
    }
  }

  /**
   * Deletes all previously cached Model File(s) and the model root folder.
   *
   * <p>All model and model support files are stored in temp folder until the model gets fully
   * downloaded, validated and hash-checked. We delete both temp files and files in the final
   * destination for a model in this method.
   */
  @WorkerThread
  public synchronized boolean deleteAllModels(@NonNull String modelName) {
    File modelFolder = getModelDirUnsafe(modelName);
    return deleteRecursively(modelFolder);
  }

  /**
   * Deletes all files under the {@code root}. If @{code root} is a file, just itself will be
   * deleted. If it is a folder, all files and subfolders will be deleted, including {@code root}
   * itself.
   */
  boolean deleteRecursively(@Nullable File root) {
    if (root == null) {
      return false;
    }

    boolean ret = true;
    if (root.isDirectory()) {
      for (File f : root.listFiles()) {
        ret = ret && deleteRecursively(f);
      }
    }

    return ret && root.delete();
  }
}
