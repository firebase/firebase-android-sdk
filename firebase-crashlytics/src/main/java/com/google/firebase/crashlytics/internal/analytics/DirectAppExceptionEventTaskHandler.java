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

package com.google.firebase.crashlytics.internal.analytics;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

class DirectAppExceptionEventTaskHandler implements AppExceptionEventTaskHandler {

  @NonNull private final AppExceptionEventRecorder appExceptionEventRecorder;

  DirectAppExceptionEventTaskHandler(@NonNull AppExceptionEventRecorder appExceptionEventRecorder) {
    this.appExceptionEventRecorder = appExceptionEventRecorder;
  }

  @Override
  public Task<Void> createRecordAppExceptionEventTask(long timestamp) {
    appExceptionEventRecorder.recordAppExceptionEvent(timestamp);
    return Tasks.forResult(null);
  }

  @Override
  public void handleRecordedAppExceptionEvent() {
    // Not waiting on a callback, so do nothing.
  }
}
