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
import com.google.firebase.installations.FirebaseInstallationsApi.AuthTokenOption;
import com.google.firebase.installations.local.PersistedFidEntry;

class GetAuthTokenListener implements StateListener {
  private final Utils utils;
  private final TaskCompletionSource<InstallationTokenResult> resultTaskCompletionSource;
  private final String oldAuthToken;
  @AuthTokenOption private final int authTokenOption;

  public GetAuthTokenListener(
      Utils utils,
      TaskCompletionSource<InstallationTokenResult> resultTaskCompletionSource,
      String oldAuthToken,
      @AuthTokenOption int authTokenOption) {
    this.utils = utils;
    this.resultTaskCompletionSource = resultTaskCompletionSource;
    this.oldAuthToken = oldAuthToken;
    this.authTokenOption = authTokenOption;
  }

  @Override
  public boolean onStateReached(PersistedFidEntry persistedFidEntry) {
    // AuthTokenListener state is reached when:
    // 1. If AuthTokenOption is DO_NOT_FORCE_REFRESH : FID is registered and has a valid auth token
    // 2. If AuthTokenOption is FORCE_REFRESH : FID is registered, has a valid new auth token which
    // is not same as the old auth token

    if (persistedFidEntry.isRegistered()
        && !utils.isAuthTokenExpired(persistedFidEntry)
        && (authTokenOption == FirebaseInstallationsApi.DO_NOT_FORCE_REFRESH
            || !oldAuthToken.equals(persistedFidEntry.getAuthToken()))) {
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
