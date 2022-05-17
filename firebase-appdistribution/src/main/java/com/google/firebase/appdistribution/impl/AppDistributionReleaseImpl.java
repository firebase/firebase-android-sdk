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
import com.google.firebase.appdistribution.AppDistributionRelease;
import com.google.firebase.appdistribution.BinaryType;

/** The default implementation of {@link AppDistributionRelease}. */
@AutoValue
abstract class AppDistributionReleaseImpl implements AppDistributionRelease {

  @NonNull
  static Builder builder() {
    return new AutoValue_AppDistributionReleaseImpl.Builder();
  }

  /** Returns the short bundle version of this build (example: 1.0.0). */
  @Override
  @NonNull
  public abstract String getDisplayVersion();

  /** Returns the version code of this build (example: 123). */
  @Override
  @NonNull
  public abstract long getVersionCode();

  /** Returns the release notes for this build. */
  @Override
  @Nullable
  public abstract String getReleaseNotes();

  /** Returns the binary type for this build. */
  @Override
  @NonNull
  public abstract BinaryType getBinaryType();

  /** Builder for {@link AppDistributionReleaseImpl}. */
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
    abstract AppDistributionReleaseImpl build();
  }
}
