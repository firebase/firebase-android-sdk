// Copyright 2018 Google LLC
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

package com.google.firebase.gradle.plugins.ci.metrics;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.logging.Logger;

/**
 * Build listener that waits for Stackdriver to export metrics before exiting.
 *
 * <p>Stackdriver exporter is implemented in such a way that it exports metrics on a periodic basis,
 * with period being configurable. This means that, when the build finishes and exits, it is highly
 * likely that there are unexported metrics in memory. For this reason we have this build listener
 * that makes the gradle process sleep for the duration of the configured export period to make sure
 * metrics get exported.
 *
 * @see <a
 *     href="https://opencensus.io/exporters/supported-exporters/java/stackdriver-stats/">Opencensus
 *     docs</a>
 */
class DrainingBuildListener extends BuildAdapter {
  private final long sleepDuration;
  private final Logger logger;

  DrainingBuildListener(long sleepDuration, Logger logger) {
    this.sleepDuration = sleepDuration;
    this.logger = logger;
  }

  @Override
  public void buildFinished(BuildResult result) {
    try {
      logger.lifecycle("Draining metrics to Stackdriver.");
      Thread.sleep(sleepDuration);
    } catch (InterruptedException e) {
      // Restore the interrupted status
      Thread.currentThread().interrupt();
    }
  }
}
