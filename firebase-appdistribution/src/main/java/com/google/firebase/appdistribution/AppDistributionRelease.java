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

/**
 * The release information returned by {@link FirebaseAppDistribution#checkForNewRelease} when a new
 * version is available for the signed in tester.
 */
@AutoValue
public abstract class AppDistributionRelease {

  @NonNull
  static Builder builder() {
    return new com.google.firebase.appdistribution.AutoValue_AppDistributionRelease.Builder();
  }

  /** The short bundle version of this build (example: 1.0.0). */
  @NonNull
  public abstract String getDisplayVersion();

  /** The version code of this build (example: 123). */
  @NonNull
  public abstract long getVersionCode();

  /** The release notes for this build. */
  @Nullable
  public abstract String getReleaseNotes();

  /** The binary type for this build. */
  @NonNull
  public abstract BinaryType getBinaryType();

  /** Builder for {@link AppDistributionRelease}. */
  @AutoValue.Builder
  abstract static class Builder {

    @NonNull
    abstract Builder setDisplayVersion(@NonNull String value);

    @NonNull
    abstract Builder setVersionCode(@NonNull long value);

    @NonNull
    abstract Builder setReleaseNotes(@Nullable String value);

    @NonNull
    abstract Builder setBinaryType(@NonNull BinaryType value);

    @NonNull
    abstract AppDistributionRelease build();
  }
}
