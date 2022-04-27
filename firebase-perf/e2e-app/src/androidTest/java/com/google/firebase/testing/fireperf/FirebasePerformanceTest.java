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

import android.content.Context;
import android.content.Intent;
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Runs an android test for 20 minutes to allow all trace data to be generated and logged. */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class FirebasePerformanceTest {

  @Rule
  public ActivityTestRule<FirebasePerfActivity> activityRule =
      new ActivityTestRule<>(
          FirebasePerfActivity.class, /* initialTouchMode= */ true, /* launchActivity= */ false);

  private Intent getTraceAndNetworkEventIntent() {
    Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Intent intent = new Intent(targetContext, FirebasePerfActivity.class);
    intent.putExtra(FirebasePerfActivity.START_TRACES, true);
    intent.putExtra(FirebasePerfActivity.START_NETWORK_REQUESTS, true);
    intent.putExtra(FirebasePerfActivity.NUMBER_OF_TRACE_ITERATIONS, 15);
    intent.putExtra(FirebasePerfActivity.NUMBER_OF_NETWORK_ITERATIONS, 15);

    return intent;
  }

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
  public void waitForBothTracesAndNetworkRequestsBatch() throws InterruptedException {
    Intent intent = getTraceAndNetworkEventIntent();
    activityRule.launchActivity(intent);
    FireperfUtils.blockUntilAllEventsSent();
  }
}
