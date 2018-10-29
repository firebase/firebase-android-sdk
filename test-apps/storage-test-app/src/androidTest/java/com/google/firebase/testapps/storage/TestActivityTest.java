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

package com.google.firebase.testapps.storage;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.*;
import static android.support.test.espresso.assertion.ViewAssertions.*;
import static android.support.test.espresso.matcher.ViewMatchers.*;

import android.support.test.espresso.Espresso;
import android.support.test.espresso.IdlingRegistry;
import android.support.test.espresso.IdlingResource;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.filters.LargeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TestActivityTest {
  @Rule
  public ActivityTestRule<TestActivity> mActivityTestRule =
      new ActivityTestRule<>(TestActivity.class);

  private IdlingResource mIdlingResource;

  @Before
  public void before() {
    mIdlingResource = mActivityTestRule.getActivity().getIdlingResource();
    // To prove that the test fails, omit this call:
    IdlingRegistry.getInstance().register(mIdlingResource);
  }

  @After
  public void unregisterIdlingResource() {
    if (mIdlingResource != null) {
      IdlingRegistry.getInstance().unregister(mIdlingResource);
    }
  }

  @Test
  public void testActivityTest() throws Exception {
    onView(withId(R.id.restaurant)).check(matches(withText("Google MTV")));
  }
}
