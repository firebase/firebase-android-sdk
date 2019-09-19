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
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.withDecorView;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.example.firebase.fiamui.TestConstants.BODY_OPT_LONG;
import static com.example.firebase.fiamui.TestConstants.BODY_OPT_NONE;
import static com.example.firebase.fiamui.TestConstants.BODY_OPT_NORMAL;
import static com.example.firebase.fiamui.TestConstants.BODY_TEXT_LONG;
import static com.example.firebase.fiamui.TestConstants.BODY_TEXT_NORMAL;
import static com.example.firebase.fiamui.TestConstants.BUTTON_TEXT_CANCEL;
import static com.example.firebase.fiamui.TestConstants.BUTTON_TEXT_NONE;
import static com.example.firebase.fiamui.TestConstants.BUTTON_TEXT_NORMAL;
import static com.example.firebase.fiamui.TestConstants.TITLE_TEXT_NORMAL;
import static com.example.firebase.fiamui.TestConstants.TITLE_TEXT_SHORT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.test.espresso.Root;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.action.ViewActions;
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
public class CardTest {

  @Rule
  public ActivityTestRule<MainActivity> mActivityRule = new ActivityTestRule<>(MainActivity.class);

  @Rule public TestName name = new TestName();

  private Matcher<Root> rootMatcher;

  @Before
  public void setUp() {
    rootMatcher = withDecorView(not(is(mActivityRule.getActivity().getWindow().getDecorView())));

    // Set defaults
    setTitle(TITLE_TEXT_NORMAL);
    selectBody(BODY_OPT_NORMAL);
    setPortraitImageSize(600, 400); // Ideal 3:2 aspect ratio
    setLandscapeImageSize(800, 800); // Ideal 1:1 aspect ratio
    setButton(BUTTON_TEXT_NORMAL, R.id.action_button_text);
    setButton(BUTTON_TEXT_CANCEL, R.id.secondary_action_button_text);
  }

