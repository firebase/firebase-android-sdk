// Copyright 2019 Google LLC
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

package com.google.firebase.installations.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;

/**
 * This class represents a cache entry value in {@link PersistedFid}, which contains a Firebase
 * instance id and the cache status of this entry.
 */
@AutoValue
public abstract class PersistedFidEntryValue {

  @NonNull
  public abstract String getFirebaseInstallationId();

  @NonNull
  public abstract PersistedFid.PersistedStatus getPersistedStatus();

  @Nullable
  public abstract String getAuthToken();

  @Nullable
  public abstract String getRefreshToken();

  public abstract long getExpiresInSecs();

  public abstract long getTokenCreationEpochInSecs();

  @NonNull
  public abstract Builder toBuilder();

  /** Returns a default Builder object to create an InstallationResponse object */
  @NonNull
  public static PersistedFidEntryValue.Builder builder() {
    return new AutoValue_PersistedFidEntryValue.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public abstract Builder setFirebaseInstallationId(@NonNull String value);

    @NonNull
    public abstract Builder setPersistedStatus(@NonNull PersistedFid.PersistedStatus value);

    @NonNull
    public abstract Builder setAuthToken(@Nullable String value);

    @NonNull
    public abstract Builder setRefreshToken(@Nullable String value);

    @NonNull
    public abstract Builder setExpiresInSecs(long value);

    @NonNull
    public abstract Builder setTokenCreationEpochInSecs(long value);

    @NonNull
    public abstract PersistedFidEntryValue build();
  }
}
