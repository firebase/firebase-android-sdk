// Copyright 2026 Google LLC
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

package com.google.firebase.inappmessaging.display.internal.bindingwrappers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.firebase.inappmessaging.testutil.TestData.ACTION_MODEL_WITHOUT_BUTTON;
import static com.google.firebase.inappmessaging.testutil.TestData.ACTION_URL_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.BODY_TEXT_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.BUTTON_HEX_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.BUTTON_TEXT_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_METADATA_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.DATA;
import static com.google.firebase.inappmessaging.testutil.TestData.IMAGE_DATA;
import static com.google.firebase.inappmessaging.testutil.TestData.MESSAGE_BACKGROUND_HEX_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.SECONDARY_ACTION_URL_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.TITLE_TEXT_STRING;

import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.inappmessaging.display.R;
import com.google.firebase.inappmessaging.display.internal.injection.modules.InflaterConfigModule;
import com.google.firebase.inappmessaging.model.Action;
import com.google.firebase.inappmessaging.model.BannerMessage;
import com.google.firebase.inappmessaging.model.Button;
import com.google.firebase.inappmessaging.model.CardMessage;
import com.google.firebase.inappmessaging.model.ModalMessage;
import com.google.firebase.inappmessaging.model.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests how the binding wrappers apply the text colors of a campaign. */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "port")
public class BindingWrapperTextColorTest {
  // Non-empty strings that the message model accepts but Color.parseColor rejects.
  private static final List<String> INVALID_COLORS =
      Arrays.asList("#FFF", "rgba(0,0,0,1)", "FFFFFF", " #FFFFFF", "#GGGGGG");
  private static final String VALID_COLOR = "#FF0000";
  private static final String VALID_COLOR_WITH_ALPHA = "#80FF0000";

  private final InflaterConfigModule inflaterConfigModule = new InflaterConfigModule();
  private final DisplayMetrics displayMetrics = new DisplayMetrics();
  private final View.OnClickListener dismissListener = view -> {};
  private LayoutInflater inflater;

  @Before
  public void setUp() {
    displayMetrics.widthPixels = 1000;
    displayMetrics.heightPixels = 2000;
    inflater = LayoutInflater.from(ApplicationProvider.getApplicationContext());
  }

  @Test
  public void setTextColorFromHex_emptyOrInvalidColor_keepsTextColor() {
    TextView view = new TextView(ApplicationProvider.getApplicationContext());
    view.setTextColor(Color.BLUE);
    List<String> colors = new ArrayList<>(INVALID_COLORS);
    colors.add("");
    colors.add(null);

    for (String color : colors) {
      BindingWrapper.setTextColorFromHex(view, color);

      assertWithMessage("text color with color \"%s\"", color)
          .that(view.getCurrentTextColor())
          .isEqualTo(Color.BLUE);
    }
  }

  @Test
  public void setTextColorFromHex_validColor_setsTextColor() {
    TextView view = new TextView(ApplicationProvider.getApplicationContext());

    BindingWrapper.setTextColorFromHex(view, VALID_COLOR_WITH_ALPHA);

    assertThat(view.getCurrentTextColor()).isEqualTo(0x80FF0000);
  }

  @Test
  public void inflateBanner_invalidTextColors_keepsDefaultTextColors() {
    for (String color : INVALID_COLORS) {
      BindingWrapper wrapper = inflateBanner(color, color);

      assertDefaultTextColor(wrapper, R.layout.banner, R.id.banner_title, TITLE_TEXT_STRING, color);
      assertDefaultTextColor(wrapper, R.layout.banner, R.id.banner_body, BODY_TEXT_STRING, color);
    }
  }

  @Test
  public void inflateBanner_validTextColors_setsTextColors() {
    BindingWrapper wrapper = inflateBanner(VALID_COLOR, VALID_COLOR_WITH_ALPHA);

    assertTextColor(wrapper, R.id.banner_title, VALID_COLOR);
    assertTextColor(wrapper, R.id.banner_body, VALID_COLOR_WITH_ALPHA);
  }

  @Test
  public void inflateModal_invalidTextColors_keepsDefaultTextColors() {
    for (String color : INVALID_COLORS) {
      BindingWrapper wrapper = inflateModal(color, color, color);

      assertDefaultTextColor(wrapper, R.layout.modal, R.id.message_title, TITLE_TEXT_STRING, color);
      assertDefaultTextColor(wrapper, R.layout.modal, R.id.message_body, BODY_TEXT_STRING, color);
      assertDefaultTextColor(wrapper, R.layout.modal, R.id.button, BUTTON_TEXT_STRING, color);
    }
  }

  @Test
  public void inflateModal_validTextColors_setsTextColors() {
    BindingWrapper wrapper = inflateModal(VALID_COLOR, VALID_COLOR_WITH_ALPHA, VALID_COLOR);

    assertTextColor(wrapper, R.id.message_title, VALID_COLOR);
    assertTextColor(wrapper, R.id.message_body, VALID_COLOR_WITH_ALPHA);
    assertTextColor(wrapper, R.id.button, VALID_COLOR);
  }