  @After
  public void tearDown() {
    ScreenShotter.takeScreenshot(name.getMethodName());

    // If we are NOT in test lab, add a 2s delay after each test. This
    // makes bench testing easier since you can eyeball the results as they run.
    if (!TestUtils.isInTestLab(mActivityRule.getActivity())) {
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    close();
  }

  @Test
  public void testCard() {
    open();

    getView(R.id.card_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_NORMAL)));
    getView(R.id.primary_button).check(matches(withText(BUTTON_TEXT_NORMAL)));
    getView(R.id.secondary_button).check(matches(withText(BUTTON_TEXT_CANCEL)));
  }

  @Test
  public void testCard_LongBody() {
    selectBody(BODY_OPT_LONG);
    open();

    getView(R.id.card_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_LONG)));
    getView(R.id.primary_button).check(matches(withText(BUTTON_TEXT_NORMAL)));
    getView(R.id.secondary_button).check(matches(withText(BUTTON_TEXT_CANCEL)));
  }

  @Test
  public void testCard_NoBody() {
    selectBody(BODY_OPT_NONE);
    open();

    getView(R.id.card_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(not(isDisplayed())));
    getView(R.id.primary_button).check(matches(withText(BUTTON_TEXT_NORMAL)));
    getView(R.id.secondary_button).check(matches(withText(BUTTON_TEXT_CANCEL)));
  }

  @Test
  public void testCard_ShortTitle() {
    setTitle(TITLE_TEXT_SHORT);
    open();

    getView(R.id.card_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_SHORT)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_NORMAL)));
    getView(R.id.primary_button).check(matches(withText(BUTTON_TEXT_NORMAL)));
    getView(R.id.secondary_button).check(matches(withText(BUTTON_TEXT_CANCEL)));
  }

  @Test
  public void testCard_ShortTitleNoBody() {
    setTitle(TITLE_TEXT_SHORT);
    selectBody(BODY_OPT_NONE);
    open();

    getView(R.id.card_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_SHORT)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(not(isDisplayed())));
    getView(R.id.primary_button).check(matches(withText(BUTTON_TEXT_NORMAL)));
    getView(R.id.secondary_button).check(matches(withText(BUTTON_TEXT_CANCEL)));
  }

  @Test
  public void testCard_OneButton() {
    setButton(BUTTON_TEXT_NONE, R.id.secondary_action_button_text);
    open();

    getView(R.id.card_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_NORMAL)));
    getView(R.id.primary_button).check(matches(withText(BUTTON_TEXT_NORMAL)));
    getView(R.id.secondary_button).check(matches(not(isDisplayed())));
  }

  @Test
  public void testCard_TinyImage() {
    setPortraitImageSize(50, 50);
    open();

    getView(R.id.card_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_NORMAL)));
    getView(R.id.primary_button).check(matches(withText(BUTTON_TEXT_NORMAL)));
    getView(R.id.secondary_button).check(matches(withText(BUTTON_TEXT_CANCEL)));
  }

  @Test
  public void testCard_WideImage() {
    setPortraitImageSize(600, 400);
    open();

    getView(R.id.card_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_NORMAL)));
    getView(R.id.primary_button).check(matches(withText(BUTTON_TEXT_NORMAL)));
    getView(R.id.secondary_button).check(matches(withText(BUTTON_TEXT_CANCEL)));
  }

  @Test
  public void testCard_MissingLandscapeImage() {
    setPortraitImageSize(600, 400);
    setLandscapeImageSize(0, 0);
    open();

    getView(R.id.card_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_NORMAL)));
    getView(R.id.primary_button).check(matches(withText(BUTTON_TEXT_NORMAL)));
    getView(R.id.secondary_button).check(matches(withText(BUTTON_TEXT_CANCEL)));
  }

  @Test
  public void testCard_MissingPortraitImage() {
    setPortraitImageSize(0, 0);
    setLandscapeImageSize(800, 800);
    open();

    getView(R.id.card_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_NORMAL)));
    getView(R.id.primary_button).check(matches(withText(BUTTON_TEXT_NORMAL)));
    getView(R.id.secondary_button).check(matches(withText(BUTTON_TEXT_CANCEL)));
  }

  @NonNull
  private ViewInteraction getView(@IdRes int id) {
    return onView(withId(id)).inRoot(rootMatcher);
  }

  private void selectBody(@IdRes int radioButtonId) {
    onView(withId(radioButtonId)).perform(scrollTo()).perform(click());
  }

  private void setTitle(@StringRes int titleRes) {
    if (titleRes >= 0) {
      String title = mActivityRule.getActivity().getString(titleRes);
      onView(withId(R.id.message_title)).perform(scrollTo()).perform(replaceText(title));
    } else {
      onView(withId(R.id.message_title)).perform(scrollTo()).perform(clearText());
    }
  }

  private void setButton(@StringRes int buttonRes, @IdRes int textId) {
    if (buttonRes >= 0) {
      String buttonString = mActivityRule.getActivity().getString(buttonRes);
      onView(withId(textId)).perform(scrollTo()).perform(replaceText(buttonString));
    } else {
      onView(withId(textId)).perform(scrollTo()).perform(clearText());
    }
  }

  private void setPortraitImageSize(int w, int h) {
    onView(withId(R.id.image_width)).perform(scrollTo()).perform(replaceText(Integer.toString(w)));
    onView(withId(R.id.image_height)).perform(scrollTo()).perform(replaceText(Integer.toString(h)));
  }

  private void setLandscapeImageSize(int w, int h) {
    onView(withId(R.id.landscape_image_width))
        .perform(scrollTo())
        .perform(replaceText(Integer.toString(w)));
    onView(withId(R.id.landscape_image_height))
        .perform(scrollTo())
        .perform(replaceText(Integer.toString(h)));
  }

  private void open() {
    onView(withId(R.id.card_fiam)).perform(scrollTo()).perform(click());
    onView(withId(R.id.start)).perform(scrollTo()).perform(click());
  }

  private void close() {
    onView(isRoot()).perform(ViewActions.pressBack());
  }
}
