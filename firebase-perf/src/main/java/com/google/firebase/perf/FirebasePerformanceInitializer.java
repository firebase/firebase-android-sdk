// Copyright 2021 Google LLC
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

package com.google.firebase.perf;

import com.google.firebase.perf.application.AppStateMonitor;
import com.google.firebase.perf.logging.AndroidLogger;

/**
 * FirebasePerformanceInitializer to initialize FirebasePerformance during app cold start
 *
 * @hide
 */
public final class FirebasePerformanceInitializer implements AppStateMonitor.AppColdStartCallback {
  private static final AndroidLogger logger = AndroidLogger.getInstance();

  @Override
  public void onAppColdStart() {
    // Initialize FirebasePerformance when app cold starts.
    try {
      FirebasePerformance.getInstance();
    } catch (IllegalStateException ex) {
      logger.warn(
          "FirebaseApp is not initialized. Firebase Performance will not be collecting any "
              + "performance metrics until initialized. %s",
          ex);
    }
  }
}
