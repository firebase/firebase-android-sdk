/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googletest.firebase.remoteconfig.bandwagoner;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

import android.content.Context;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * All the Firebase Remote Config (FRC) SDK integration tests that can be run with just the 3P API.
 *
 * @author Miraziz Yusupov
 */
@RunWith(AndroidJUnit4.class)
public class BandwagonerEspressoTest {
  private static final String KEY_FOR_STRING = "string_key";
  private static final String STRING_TYPE = "String";
  private static final String STRING_STATIC_DEFAULT_VALUE = "";
  private static final String STRING_REMOTE_DEFAULT_VALUE = "default_v1_remote_string_value";

  private IdlingResource idlingResource;

  @Rule
  public ActivityTestRule<MainActivity> activityTestRule =
      new ActivityTestRule<>(MainActivity.class);

  @Before
  public void setUp() {
    idlingResource = IdlingResourceManager.getInstance();
    IdlingRegistry.getInstance().register(idlingResource);

    onView(withId(R.id.reset_frc_button)).perform(click());
  }

  @Test
  public void getDataTypes_returnsStaticDefaults() throws InterruptedException {
    verifyKeyValuePair(KEY_FOR_STRING, STRING_TYPE, "");
  }

  @Test
  public void activateFetchedWithoutFetching_activateFetchedReturnsFalse()
      throws InterruptedException {

    onView(withId(R.id.activate_fetched_button)).perform(click());
    onView(withId(R.id.api_call_results))
        .check(matches(withText(allOf(containsString("activate"), containsString("false!")))));
  }

  @Test
  public void fetchAndActivateFetchedTwice_activateFetchedReturnsFalse()
      throws InterruptedException {

    onView(withId(R.id.fetch_button)).perform(click());

    onView(withId(R.id.activate_fetched_button)).perform(click());
    onView(withId(R.id.api_call_results))
        .check(matches(withText(allOf(containsString("activate"), containsString("successful!")))));

    onView(withId(R.id.activate_fetched_button)).perform(click());
    onView(withId(R.id.api_call_results))
        .check(matches(withText(allOf(containsString("activate"), containsString("false!")))));
  }

  @Test
  public void fetchAndGetString_returnsStaticDefault() throws InterruptedException {

    verifyKeyValuePair(KEY_FOR_STRING, STRING_TYPE, STRING_STATIC_DEFAULT_VALUE);

    onView(withId(R.id.fetch_button)).perform(click());

    verifyKeyValuePair(KEY_FOR_STRING, STRING_TYPE, STRING_STATIC_DEFAULT_VALUE);
  }

  @Test
  public void fetchActivateFetchedAndGetString_returnsRemoteValue() throws InterruptedException {

    verifyKeyValuePair(KEY_FOR_STRING, STRING_TYPE, STRING_STATIC_DEFAULT_VALUE);

    onView(withId(R.id.fetch_button)).perform(click());
    onView(withId(R.id.activate_fetched_button)).perform(click());

    verifyKeyValuePair(KEY_FOR_STRING, STRING_TYPE, STRING_REMOTE_DEFAULT_VALUE);
  }

  private void verifyKeyValuePair(String key, String dataType, String expectedValue)
      throws InterruptedException {

    onView(withId(R.id.frc_parameter_key)).perform(click(), clearText(), typeText(key));
    onView(withText("Get " + dataType)).perform(click());

    String expectedResult = String.format("%s: (%s, %s)", dataType, key, expectedValue);
    onView(withId(R.id.frc_parameter_value))
        .check(matches(withText(containsString(expectedResult))));
  }

  @After
  public void cleanUp() {
    clearIdlingResources();
    clearCacheFiles();
  }

  private void clearIdlingResources() {
    IdlingRegistry.getInstance().unregister(idlingResource);
  }

  private void clearCacheFiles() {
    Context context = activityTestRule.getActivity().getApplicationContext();

    for (String fileName : context.fileList()) {
      context.deleteFile(fileName);
    }
  }
}
