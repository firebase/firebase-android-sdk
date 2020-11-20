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
  private final String downloadUrl;
  private final long downloadUrlExpiry;

  /**
   * Use when creating a custom model while the initial download is still in progress.
   *
   * @param name - model name
   * @param modelHash - model hash
   * @param fileSize - model file size
   * @param downloadId - Android Download Manger - download id
   * @hide
   */
  public CustomModel(
      @NonNull String name, @NonNull String modelHash, long fileSize, long downloadId) {
    this(name, modelHash, fileSize, downloadId, "", "", 0);
  }

  /**
   * Use when creating a custom model from a stored model with a new download in the background.
   *
   * @param name - model name
   * @param modelHash - model hash
   * @param fileSize - model file size
   * @param downloadId - Android Download Manger - download id
   * @hide
   */
  public CustomModel(
      @NonNull String name,
      @NonNull String modelHash,
      long fileSize,
      long downloadId,
      String localFilePath) {
    this(name, modelHash, fileSize, downloadId, localFilePath, "", 0);
  }

  /**
   * Use when creating a custom model from a download service response. Download url and download
   * url expiry should go together. These will not be stored in user preferences as this is a
   * temporary step towards setting the actual download id.
   *
   * @param name - model name
   * @param modelHash - model hash
   * @param fileSize - model file size
   * @param downloadUrl - download url path
   * @param downloadUrlExpiry - time download url path expires
   * @hide
   */
  public CustomModel(
      @NonNull String name,
      @NonNull String modelHash,
      long fileSize,
      String downloadUrl,
      long downloadUrlExpiry) {
    this(name, modelHash, fileSize, 0, "", downloadUrl, downloadUrlExpiry);
  }

  /**
   * Use when creating a custom model while the initial download is still in progress.
   *
   * @param name - model name
   * @param modelHash - model hash
   * @param fileSize - model file size
   * @param downloadId - Android Download Manger - download id
   * @param localFilePath - location of the current file
   * @param downloadUrl - download url path returned from download service
   * @param downloadUrlExpiry - expiry time of download url link
   * @hide
   */
  private CustomModel(
      @NonNull String name,
      @NonNull String modelHash,
      long fileSize,
      long downloadId,
      @Nullable String localFilePath,
      @Nullable String downloadUrl,
      long downloadUrlExpiry) {
    this.modelHash = modelHash;
    this.name = name;
    this.fileSize = fileSize;
    this.downloadId = downloadId;
    this.localFilePath = localFilePath;
    this.downloadUrl = downloadUrl;
    this.downloadUrlExpiry = downloadUrlExpiry;
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
    if (localFilePath == null || localFilePath.isEmpty()) {
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
        && Objects.equal(downloadId, other.downloadId)
        && Objects.equal(downloadUrl, other.downloadUrl)
        && Objects.equal(downloadUrlExpiry, other.downloadUrlExpiry);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        name, modelHash, fileSize, localFilePath, downloadId, downloadUrl, downloadUrlExpiry);
  }

  /**
   * The expiry time for the current download url.
   *
   * <p>Internal use only.
   *
   * @hide
   */
  public long getDownloadUrlExpiry() {
    return downloadUrlExpiry;
  }

  /**
   * @return the model download url
   *     <p>Internal use only
   * @hide
   */
  @Nullable
  public String getDownloadUrl() {
    return downloadUrl;
  }

  /**
   * @return the model file path
   * @hide
   */
  @Nullable
  public String getLocalFilePath() {
    return localFilePath;
  }
}