  @Test
  public void inflateCard_invalidTextColors_keepsDefaultTextColors() {
    for (String color : INVALID_COLORS) {
      BindingWrapper wrapper = inflateCard(color, color, color, color);

      assertDefaultTextColor(wrapper, R.layout.card, R.id.message_title, TITLE_TEXT_STRING, color);
      assertDefaultTextColor(wrapper, R.layout.card, R.id.message_body, BODY_TEXT_STRING, color);
      assertDefaultTextColor(
          wrapper, R.layout.card, R.id.primary_button, BUTTON_TEXT_STRING, color);
      assertDefaultTextColor(
          wrapper, R.layout.card, R.id.secondary_button, BUTTON_TEXT_STRING, color);
    }
  }

  @Test
  public void inflateCard_validTextColors_setsTextColors() {
    BindingWrapper wrapper =
        inflateCard(VALID_COLOR, VALID_COLOR_WITH_ALPHA, VALID_COLOR, VALID_COLOR_WITH_ALPHA);

    assertTextColor(wrapper, R.id.message_title, VALID_COLOR);
    assertTextColor(wrapper, R.id.message_body, VALID_COLOR_WITH_ALPHA);
    assertTextColor(wrapper, R.id.primary_button, VALID_COLOR);
    assertTextColor(wrapper, R.id.secondary_button, VALID_COLOR_WITH_ALPHA);
  }

  @Test
  public void inflateCard_someInvalidTextColors_stillSetsValidTextColors() {
    BindingWrapper wrapper = inflateCard("#FFF", VALID_COLOR, "rgba(0,0,0,1)", VALID_COLOR);

    assertDefaultTextColor(wrapper, R.layout.card, R.id.message_title, TITLE_TEXT_STRING, "#FFF");
    assertTextColor(wrapper, R.id.message_body, VALID_COLOR);
    assertDefaultTextColor(
        wrapper, R.layout.card, R.id.primary_button, BUTTON_TEXT_STRING, "rgba(0,0,0,1)");
    assertTextColor(wrapper, R.id.secondary_button, VALID_COLOR);
  }

  private BindingWrapper inflateBanner(String titleColor, String bodyColor) {
    BannerMessage message =
        BannerMessage.builder()
            .setAction(ACTION_MODEL_WITHOUT_BUTTON)
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .setTitle(text(TITLE_TEXT_STRING, titleColor))
            .setBody(text(BODY_TEXT_STRING, bodyColor))
            .setImageData(IMAGE_DATA)
            .build(CAMPAIGN_METADATA_MODEL, DATA);
    return inflate(
        new BannerBindingWrapper(
            inflaterConfigModule.providesBannerPortraitLayoutConfig(displayMetrics),
            inflater,
            message));
  }

  private BindingWrapper inflateModal(String titleColor, String bodyColor, String buttonColor) {
    ModalMessage message =
        ModalMessage.builder()
            .setAction(action(ACTION_URL_STRING, buttonColor))
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .setTitle(text(TITLE_TEXT_STRING, titleColor))
            .setBody(text(BODY_TEXT_STRING, bodyColor))
            .setImageData(IMAGE_DATA)
            .build(CAMPAIGN_METADATA_MODEL, DATA);
    return inflate(
        new ModalBindingWrapper(
            inflaterConfigModule.providesModalPortraitConfig(displayMetrics), inflater, message));
  }

  private BindingWrapper inflateCard(
      String titleColor, String bodyColor, String primaryButtonColor, String secondaryButtonColor) {
    CardMessage message =
        CardMessage.builder()
            .setPrimaryAction(action(ACTION_URL_STRING, primaryButtonColor))
            .setSecondaryAction(action(SECONDARY_ACTION_URL_STRING, secondaryButtonColor))
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .setTitle(text(TITLE_TEXT_STRING, titleColor))
            .setBody(text(BODY_TEXT_STRING, bodyColor))
            .setPortraitImageData(IMAGE_DATA)
            .build(CAMPAIGN_METADATA_MODEL, DATA);
    return inflate(
        new CardBindingWrapper(
            inflaterConfigModule.providesCardPortraitConfig(displayMetrics), inflater, message));
  }

  private BindingWrapper inflate(BindingWrapper wrapper) {
    wrapper.inflate(new HashMap<>(), dismissListener);
    return wrapper;
  }

  private static Text text(String text, String hexColor) {
    return Text.builder().setText(text).setHexColor(hexColor).build();
  }

  private static Action action(String actionUrl, String buttonTextColor) {
    Button button =
        Button.builder()
            .setText(text(BUTTON_TEXT_STRING, buttonTextColor))
            .setButtonHexColor(BUTTON_HEX_STRING)
            .build();
    return Action.builder().setActionUrl(actionUrl).setButton(button).build();
  }

  /** Asserts that the view shows its text in the text color that the layout sets. */
  private void assertDefaultTextColor(
      BindingWrapper wrapper, int layoutId, int viewId, String expectedText, String color) {
    TextView view = wrapper.getRootView().findViewById(viewId);
    TextView viewWithLayoutDefaults = inflater.inflate(layoutId, null).findViewById(viewId);

    assertWithMessage("text with color \"%s\"", color)
        .that(view.getText().toString())
        .isEqualTo(expectedText);
    assertWithMessage("text color with color \"%s\"", color)
        .that(view.getCurrentTextColor())
        .isEqualTo(viewWithLayoutDefaults.getCurrentTextColor());
  }

  private static void assertTextColor(BindingWrapper wrapper, int viewId, String hexColor) {
    TextView view = wrapper.getRootView().findViewById(viewId);

    assertThat(view.getCurrentTextColor()).isEqualTo(Color.parseColor(hexColor));
  }
}
