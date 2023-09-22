// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import com.google.firebase.StartupTime;
import com.google.firebase.perf.application.AppStateMonitor;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.metrics.AppStartTrace;
import com.google.firebase.perf.session.PerfSession;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.sessions.FirebaseSessions;
import com.google.firebase.sessions.api.SessionSubscriber;
import java.util.concurrent.Executor;

/**
 * The Firebase Performance early initialization.
 *
 * <p>Responsible for initializing the AppStartTrace, and early initialization of ConfigResolver
 *
 * @hide
 */
public class FirebasePerfEarly {

  public FirebasePerfEarly(
      FirebaseApp app,
      FirebaseSessions firebaseSessions,
      @Nullable StartupTime startupTime,
      Executor uiExecutor) {
    Context context = app.getApplicationContext();

    // Initialize ConfigResolver early for accessing device caching layer.
    ConfigResolver configResolver = ConfigResolver.getInstance();
    configResolver.setApplicationContext(context);

    AppStateMonitor appStateMonitor = AppStateMonitor.getInstance();
    appStateMonitor.registerActivityLifecycleCallbacks(context);
    appStateMonitor.registerForAppColdStart(new FirebasePerformanceInitializer());

    if (startupTime != null) {
      AppStartTrace appStartTrace = AppStartTrace.getInstance();
      appStartTrace.registerActivityLifecycleCallbacks(context);
      uiExecutor.execute(new AppStartTrace.StartFromBackgroundRunnable(appStartTrace));
    }

    // Register with Firebase sessions to receive updates about session changes.
    firebaseSessions.register(
        new SessionSubscriber() {
          @Override
          public void onSessionChanged(@NonNull SessionDetails sessionDetails) {
            PerfSession perfSession = PerfSession.createWithId(sessionDetails.getSessionId());
            SessionManager.getInstance().updatePerfSession(perfSession);
          }

          @Override
          public boolean isDataCollectionEnabled() {
            // If there is no cached config data available for data collection, be conservative.
            // Return false.
            if (!configResolver.isCollectionEnabledConfigValueAvailable()) {
              return false;
            }
            return ConfigResolver.getInstance().isPerformanceMonitoringEnabled();
          }

          @NonNull
          @Override
          public Name getSessionSubscriberName() {
            return SessionSubscriber.Name.PERFORMANCE;
          }
        });

    // In the case of cold start, we create a session and start collecting gauges as early as
    // possible.
    // There is code in SessionManager that prevents us from resetting the session twice in case
    // of app cold start.
    SessionManager.getInstance().initializeGaugeCollection();
  }
}
