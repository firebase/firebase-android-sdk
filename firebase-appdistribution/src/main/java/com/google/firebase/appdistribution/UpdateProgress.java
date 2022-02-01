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
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;

/** Represents a progress update or a final state from updating an app. */
@AutoValue
public abstract class UpdateProgress {

  @NonNull
  static UpdateProgress.Builder builder() {
    return new com.google.firebase.appdistribution.AutoValue_UpdateProgress.Builder();
  }

  /**
   * The number of bytes downloaded so far for an APK. Returns -1 if called when updating to an AAB
   * or if no new release is available.
   */
  @NonNull
  public abstract long getApkBytesDownloaded();

  /**
   * The file size of the APK file to download in bytes. Returns -1 if called when updating to an
   * AAB or if no new release is available.
   */
  @NonNull
  public abstract long getApkFileTotalBytes();

  /** Returns the current {@link UpdateStatus} of the update */
  @NonNull
  public abstract UpdateStatus getUpdateStatus();

  /** Builder for {@link UpdateProgress}. */
  @AutoValue.Builder
  abstract static class Builder {

    @NonNull
    abstract UpdateProgress.Builder setApkBytesDownloaded(@NonNull long value);

    @NonNull
    abstract UpdateProgress.Builder setApkFileTotalBytes(@NonNull long value);

    @NonNull
    abstract UpdateProgress.Builder setUpdateStatus(@Nullable UpdateStatus value);

    @NonNull
    abstract UpdateProgress build();
  }
}
