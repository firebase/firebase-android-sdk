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
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import com.google.firebase.StartupTime;
import com.google.firebase.perf.application.AppStateMonitor;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.metrics.AppStartTrace;
import com.google.firebase.perf.session.SessionManager;
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
      FirebaseApp app, @Nullable StartupTime startupTime, Executor uiExecutor) {
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

    // TODO: Bring back Firebase Sessions dependency to watch for updates to sessions.

    // In the case of cold start, we create a session and start collecting gauges as early as
    // possible.
    // There is code in SessionManager that prevents us from resetting the session twice in case
    // of app cold start.
    SessionManager.getInstance().initializeGaugeCollection();
  }
}
