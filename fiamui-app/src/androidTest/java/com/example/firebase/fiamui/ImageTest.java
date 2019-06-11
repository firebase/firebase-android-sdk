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

package com.example.firebase.fiamui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.withDecorView;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.Is.is;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.test.espresso.Root;
import androidx.test.espresso.ViewInteraction;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ImageTest {

  @Rule
  public ActivityTestRule<MainActivity> mActivityRule = new ActivityTestRule<>(MainActivity.class);

  @Rule public TestName name = new TestName();

  private Matcher<Root> rootMatcher;

  @Before
  public void setUp() {
    rootMatcher = withDecorView(not(is(mActivityRule.getActivity().getWindow().getDecorView())));
  }

  @After
  public void tearDown() {
    ScreenShotter.takeScreenshot(name.getMethodName());
    close();
  }

  @Test
  public void testImage() {
    open();

    getView(R.id.image_root).check(matches(isDisplayed()));
  }

  @NonNull
  private ViewInteraction getView(@IdRes int id) {
    return onView(withId(id)).inRoot(rootMatcher);
  }

  private void open() {
    onView(withId(R.id.image_fiam)).perform(scrollTo()).perform(click());
    onView(withId(R.id.start)).perform(scrollTo()).perform(click());
  }

  private void close() {
    getView(R.id.collapse_button).perform(click());
  }
}
