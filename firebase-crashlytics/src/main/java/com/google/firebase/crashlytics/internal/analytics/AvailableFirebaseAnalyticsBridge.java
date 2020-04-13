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
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbHandler;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbSource;
import java.util.concurrent.Executor;

/** Analytics bridge implementation for use when Firebase Analytics is available. */
public class AvailableFirebaseAnalyticsBridge implements AnalyticsBridge {

  @NonNull private Task<Void> recordFatalFirebaseEventsTaskChain = Tasks.forResult(null);

  @NonNull private final BreadcrumbSource breadcrumbSource;

  @NonNull private final AppExceptionEventHandler appExceptionEventHandler;

  @NonNull private final Executor executor;

  public AvailableFirebaseAnalyticsBridge(
      @NonNull BreadcrumbSource breadcrumbSource,
      @NonNull AppExceptionEventHandler appExceptionEventHandler,
      @NonNull Executor executor) {
    this.breadcrumbSource = breadcrumbSource;
    this.appExceptionEventHandler = appExceptionEventHandler;
    this.executor = executor;
  }

  @Override
  public void registerBreadcrumbHandler(BreadcrumbHandler breadcrumbHandler) {
    breadcrumbSource.registerBreadcrumbHandler(breadcrumbHandler);
  }

  @Override
  public void recordFatalFirebaseEvent(long timestamp) {
    // Add another iteration of this task to the existing task chain.
    // Keeping each iteration serialized in a chain ensures that we receive the callback for the
    // sent event before trying to send another.
    recordFatalFirebaseEventsTaskChain =
        recordFatalFirebaseEventsTaskChain.continueWithTask(
            executor,
            (previousTask) -> {
              Logger.getLogger().d("Sending Crashlytics app exception event to Firebase Analytics");
              return appExceptionEventHandler.recordAppExceptionEvent(timestamp);
            });
  }

  @Override
  public Task<Void> getAnalyticsTaskChain() {
    return recordFatalFirebaseEventsTaskChain;
  }
}
