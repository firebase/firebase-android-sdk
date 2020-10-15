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
import java.io.File;

/**
 * Used to store information about custom models that are being downloaded or are already downloaded
 * on a device.
 */
public class CustomModel {
  private String name;
  private long downloadId;
  private long fileSize;
  private String modelHash;
  private String localFilePath = "";

  public @NonNull String getName() {
    return name;
  }

  public @Nullable File getFile() {
    if (localFilePath.isEmpty()) {
      return null;
    }
    throw new UnsupportedOperationException("Not implemented, file retrieval coming soon.");
  }

  public long getSize() {
    return fileSize;
  }

  public @NonNull String getModelHash() {
    return modelHash;
  }

  // If download in progress, return the download id, otherwise return 0
  // Can be used with AndroidDownloadManager to query progress information
  public long getDownloadId() {
    return downloadId;
  }

  CustomModel(String name, long downloadId, long fileSize, String modelHash) {
    this.modelHash = modelHash;
    this.name = name;
    this.fileSize = fileSize;
    this.downloadId = downloadId;
  }
}
