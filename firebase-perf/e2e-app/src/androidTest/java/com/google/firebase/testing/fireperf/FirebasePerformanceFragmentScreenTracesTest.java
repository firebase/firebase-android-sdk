// Copyright 2021 Google LLC
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
import static com.google.common.truth.Truth.assertThat;

import androidx.lifecycle.Lifecycle.State;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import com.google.firebase.testing.fireperf.ui.fast.FastFragment;
import com.google.firebase.testing.fireperf.ui.home.HomeFragment;
import com.google.firebase.testing.fireperf.ui.slow.SlowFragment;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Scrolls a slow RecyclerView all the way to the end, which should generate slow and frozen frame
 * data.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class FirebasePerformanceFragmentScreenTracesTest {

  @Rule
  public ActivityScenarioRule<FirebasePerfFragmentsActivity> activityRule =
      new ActivityScenarioRule<>(FirebasePerfFragmentsActivity.class);

  @Test
  public void scrollAndCycleThroughAllFragments() throws InterruptedException {
    ActivityScenario scenario = activityRule.getScenario();
    scrollRecyclerViewToEnd(HomeFragment.NUM_LIST_ITEMS, R.id.rv_numbers_home);
    scenario.onActivity(new NavigateAction(R.id.navigation_fast));
    scrollRecyclerViewToEnd(FastFragment.NUM_LIST_ITEMS, R.id.rv_numbers_fast);
    scenario.onActivity(new NavigateAction(R.id.navigation_slow));
    scrollRecyclerViewToEnd(SlowFragment.NUM_LIST_ITEMS, R.id.rv_numbers_slow);
    assertThat(scenario.getState()).isEqualTo(State.RESUMED);
    scenario.moveToState(State.STARTED).moveToState(State.CREATED);

    // End Activity screen trace by relaunching the activity to ensure the screen trace is sent.
    scenario.launch(FirebasePerfFragmentsActivity.class);
    // Default wait between flushes is 30s.
    Thread.sleep(40 * 1000);
  }

  private void scrollRecyclerViewToEnd(int itemCount, int viewId) {
    int currItemCount = 0;

    while (currItemCount < itemCount) {
      onView(withId(viewId)).perform(scrollToPosition(currItemCount));
      currItemCount += 5;
    }
  }

  static class NavigateAction
      implements ActivityScenario.ActivityAction<FirebasePerfFragmentsActivity> {
    private final int destinationId;

    public NavigateAction(int destinationId) {
      this.destinationId = destinationId;
    }

    @Override
    public void perform(FirebasePerfFragmentsActivity activity) {
      NavController navController =
          Navigation.findNavController(activity, R.id.nav_host_fragment_activity_fragment);
      navController.navigate(destinationId);
    }
  }
}
