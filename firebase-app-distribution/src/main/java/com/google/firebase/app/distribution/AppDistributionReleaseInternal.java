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

package com.google.firebase.app.distribution;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;

/**
 * This class represents the AppDistributionRelease object returned by the App Distribution backend
 *
 * <p>It is an immutable value class implemented by AutoValue.
 *
 * @see <a
 *     href="https://github.com/google/auto/tree/master/value">https://github.com/google/auto/tree/master/value</a>
 */
@AutoValue
public abstract class AppDistributionReleaseInternal {

  @NonNull
  public static Builder builder() {
    return new AutoValue_AppDistributionReleaseInternal.Builder();
  }

  /** The short bundle version of this build (example 1.0.0) */
  @NonNull
  public abstract String getDisplayVersion();

  /** The bundle version of this build (example: 123) */
  @NonNull
  public abstract String getBuildVersion();

  /** The release notes for this build */
  @Nullable
  public abstract String getReleaseNotes();

  /** The binary type for this build */
  @NonNull
  public abstract BinaryType getBinaryType();

  /** Hash of binary of an Android app */
  @Nullable
  public abstract String getCodeHash();

  /** Efficient hash of an Android apk. Used to identify a release */
  @Nullable
  public abstract String getApkHash();

  /**
   * IAS artifact id. This value is inserted into the manifest of APK's installed via Used to map a
   * release to an APK installed via an app bundle
   */
  @Nullable
  public abstract String getIasArtifactId();

  /** Short-lived download URL */
  @Nullable
  public abstract String getDownloadUrl();

  /** Builder for {@link AppDistributionReleaseInternal}. */
  @AutoValue.Builder
  public abstract static class Builder {

    @NonNull
    public abstract Builder setDisplayVersion(@NonNull String value);

    @NonNull
    public abstract Builder setBuildVersion(@NonNull String value);

    @NonNull
    public abstract Builder setReleaseNotes(@Nullable String value);

    @NonNull
    public abstract Builder setBinaryType(@NonNull BinaryType value);

    @NonNull
    public abstract Builder setCodeHash(@NonNull String value);

    @NonNull
    public abstract Builder setApkHash(@NonNull String value);

    @NonNull
    public abstract Builder setIasArtifactId(@NonNull String value);

    @NonNull
    public abstract Builder setDownloadUrl(@NonNull String value);

    @NonNull
    public abstract AppDistributionReleaseInternal build();
  }
}
