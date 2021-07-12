// Copyright 2021 Google LLC
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

package com.google.firebase.appdistribution;

import androidx.annotation.NonNull;

/** Data class to get download progress for APKs and the status of the update. Used in updateApp. */
public final class UpdateState {
  private final long apkBytesDownloaded;
  private final long apkTotalBytesToDownload;
  private final UpdateStatus updateStatus;

  UpdateState(long apkBytesDownloaded, long apkTotalBytesToDownload, UpdateStatus updateStatus) {
    this.apkBytesDownloaded = apkBytesDownloaded;
    this.apkTotalBytesToDownload = apkTotalBytesToDownload;
    this.updateStatus = updateStatus;
  }

  /** The number of bytes downloaded so far for the APK. Returns -1 if called on an AAB. */
  @NonNull
  public long getApkBytesDownloaded() {
    return apkBytesDownloaded;
  }

  /** The total number of bytes to download for the APK. Returns -1 if called on an AAB. */
  @NonNull
  public long getApkTotalBytesToDownload() {
    return apkTotalBytesToDownload;
  }

  @NonNull
  /** returns the current state of the update */
  public UpdateStatus getUpdateStatus() {
    return updateStatus;
  }
}
