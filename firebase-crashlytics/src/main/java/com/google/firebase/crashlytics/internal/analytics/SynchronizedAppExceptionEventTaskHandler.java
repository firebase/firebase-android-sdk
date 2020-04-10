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
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.crashlytics.internal.Logger;

class SynchronizedAppExceptionEventTaskHandler implements AppExceptionEventTaskHandler {

  private interface AppExceptionEventCallback {
    void onAppExceptionEvent();
  }

  @NonNull private final Object recordCrashlyticsEventLock = new Object();

  @NonNull private final AppExceptionEventRecorder appExceptionEventRecorder;

  @Nullable private AppExceptionEventCallback appExceptionEventCallback;

  SynchronizedAppExceptionEventTaskHandler(
      @NonNull AppExceptionEventRecorder appExceptionEventRecorder) {
    this.appExceptionEventRecorder = appExceptionEventRecorder;
  }

  @Override
  public Task<Void> createRecordAppExceptionEventTask(long timestamp) {
    synchronized (recordCrashlyticsEventLock) {
      final TaskCompletionSource<Void> source = new TaskCompletionSource<>();

      appExceptionEventCallback =
          () -> {
            appExceptionEventCallback = null;
            Logger.getLogger().d("Crashlytics app exception event sent successfully");
            source.trySetResult(null);
          };

      appExceptionEventRecorder.recordAppExceptionEvent(timestamp);

      return source.getTask();
    }
  }

  @Override
  public void handleRecordedAppExceptionEvent() {
    synchronized (recordCrashlyticsEventLock) {
      if (appExceptionEventCallback != null) {
        Logger.getLogger().d("Received app exception event callback from FA");
        appExceptionEventCallback.onAppExceptionEvent();
      }
    }
  }
}
