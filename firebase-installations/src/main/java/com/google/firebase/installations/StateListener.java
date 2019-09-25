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

interface StateListener {
  boolean onStateReached(PersistedFidEntry persistedFidEntry);
}

class GetIdListener implements StateListener {
  final TaskCompletionSource<String> taskCompletionSource;

  public GetIdListener(TaskCompletionSource<String> taskCompletionSource) {
    this.taskCompletionSource = taskCompletionSource;
  }

  @Override
  public boolean onStateReached(PersistedFidEntry persistedFidEntry) {
    if (persistedFidEntry.isUnregistered() || persistedFidEntry.isRegistered()) {
      taskCompletionSource.setResult(persistedFidEntry.getFirebaseInstallationId());
      return true;
    }

    if (persistedFidEntry.isErrored()) {
      taskCompletionSource.setException(
          new FirebaseInstallationsException(
              "Failed to update client side cache.",
              FirebaseInstallationsException.Status.CLIENT_ERROR));
      return true;
    }
    return false;
  }
}

class GetAuthTokenListener implements StateListener {
  private final Utils utils;
  private final TaskCompletionSource<InstallationTokenResult> resultTaskCompletionSource;

  public GetAuthTokenListener(
      Utils utils, TaskCompletionSource<InstallationTokenResult> resultTaskCompletionSource) {
    this.utils = utils;
    this.resultTaskCompletionSource = resultTaskCompletionSource;
  }

  @Override
  public boolean onStateReached(PersistedFidEntry persistedFidEntry) {
    if (persistedFidEntry.isRegistered() && !utils.isAuthTokenExpired(persistedFidEntry)) {
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
