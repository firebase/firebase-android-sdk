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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.modeldownloader.CustomModel;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Model File Manager is used to move the downloaded file to the appropriate locations. */
public class ModelFileManager {

  @VisibleForTesting
  static final String CUSTOM_MODEL_ROOT_PATH = "com.google.firebase.ml.custom.models";

  private static final int INVALID_INDEX = -1;
  private final Context context;
  private final FirebaseApp firebaseApp;

  public ModelFileManager(@NonNull FirebaseApp firebaseApp) {
    this.context = firebaseApp.getApplicationContext();
    this.firebaseApp = firebaseApp;
  }

  /**
   * Get ModelFileDownloadService instance using the firebase app returned by {@link
   * FirebaseApp#getInstance()}
   *
   * @return ModelFileDownloadService
   */
  @NonNull
  public static ModelFileManager getInstance() {
    return FirebaseApp.getInstance().get(ModelFileManager.class);
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
    File firebaseAppDir = new File(root, firebaseApp.getPersistenceKey());
    return new File(firebaseAppDir, modelName);
  }

  /**
   * Gets the directory in the following schema:
   * app_root/model_type_specific_root/[temp]/firebase_app_persistence_key/model_name.
   */
  @VisibleForTesting
  @WorkerThread
  File getDirImpl(@NonNull String modelName) throws Exception {
    File modelDir = getModelDirUnsafe(modelName);
    if (!modelDir.exists()) {
      if (!modelDir.mkdirs()) {
        throw new Exception("Failed to create model folder: " + modelDir);
      }
    } else if (!modelDir.isDirectory()) {
      throw new Exception(
          "Can not create model folder, since an existing file has the same name: " + modelDir);
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
        System.out.println("Contains non-integer file name " + modelFile.getName());
      }
    }
    return index;
  }

  @VisibleForTesting
  @Nullable
  File getModelFileDestination(@NonNull CustomModel model) throws Exception {
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
      throws Exception {
    File modelFileDestination = getModelFileDestination(customModel);

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
      System.out.println("Failed to copy downloaded model file to destination folder: " + e);
      modelFileDestination.delete();
      return null;
    }

    return modelFileDestination;
  }
}
