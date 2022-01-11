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

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle.State;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;
import com.google.firebase.testing.fireperf.ui.fast.FastFragment;
import com.google.firebase.testing.fireperf.ui.home.HomeFragment;
import com.google.firebase.testing.fireperf.ui.slow.SlowFragment;
import java.util.Arrays;
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
  public ActivityScenarioRule<FragmentActivity> activityRule =
      new ActivityScenarioRule<>(FragmentActivity.class);

  @Test
  public void scrollAndCycleThroughAllFragments() throws InterruptedException {
    activityRule
        .getScenario()
        .onActivity(
            activity -> {
              ((AppCompatActivity) activity)
                  .getSupportFragmentManager()
                  .registerFragmentLifecycleCallbacks(
                      new FragmentManager.FragmentLifecycleCallbacks() {
                        @Override
                        public void onFragmentResumed(
                            @NonNull FragmentManager fm, @NonNull Fragment f) {
                          super.onFragmentResumed(fm, f);
                          notifyNavigationLock();
                        }
                      },
                      true);
            });
    scrollRecyclerViewToEnd(HomeFragment.NUM_LIST_ITEMS, R.id.rv_numbers_home);
    activityRule.getScenario().onActivity(new NavigateAction(R.id.navigation_fast));
    blockUntilNavigationDone();
    scrollRecyclerViewToEnd(FastFragment.NUM_LIST_ITEMS, R.id.rv_numbers_fast);
    activityRule.getScenario().onActivity(new NavigateAction(R.id.navigation_slow));
    blockUntilNavigationDone();
    scrollRecyclerViewToEnd(SlowFragment.NUM_LIST_ITEMS, R.id.rv_numbers_slow);
    assertThat(activityRule.getScenario().getState())
        .isIn(Arrays.asList(State.CREATED, State.RESUMED));
    activityRule.getScenario().moveToState(State.CREATED);
  }

  private void scrollRecyclerViewToEnd(int itemCount, int viewId) {
    int currItemCount = 0;

    while (currItemCount < itemCount) {
      onView(withId(viewId)).perform(scrollToPosition(currItemCount));
      currItemCount += 5;
    }
  }

  private synchronized void blockUntilNavigationDone() throws InterruptedException {
    wait();
  }

  private synchronized void notifyNavigationLock() {
    notify();
  }

  static class NavigateAction implements ActivityScenario.ActivityAction {
    private final int destinationId;

    public NavigateAction(int destinationId) {
      this.destinationId = destinationId;
    }

    @Override
    public void perform(Activity activity) {
      NavController navController =
          Navigation.findNavController(activity, R.id.nav_host_fragment_activity_fragment);
      navController.navigate(destinationId);
    }
  }
}
