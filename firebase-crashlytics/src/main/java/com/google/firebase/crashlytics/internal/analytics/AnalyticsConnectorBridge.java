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

package com.google.firebase.crashlytics.internal.analytics;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorHandle;
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorListener;
import com.google.firebase.crashlytics.internal.Logger;
import java.util.concurrent.Executor;
import org.json.JSONException;
import org.json.JSONObject;

public class AnalyticsConnectorBridge implements AnalyticsBridge {

  public interface BreadcrumbHandler {
    void dropBreadcrumb(String breadcrumb);
  }

  @NonNull
  private final Executor executor;

  @Nullable
  private final AnalyticsConnectorHandle analyticsConnectorHandle;

  @NonNull
  private final AnalyticsListener analyticsListener;

  @NonNull
  private Task<Void> recordFatalFirebaseEventsTaskChain = Tasks.forResult(null);

  @NonNull
  private final AppExceptionEventTaskHandler appExceptionEventTaskHandler;

  public AnalyticsConnectorBridge(
      @NonNull AnalyticsConnector analyticsConnector, @NonNull AnalyticsListener analyticsListener, @NonNull Executor executor) {
    final AppExceptionEventRecorder appExceptionEventRecorder = new AnalyticsConnectorAppExceptionEventRecorder(analyticsConnector);
    this.executor = executor;
    // TODO: Pull this out for mocking
    this.analyticsConnectorHandle = AnalyticsListener.subscribeToAnalyticsEvents(analyticsConnector, analyticsListener);
    this.analyticsListener = analyticsListener;
    this.appExceptionEventTaskHandler = (analyticsConnectorHandle == null) ? new DirectAppExceptionEventTaskHandler(appExceptionEventRecorder) : new SynchronizedAppExceptionEventTaskHandler(appExceptionEventRecorder);
    this.analyticsListener.setAppExceptionEventTaskHandler(appExceptionEventTaskHandler);
  }

  @Override
  public void registerBreadcrumbHandler(BreadcrumbHandler handler) {
    boolean breadcrumbsRegistered = false;
    if (analyticsConnectorHandle != null) {
      this.analyticsListener.setBreadcrumbHandler(handler);
      breadcrumbsRegistered = true;
    }
    Logger.getLogger()
        .d("Registered Firebase Analytics event listener for breadcrumbs: " + breadcrumbsRegistered);
  }

  /**
   * Send an App Exception event to Firebase Analytics.
   */
  public void recordFatalFirebaseEvent(long timestamp) {
    // Add another iteration of this task to the existing task chain.
    // Keeping each iteration serialized in a chain ensures that we receive the callback for the
    // sent event before trying to send another.
    recordFatalFirebaseEventsTaskChain =
        recordFatalFirebaseEventsTaskChain.continueWithTask(
            executor,
            (previousTask) -> {
              Logger.getLogger().d("Sending Crashlytics app exception event to Firebase Analytics");
              return appExceptionEventTaskHandler.createRecordAppExceptionEventTask(timestamp);
            });
  }


  @NonNull
  public Task<Void> getAnalyticsTaskChain() {
    return recordFatalFirebaseEventsTaskChain;
  }
}
