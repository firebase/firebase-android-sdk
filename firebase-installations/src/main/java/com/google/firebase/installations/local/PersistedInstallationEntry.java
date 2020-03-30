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
import com.google.firebase.installations.local.PersistedInstallation.RegistrationStatus;

/**
 * This class represents a persisted fid entry in {@link PersistedInstallation}, which contains a
 * few Firebase Installation attributes and the persisted status of this entry.
 *
 * @hide
 */
@AutoValue
public abstract class PersistedInstallationEntry {

  @Nullable
  public abstract String getFirebaseInstallationId();

  @NonNull
  public abstract PersistedInstallation.RegistrationStatus getRegistrationStatus();

  @Nullable
  public abstract String getAuthToken();

  @Nullable
  public abstract String getRefreshToken();

  public abstract long getExpiresInSecs();

  public abstract long getTokenCreationEpochInSecs();

  @Nullable
  public abstract String getFisError();

  @NonNull
  public static PersistedInstallationEntry INSTANCE = PersistedInstallationEntry.builder().build();

  public boolean isRegistered() {
    return getRegistrationStatus() == PersistedInstallation.RegistrationStatus.REGISTERED;
  }

  public boolean isErrored() {
    return getRegistrationStatus() == PersistedInstallation.RegistrationStatus.REGISTER_ERROR;
  }

  public boolean isUnregistered() {
    return getRegistrationStatus() == PersistedInstallation.RegistrationStatus.UNREGISTERED;
  }

  public boolean isNotGenerated() {
    return getRegistrationStatus() == PersistedInstallation.RegistrationStatus.NOT_GENERATED
        || getRegistrationStatus() == RegistrationStatus.ATTEMPT_MIGRATION;
  }

  public boolean shouldAttemptMigration() {
    return getRegistrationStatus() == RegistrationStatus.ATTEMPT_MIGRATION;
  }

  @NonNull
  public PersistedInstallationEntry withUnregisteredFid(@NonNull String fid) {
    return toBuilder()
        .setFirebaseInstallationId(fid)
        .setRegistrationStatus(RegistrationStatus.UNREGISTERED)
        .build();
  }

  @NonNull
  public PersistedInstallationEntry withRegisteredFid(
      @NonNull String fid,
      @NonNull String refreshToken,
      long creationTime,
      @Nullable String authToken,
      long authTokenExpiration) {
    return toBuilder()
        .setFirebaseInstallationId(fid)
        .setRegistrationStatus(RegistrationStatus.REGISTERED)
        .setAuthToken(authToken)
        .setRefreshToken(refreshToken)
        .setExpiresInSecs(authTokenExpiration)
        .setTokenCreationEpochInSecs(creationTime)
        .build();
  }

  @NonNull
  public PersistedInstallationEntry withFisError(@NonNull String message) {
    return toBuilder()
        .setFisError(message)
        .setRegistrationStatus(RegistrationStatus.REGISTER_ERROR)
        .build();
  }

  @NonNull
  public PersistedInstallationEntry withNoGeneratedFid() {
    return toBuilder().setRegistrationStatus(RegistrationStatus.NOT_GENERATED).build();
  }

  @NonNull
  public PersistedInstallationEntry withAuthToken(
      @NonNull String authToken, long authTokenExpiration, long creationTime) {
    return toBuilder()
        .setAuthToken(authToken)
        .setExpiresInSecs(authTokenExpiration)
        .setTokenCreationEpochInSecs(creationTime)
        .build();
  }

  @NonNull
  public PersistedInstallationEntry withClearedAuthToken() {
    return toBuilder().setAuthToken(null).build();
  }

  @NonNull
  public abstract Builder toBuilder();

  /** Returns a default Builder object to create an PersistedInstallationEntry object */
  @NonNull
  public static PersistedInstallationEntry.Builder builder() {
    return new AutoValue_PersistedInstallationEntry.Builder()
        .setTokenCreationEpochInSecs(0)
        .setRegistrationStatus(RegistrationStatus.ATTEMPT_MIGRATION)
        .setExpiresInSecs(0);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public abstract Builder setFirebaseInstallationId(@NonNull String value);

    @NonNull
    public abstract Builder setRegistrationStatus(
        @NonNull PersistedInstallation.RegistrationStatus value);

    @NonNull
    public abstract Builder setAuthToken(@Nullable String value);

    @NonNull
    public abstract Builder setRefreshToken(@Nullable String value);

    @NonNull
    public abstract Builder setExpiresInSecs(long value);

    @NonNull
    public abstract Builder setTokenCreationEpochInSecs(long value);

    @NonNull
    public abstract Builder setFisError(@Nullable String value);

    @NonNull
    public abstract PersistedInstallationEntry build();
  }
}
