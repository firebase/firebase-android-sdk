// Copyright 2022 Google LLC
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

/** Represents a progress update or a final state from updating an app. */
public interface UpdateProgress {
  /**
   * Returns the number of bytes downloaded so far for an APK.
   *
   * @returns the number of bytes downloaded, or -1 if called when updating to an AAB or if no new
   *     release is available.
   */
  long getApkBytesDownloaded();

  /**
   * Returns the file size of the APK file to download in bytes.
   *
   * @returns the file size in bytes, or -1 if called when updating to an AAB or if no new release
   *     is available.
   */
  long getApkFileTotalBytes();

  /** Returns the current {@link UpdateStatus} of the update. */
  @NonNull
  UpdateStatus getUpdateStatus();
}
