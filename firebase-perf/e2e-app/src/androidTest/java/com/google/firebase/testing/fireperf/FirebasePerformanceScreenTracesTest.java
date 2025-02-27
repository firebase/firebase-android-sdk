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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.contrib.RecyclerViewActions.scrollToPosition;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Scrolls a slow RecyclerView all the way to the end, which should generate slow and frozen frame
 * data.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class FirebasePerformanceScreenTracesTest {
  private static final int LAUNCH_TIMEOUT = 5000;

  @Rule
  public ActivityScenarioRule<FirebasePerfScreenTracesActivity> activityRule =
      new ActivityScenarioRule<>(FirebasePerfScreenTracesActivity.class);

  @Test
  public void scrollRecyclerViewToEnd() throws Exception {
    ActivityScenario scenario = activityRule.getScenario();
    int itemCount = FirebasePerfScreenTracesActivity.NUM_LIST_ITEMS;
    int currItemCount = 0;

    while (currItemCount < itemCount) {
      onView(withId(R.id.rv_numbers)).perform(scrollToPosition(currItemCount));
      currItemCount += 5;
    }
    // End Activity screen trace by switching to another Activity
    scenario.launch(FirebasePerfScreenTracesActivity.class);
    // Default wait between flushes is 30s.
    Thread.sleep(40 * 1000);
  }
}
