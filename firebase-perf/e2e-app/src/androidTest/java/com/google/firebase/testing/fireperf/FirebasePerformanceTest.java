// Copyright 2020 Google LLC
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

package com.google.firebase.testing.fireperf;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Runs an android test for 20 minutes to allow all trace data to be generated and logged. */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class FirebasePerformanceTest {

  @Rule
  public ActivityScenarioRule<FirebasePerfActivity> rule =
      new ActivityScenarioRule<>(FirebasePerfActivity.class);

  /*
   * Totally generates 32 * 15 = 480 TraceMetric and 32 * 15 = 480 NetworkRequestMetric.
   *
   * Note: During the test we launch the Activity that logs these Performance Events and the app
   * remains Foregrounded during the entire run. As per the Token Bucket Rate Algorithm for
   * Foregrounded app in Fireperf SDK:
   *   - Trace Foreground Capacity: 300 Events (https://bityl.co/3ZPI)
   *   - Network Foreground Capacity: 700 Events (https://bityl.co/3ZPH)
   *   - Burst Capacity: 500 Events (https://bityl.co/3ZPB)
   *   - Rate: 100 Events/Minute (https://bityl.co/3ZPL)
   */
  @Test
  public void waitForBothTracesAndNetworkRequestsBatch()
      throws ExecutionException, InterruptedException {
    final int iterations = 15;
    final List<Future<?>> futureList = new ArrayList<>();
    ActivityScenario scenario = rule.getScenario();
    scenario.onActivity(
        activity -> {
          futureList.add(FireperfUtils.generateTraces(iterations));
          futureList.add(FireperfUtils.generateNetworkRequests(iterations));
        });
    for (Future<?> future : futureList) {
      future.get();
    }
    // Default wait between flushes is 30s.
    Thread.sleep(40 * 1000);
  }
}
