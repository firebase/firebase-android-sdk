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

package com.google.firebase.appdistribution.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;
import com.google.firebase.appdistribution.BinaryType;

/**
 * This class represents the AppDistributionRelease object returned by the App Distribution backend.
 *
 * <p>TODO(lkellogg): Combine this with AppDistributionReleaseImpl
 */
@AutoValue
abstract class AppDistributionReleaseInternal {

  @NonNull
  static Builder builder() {
    return new AutoValue_AppDistributionReleaseInternal.Builder();
  }

  /** The short bundle version of this build (example: 1.0.0). */
  @NonNull
  abstract String getDisplayVersion();

  /** The bundle version of this build (example: 123). */
  @NonNull
  abstract String getBuildVersion();

  /** The release notes for this build. */
  @Nullable
  abstract String getReleaseNotes();

  /** The binary type for this build. */
  @NonNull
  abstract BinaryType getBinaryType();

  /** Hash of binary of an Android app. */
  @Nullable
  abstract String getCodeHash();

  /** Efficient hash of an Android apk. Used to identify a release. */
  @Nullable
  abstract String getApkHash();

  /**
   * IAS artifact id. This value is inserted into the manifest of APK's installed via Used to map a
   * release to an APK installed via an app bundle.
   */
  @Nullable
  abstract String getIasArtifactId();

  /** Short-lived download URL. */
  @Nullable
  abstract String getDownloadUrl();

  @NonNull
  abstract Builder toBuilder();

  /** Builder for {@link AppDistributionReleaseInternal}. */
  @AutoValue.Builder
  abstract static class Builder {

    @NonNull
    abstract Builder setDisplayVersion(@NonNull String value);

    @NonNull
    abstract Builder setBuildVersion(@NonNull String value);

    @NonNull
    abstract Builder setReleaseNotes(@Nullable String value);

    @NonNull
    abstract Builder setBinaryType(@NonNull BinaryType value);

    @NonNull
    abstract Builder setCodeHash(@NonNull String value);

    @NonNull
    abstract Builder setApkHash(@NonNull String value);

    @NonNull
    abstract Builder setIasArtifactId(@NonNull String value);

    @NonNull
    abstract Builder setDownloadUrl(@NonNull String value);

    @NonNull
    abstract AppDistributionReleaseInternal build();
  }
}
