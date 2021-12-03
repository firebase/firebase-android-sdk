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

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.filters.MediumTest;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.runner.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.lifecycle.Lifecycle.State;
import android.util.Log;

/**
 * Scrolls a slow RecyclerView all the way to the end, which should generate slow and frozen frame
 * data.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class FirebasePerformanceFragmentScreenTracesTest {

    @Rule
    public ActivityScenarioRule<FirebasePerfScreenTracesActivity> activityRule =
            new ActivityScenarioRule<>(FirebasePerfScreenTracesActivity.class);

    @After
    public void stopActivity_toTriggerSendScreenTraces() {
        activityRule.getScenario().moveToState(State.CREATED);
    }

    @Test
    public void cycleThroughAllFragments() {
        activityRule.getScenario().onActivity(activity -> {
            NavController navController =
                    Navigation.findNavController(activity, R.id.nav_host_fragment_activity_fragment);
            AppCompatActivity appCompatActivityactivity = (AppCompatActivity) activity;
            appCompatActivityactivity.getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
                int[] fragmentIds = new int[] {R.id.navigation_dashboard, R.id.navigation_notifications, R.id.navigation_home};
                int idx = 0;
                @Override
                public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                    super.onFragmentResumed(fm, f);
                    navController.navigate(fragmentIds[idx++]);
                }
            }, true);
        });
    }
}
