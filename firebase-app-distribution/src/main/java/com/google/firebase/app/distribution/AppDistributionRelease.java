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
 * This class represents the AppDistributionRelease object returned by checkForUpdate() and
 * updateToLatestRelease()
 *
 * <p>It is an immutable value class implemented by AutoValue.
 *
 * @see <a
 *     href="https://github.com/google/auto/tree/master/value">https://github.com/google/auto/tree/master/value</a>
 */
@AutoValue
public abstract class AppDistributionRelease {

  @NonNull
  public static Builder builder() {
    return new com.google.firebase.app.distribution.AutoValue_AppDistributionRelease.Builder();
  }

  /** The short bundle version of this build (example 1.0.0) */
  @NonNull
  public abstract String getDisplayVersion();

  /** The version code of this build (example: 123) */
  @NonNull
  public abstract long getVersionCode();

  /** The release notes for this build */
  @Nullable
  public abstract String getReleaseNotes();

  /** The binary type for this build */
  @NonNull
  public abstract BinaryType getBinaryType();

  /** Builder for {@link AppDistributionRelease}. */
  @AutoValue.Builder
  public abstract static class Builder {

    @NonNull
    public abstract Builder setDisplayVersion(@NonNull String value);

    @NonNull
    public abstract Builder setVersionCode(@NonNull long value);

    @NonNull
    public abstract Builder setReleaseNotes(@Nullable String value);

    @NonNull
    public abstract Builder setBinaryType(@NonNull BinaryType value);

    @NonNull
    public abstract AppDistributionRelease build();
  }
}
