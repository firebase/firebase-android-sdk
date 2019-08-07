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
 * This class represents a cache entry value in {@link FirebaseInstallationIdCache}, which contains
 * a Firebase instance id and the cache status of this entry.
 */
@AutoValue
public abstract class FirebaseInstallationIdCacheEntryValue {

  @NonNull
  public abstract String getFirebaseInstallationId();

  @NonNull
  public abstract FirebaseInstallationIdCache.CacheStatus getCacheStatus();

  @NonNull
  public abstract String getAuthToken();

  @NonNull
  public abstract String getRefreshToken();

  public abstract long getExpiresIn();

  public abstract long getTokenCreationTime();

  @NonNull
  public static FirebaseInstallationIdCacheEntryValue create(
      @NonNull String firebaseInstallationId,
      @NonNull FirebaseInstallationIdCache.CacheStatus cacheStatus,
      @NonNull String authToken,
      @NonNull String refreshToken,
      long tokenCreationTime,
      long expiresIn) {
    return new AutoValue_FirebaseInstallationIdCacheEntryValue(
        firebaseInstallationId, cacheStatus, authToken, refreshToken, expiresIn, tokenCreationTime);
  }
}
