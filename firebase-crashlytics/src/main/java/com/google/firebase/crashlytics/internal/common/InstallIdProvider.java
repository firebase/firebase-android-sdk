// Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;

public interface InstallIdProvider {

  /** Returns an InstallIds that uniquely identifies the app installation on the current device. */
  InstallIds getInstallIds();

  @AutoValue
  abstract class InstallIds {
    @NonNull
    public abstract String getCrashlyticsInstallId();

    @Nullable
    public abstract String getFirebaseInstallationId();

    @Nullable
    public abstract String getFirebaseAuthenticationToken();

    /** Creates an InstallIds with just a crashlyticsInstallId, no firebaseInstallationId. */
    public static InstallIds createWithoutFid(String crashlyticsInstallId) {
      return new AutoValue_InstallIdProvider_InstallIds(
          crashlyticsInstallId,
          /* firebaseInstallationId= */ null,
          /* firebaseAuthenticationToken= */ null);
    }

    static InstallIds create(
        String crashlyticsInstallId, FirebaseInstallationId firebaseInstallationId) {
      return new AutoValue_InstallIdProvider_InstallIds(
          crashlyticsInstallId,
          firebaseInstallationId.getFid(),
          firebaseInstallationId.getAuthToken());
    }
  }
}
