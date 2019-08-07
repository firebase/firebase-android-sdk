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
import com.google.auto.value.AutoValue;

/**
 * This class represents a cache entry value in {@link FiidCache}, which contains a Firebase
 * instance id and the cache status of this entry.
 */
@AutoValue
public abstract class FiidCacheEntryValue {

  @NonNull
  public abstract String getFirebaseInstallationId();

  @NonNull
  public abstract FiidCache.CacheStatus getCacheStatus();

  @NonNull
  public abstract String getAuthToken();

  @NonNull
  public abstract String getRefreshToken();

  public abstract long getExpiresInSecs();

  public abstract long getTokenCreationEpochInSecs();

  @NonNull
  public static FiidCacheEntryValue create(
      @NonNull String firebaseInstallationId,
      @NonNull FiidCache.CacheStatus cacheStatus,
      @NonNull String authToken,
      @NonNull String refreshToken,
      long tokenCreationEpochInSecs,
      long expiresInSecs) {
    return new AutoValue_FiidCacheEntryValue(
        firebaseInstallationId,
        cacheStatus,
        authToken,
        refreshToken,
        expiresInSecs,
        tokenCreationEpochInSecs);
  }
}
