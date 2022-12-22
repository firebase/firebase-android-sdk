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
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.Objects;
import com.google.firebase.ml.modeldownloader.internal.ModelFileDownloadService;
import java.io.File;

/**
 * Stores information about custom models that are being downloaded or are already downloaded on a
 * device. In the case where an update is available, after the updated model file is fully
 * downloaded, the original model file will be removed once it is safe to do so.
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
   * @param name Model name.
   * @param modelHash Model hash.
   * @param fileSize Model file size.
   * @param downloadId Android Download Manger - download ID.
   * @hide
   */
  public CustomModel(
      @NonNull String name, @NonNull String modelHash, long fileSize, long downloadId) {
    this(name, modelHash, fileSize, downloadId, "", "", 0);
  }

  /**
   * Use when creating a custom model from a stored model with a new download in the background.
   *
   * @param name Model name.
   * @param modelHash Model hash.
   * @param fileSize Model file size.
   * @param downloadId Android Download Manger - download ID.
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
   * Use when creating a custom model from a download service response. Download URL and download
   * URL expiry should go together. These will not be stored in user preferences as this is a
   * temporary step towards setting the actual download ID.
   *
   * @param name Model name.
   * @param modelHash Model hash.
   * @param fileSize Model file size.
   * @param downloadUrl Download URL path
   * @param downloadUrlExpiry Time download URL path expires.
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
   * @param name Model name.
   * @param modelHash Model hash.
   * @param fileSize Model file size.
   * @param downloadId Android Download Manger - download ID.
   * @param localFilePath Location of the current file.
   * @param downloadUrl Download URL path returned from download service.
   * @param downloadUrlExpiry Expiry time of download URL link.
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

  /**
   * Retrieves the model name and identifier.
   *
   * @return The name of the model.
   */
  @NonNull
  public String getName() {
    return name;
  }

  /**
   * The local model file. If <code>null</code> is returned, use the download ID to check the
   * download status.
   *
   * @return The local file associated with the model. If the original file download is still in
   *     progress, returns <code>null</code>. If a file update is in progress, returns the last
   *     fully downloaded model.
   */
  @Nullable
  public File getFile() {
    return getFile(ModelFileDownloadService.getInstance());
  }

  /**
   * The local model file. If <code>null</code> is returned, use the download ID to check the
   * download status.
   *
   * @return The local file associated with the model. If the original file download is still in
   *     progress, returns <code>null</code>. If a file update is in progress, returns the last
   *     fully downloaded model.
   */
  @Nullable
  @VisibleForTesting
  File getFile(ModelFileDownloadService fileDownloadService) {
    // check for completed download
    File newDownloadFile = fileDownloadService.loadNewlyDownloadedModelFile(this);
    if (newDownloadFile != null) {
      return newDownloadFile;
    }
    // return local file, if present
    if (localFilePath == null || localFilePath.isEmpty()) {
      return null;
    }
    File modelFile = new File(localFilePath);

    if (!modelFile.exists()) {
      return null;
    }

    return modelFile;
  }

  boolean isModelFilePresent() {
    try {
      return getFile() != null;
    } catch (Exception ex) {
      return false;
    }
  }

  /**
   * The size of the file currently associated with this model. If a download is in progress, this
   * will be the size of the current model, not the new model currently being downloaded.
   *
   * @return The local model size.
   */
  public long getSize() {
    return fileSize;
  }

  /**
   * Retrieves the model hash.
   *
   * @return The model hash
   */
  @NonNull
  public String getModelHash() {
    return modelHash;
  }

  /**
   * The download ID (returns 0 if no download in progress), which can be used with the Android
   * <code>DownloadManager</code> to query download progress. The retrieved progress information can
   * be used to populate a progress bar, monitor when an updated model is available, etc.
   *
   * @return The download ID (if download in progress), otherwise returns 0.
   */
  public long getDownloadId() {
    return downloadId;
  }

  @NonNull
  @Override
  public String toString() {
    Objects.ToStringHelper stringHelper =
        Objects.toStringHelper(this)
            .add("name", name)
            .add("modelHash", modelHash)
            .add("fileSize", fileSize);

    if (localFilePath != null && !localFilePath.isEmpty()) {
      stringHelper.add("localFilePath", localFilePath);
    }
    if (downloadId != 0L) {
      stringHelper.add("downloadId", downloadId);
    }
    if (downloadUrl != null && !downloadUrl.isEmpty()) {
      stringHelper.add("downloadUrl", downloadUrl);
    }
    if (downloadUrlExpiry != 0L) {
      stringHelper.add("downloadUrlExpiry", downloadUrlExpiry);
    }

    return stringHelper.toString();
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
   * The expiry time for the current download URL.
   *
   * <p>Internal use only.
   *
   * @hide
   */
  public long getDownloadUrlExpiry() {
    return downloadUrlExpiry;
  }

  /**
   * Returns the model download URL, usually only present when download is about to occur.
   *
   * @return The model download url.
   *     <p>Internal use only
   * @hide
   */
  @Nullable
  public String getDownloadUrl() {
    return downloadUrl;
  }

  /**
   * @return The model file path.
   * @hide
   */
  @Nullable
  public String getLocalFilePath() {
    return localFilePath;
  }
}
