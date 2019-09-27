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

class GetIdListener implements StateListener {
  final TaskCompletionSource<String> taskCompletionSource;

  public GetIdListener(TaskCompletionSource<String> taskCompletionSource) {
    this.taskCompletionSource = taskCompletionSource;
  }

  @Override
  public boolean onStateReached(PersistedFidEntry persistedFidEntry) {
    if (persistedFidEntry.isUnregistered() || persistedFidEntry.isRegistered()) {
      taskCompletionSource.trySetResult(persistedFidEntry.getFirebaseInstallationId());
      return true;
    }
    return false;
  }

  @Override
  public boolean onException(PersistedFidEntry persistedFidEntry, Exception exception) {
    if (persistedFidEntry.isErrored()) {
      taskCompletionSource.trySetException(exception);
      return true;
    }
    return false;
  }
}
