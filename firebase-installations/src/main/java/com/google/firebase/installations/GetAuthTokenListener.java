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

package com.google.firebase.installations;

import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.installations.local.PersistedFidEntry;

class GetAuthTokenListener implements StateListener {
  private final Utils utils;
  private final TaskCompletionSource<InstallationTokenResult> resultTaskCompletionSource;

  public GetAuthTokenListener(
      Utils utils, TaskCompletionSource<InstallationTokenResult> resultTaskCompletionSource) {
    this.utils = utils;
    this.resultTaskCompletionSource = resultTaskCompletionSource;
  }

  @Override
  public boolean onStateReached(
      PersistedFidEntry persistedFidEntry, boolean shouldRefreshAuthToken) {
    // AuthTokenListener state is reached when FID is registered and has a valid auth token
    if (persistedFidEntry.isRegistered()
        && !utils.isAuthTokenExpired(persistedFidEntry)
        && !shouldRefreshAuthToken) {
      resultTaskCompletionSource.setResult(
          InstallationTokenResult.builder()
              .setToken(persistedFidEntry.getAuthToken())
              .setTokenExpirationInSecs(persistedFidEntry.getExpiresInSecs())
              .build());
      return true;
    }

    if (persistedFidEntry.isErrored()) {
      resultTaskCompletionSource.setException(
          new FirebaseInstallationsException(
              "Firebase Installation is not registered.",
              FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR));
      return true;
    }
    return false;
  }
}
