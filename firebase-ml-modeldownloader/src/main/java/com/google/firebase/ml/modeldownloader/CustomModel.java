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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.internal.Objects;
import java.io.File;

/**
 * Used to store information about custom models that are being downloaded or are already downloaded
 * on a device. The model file associated with this model can be updated, once the new model file is
 * fully uploaded, the original model file will be removed as soon as it is safe to do so.
 */
public class CustomModel {
  private final String name;
  private final long downloadId;
  private final long fileSize;
  private final String modelHash;
  private final String localFilePath;

  /**
   * Use when creating a custom model while the initial download is still in progress.
   *
   * @param name - model name
   * @param downloadId - Android Download Manger - download id
   * @param fileSize - model file size
   * @param modelHash - model hash size
   * @hide
   */
  public CustomModel(
      @NonNull String name, long downloadId, long fileSize, @NonNull String modelHash) {
    this(name, downloadId, fileSize, modelHash, "");
  }

  /**
   * Use when creating a custom model while the initial download is still in progress.
   *
   * @param name - model name
   * @param downloadId - Android Download Manger - download id
   * @param fileSize - model file size
   * @param modelHash - model hash size
   * @param localFilePath - location of the current file
   * @hide
   */
  public CustomModel(
      @NonNull String name,
      long downloadId,
      long fileSize,
      @NonNull String modelHash,
      @NonNull String localFilePath) {
    this.modelHash = modelHash;
    this.name = name;
    this.fileSize = fileSize;
    this.downloadId = downloadId;
    this.localFilePath = localFilePath;
  }

  @NonNull
  public String getName() {
    return name;
  }

  /**
   * The local model file. If null is returned, use the download Id to check the download status.
   *
   * @return the local file associated with the model, if the original file download is still in
   *     progress, returns null, if file update is in progress returns last fully uploaded model.
   */
  @Nullable
  public File getFile() {
    if (localFilePath.isEmpty()) {
      return null;
    }
    throw new UnsupportedOperationException("Not implemented, file retrieval coming soon.");
  }

  /**
   * The size of the file currently associated with this model. If a download is in progress, this
   * will be the size of the current model, not the new model currently being uploaded.
   *
   * @return the local model size
   */
  public long getSize() {
    return fileSize;
  }

  /** @return the model hash */
  @NonNull
  public String getModelHash() {
    return modelHash;
  }

  /**
   * The download id (returns 0 if no download in progress), which can be used with the
   * AndroidDownloadManager to query download progress. The retrieved progress information can be
   * used to populate a progress bar, monitor when an updated model is available, etc.
   *
   * @return the download id (if download in progress), otherwise returns 0
   */
  public long getDownloadId() {
    return downloadId;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof CustomModel)) {
      return false;
    }

    CustomModel other = (CustomModel) o;

    return Objects.equal(name, other.name)
        && Objects.equal(modelHash, other.modelHash)
        && Objects.equal(fileSize, other.fileSize)
        && Objects.equal(localFilePath, other.localFilePath)
        && Objects.equal(downloadId, other.downloadId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, modelHash, fileSize, localFilePath, downloadId);
  }

  /**
   * @return the model file path
   * @hide
   */
  @NonNull
  public String getLocalFilePath() {
    return localFilePath;
  }
}
