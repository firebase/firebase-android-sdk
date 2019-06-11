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
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.example.firebase.fiamui.TestConstants.BODY_OPT_LONG;
import static com.example.firebase.fiamui.TestConstants.BODY_OPT_NONE;
import static com.example.firebase.fiamui.TestConstants.BODY_OPT_NORMAL;
import static com.example.firebase.fiamui.TestConstants.BODY_TEXT_LONG;
import static com.example.firebase.fiamui.TestConstants.BODY_TEXT_NORMAL;
import static com.example.firebase.fiamui.TestConstants.BUTTON_TEXT_NONE;
import static com.example.firebase.fiamui.TestConstants.BUTTON_TEXT_NORMAL;
import static com.example.firebase.fiamui.TestConstants.TITLE_TEXT_NONE;
import static com.example.firebase.fiamui.TestConstants.TITLE_TEXT_NORMAL;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
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
public class ModalTest {

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
    setImageSize(800, 800);
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
  public void testModal() {
    open();

    getView(R.id.modal_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_NORMAL)));
    getView(R.id.button).check(matches(withText(BUTTON_TEXT_NORMAL)));
  }

  @Test
  public void testModal_LongBody() {
    selectBody(BODY_OPT_LONG);
    open();

    getView(R.id.modal_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_LONG)));
    getView(R.id.button).check(matches(withText(BUTTON_TEXT_NORMAL)));
  }

  @Test
  public void testModal_NoBody() {
    selectBody(BODY_OPT_NONE);
    open();

    getView(R.id.modal_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(not(isDisplayed())));
    getView(R.id.button).check(matches(withText(BUTTON_TEXT_NORMAL)));
  }

  @Test
  public void testModal_NoTitle() {
    setTitle(TITLE_TEXT_NONE);
    open();

    getView(R.id.modal_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(not(isDisplayed())));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_NORMAL)));
    getView(R.id.button).check(matches(withText(BUTTON_TEXT_NORMAL)));
  }

  @Test
  public void testModal_NoImage() {
    setImageSize(0, 0);
    open();

    getView(R.id.modal_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(isDisplayed()));
    getView(R.id.image_view).check(matches(not(isDisplayed())));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_NORMAL)));
    getView(R.id.button).check(matches(withText(BUTTON_TEXT_NORMAL)));
  }

  @Test
  public void testModal_NoImage_NoButton() {
    setImageSize(0, 0);
    setButton(BUTTON_TEXT_NONE);
    open();

    getView(R.id.modal_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(isDisplayed()));
    getView(R.id.image_view).check(matches(not(isDisplayed())));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_NORMAL)));
    getView(R.id.button).check(matches(not(isDisplayed())));
  }

  @Test
  public void testModal_NoButton() {
    setButton(BUTTON_TEXT_NONE);
    open();

    getView(R.id.modal_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_NORMAL)));
    getView(R.id.button).check(matches(not(isDisplayed())));
  }

  @Test
  public void testModal_NoTitleNoBody() {
    setTitle(TITLE_TEXT_NONE);
    selectBody(BODY_OPT_NONE);
    open();

    getView(R.id.modal_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(not(isDisplayed())));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(not(isDisplayed())));
    getView(R.id.button).check(matches(withText(BUTTON_TEXT_NORMAL)));
  }

  @Test
  public void testModal_TinyImage() {
    setImageSize(50, 50);
    open();

    getView(R.id.modal_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_NORMAL)));
    getView(R.id.button).check(matches(withText(BUTTON_TEXT_NORMAL)));
  }

  @Test
  public void testModal_TallImage_LongBody() {
    setImageSize(100, 1200);
    selectBody(BODY_OPT_LONG);
    open();

    getView(R.id.modal_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_LONG)));
    getView(R.id.button).check(matches(withText(BUTTON_TEXT_NORMAL)));
  }

  @Test
  public void testModal_WideImage() {
    setImageSize(1500, 500);
    open();

    getView(R.id.modal_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_NORMAL)));
    getView(R.id.button).check(matches(withText(BUTTON_TEXT_NORMAL)));
  }

  @Test
  public void testModal_TallImage() {
    setImageSize(500, 1500);
    open();

    getView(R.id.modal_root).check(matches(isDisplayed()));
    getView(R.id.message_title).check(matches(withText(TITLE_TEXT_NORMAL)));
    getView(R.id.image_view).check(matches(isDisplayed()));
    getView(R.id.message_body).check(matches(withText(BODY_TEXT_NORMAL)));
    getView(R.id.button).check(matches(withText(BUTTON_TEXT_NORMAL)));
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

  private void setButton(@StringRes int buttonRes) {
    if (buttonRes >= 0) {
      String buttonString = mActivityRule.getActivity().getString(buttonRes);
      onView(withId(R.id.action_button_text))
          .perform(scrollTo())
          .perform(replaceText(buttonString));
    } else {
      onView(withId(R.id.action_button_text)).perform(scrollTo()).perform(clearText());
    }
  }

  private void setImageSize(int w, int h) {
    onView(withId(R.id.image_width)).perform(scrollTo()).perform(replaceText(Integer.toString(w)));
    onView(withId(R.id.image_height)).perform(scrollTo()).perform(replaceText(Integer.toString(h)));
  }

  private void open() {
    onView(withId(R.id.modal_fiam)).perform(scrollTo()).perform(click());
    onView(withId(R.id.start)).perform(scrollTo()).perform(click());
  }

  private void close() {
    getView(R.id.collapse_button).perform(click());
  }
}
