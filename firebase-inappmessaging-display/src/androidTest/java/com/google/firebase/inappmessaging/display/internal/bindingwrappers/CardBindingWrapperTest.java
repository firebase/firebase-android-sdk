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

package com.google.firebase.inappmessaging.display.internal.bindingwrappers;

import static com.google.firebase.inappmessaging.testutil.TestData.ACTION_MODEL_WITHOUT_URL;
import static com.google.firebase.inappmessaging.testutil.TestData.ACTION_MODEL_WITH_BUTTON;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_METADATA_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.CARD_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.DATA;
import static com.google.firebase.inappmessaging.testutil.TestData.IMAGE_DATA;
import static com.google.firebase.inappmessaging.testutil.TestData.MESSAGE_BACKGROUND_HEX_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.TITLE_MODEL;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import android.content.Context;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import com.google.firebase.inappmessaging.display.internal.InAppMessageLayoutConfig;
import com.google.firebase.inappmessaging.display.internal.injection.modules.InflaterConfigModule;
import com.google.firebase.inappmessaging.model.Action;
import com.google.firebase.inappmessaging.model.CardMessage;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class CardBindingWrapperTest {

  private DisplayMetrics testDisplayMetrics = new DisplayMetrics();
  private InflaterConfigModule inflaterConfigModule = new InflaterConfigModule();
  private InAppMessageLayoutConfig cardPortraitLayoutConfig;

  @Rule public ActivityTestRule<TestActivity> rule = new ActivityTestRule<>(TestActivity.class);
  @Mock private View.OnClickListener onDismissListener;
  @Mock private View.OnClickListener primaryActionListener;
  @Mock private View.OnClickListener secondaryActionListener;

  private Map<Action, View.OnClickListener> actionListeners;
  private CardBindingWrapper cardBindingWrapper;

  @Before
  public void setup() {
    testDisplayMetrics.widthPixels = 1000;
    testDisplayMetrics.widthPixels = 2000;
    cardPortraitLayoutConfig = inflaterConfigModule.providesCardPortraitConfig(testDisplayMetrics);

    MockitoAnnotations.initMocks(this);
    TestActivity testActivity = rule.getActivity();
    LayoutInflater layoutInflater =
        (LayoutInflater) testActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    cardBindingWrapper =
        new CardBindingWrapper(cardPortraitLayoutConfig, layoutInflater, CARD_MESSAGE_MODEL);
    this.actionListeners = new HashMap<>();
    actionListeners.put(CARD_MESSAGE_MODEL.getPrimaryAction(), primaryActionListener);
    actionListeners.put(CARD_MESSAGE_MODEL.getSecondaryAction(), secondaryActionListener);
  }

  @Test
  public void inflate_setsMessage() throws Exception {
    cardBindingWrapper.inflate(actionListeners, onDismissListener);

    assertEquals(cardBindingWrapper.message, CARD_MESSAGE_MODEL);
  }

  @Test
  public void inflate_setsLayoutConfig() throws Exception {
    cardBindingWrapper.inflate(actionListeners, onDismissListener);

    assertEquals(cardBindingWrapper.config, cardPortraitLayoutConfig);
  }

  @Test
  public void inflate_setsDismissListener() throws Exception {
    cardBindingWrapper.inflate(actionListeners, onDismissListener);

    assertEquals(cardBindingWrapper.getDismissListener(), onDismissListener);
  }

  @Test
  public void inflate_setsPrimaryActionListener() throws Exception {
    cardBindingWrapper.inflate(actionListeners, onDismissListener);

    assertTrue(cardBindingWrapper.getPrimaryButton().hasOnClickListeners());
  }

  @Test
  public void inflate_setsSecondaryActionListener() throws Exception {
    cardBindingWrapper.inflate(actionListeners, onDismissListener);

    assertTrue(cardBindingWrapper.getSecondaryButton().hasOnClickListeners());
  }

  @Test
  public void inflate_setsPrimaryButtonTextColor() throws Exception {
    cardBindingWrapper.inflate(actionListeners, onDismissListener);

    int textColor = cardBindingWrapper.getPrimaryButton().getTextColors().getDefaultColor();
    int expectedTextColor =
        Color.parseColor(CARD_MESSAGE_MODEL.getPrimaryAction().getButton().getText().getHexColor());

    assertEquals(textColor, expectedTextColor);
    assertEquals(cardBindingWrapper.getPrimaryButton().getVisibility(), View.VISIBLE);
  }

  @Test
  public void inflate_setsSecondaryButtonTextColor() throws Exception {
    cardBindingWrapper.inflate(actionListeners, onDismissListener);

    int textColor = cardBindingWrapper.getSecondaryButton().getTextColors().getDefaultColor();
    int expectedTextColor =
        Color.parseColor(
            CARD_MESSAGE_MODEL.getSecondaryAction().getButton().getText().getHexColor());

    assertEquals(textColor, expectedTextColor);
    assertEquals(cardBindingWrapper.getSecondaryButton().getVisibility(), View.VISIBLE);
  }

  @Test
  public void inflate_setsAllViewsVisibleWithCompleteMessage() throws Exception {
    TestActivity testActivity = rule.getActivity();
    LayoutInflater layoutInflater =
        (LayoutInflater) testActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    cardBindingWrapper =
        new CardBindingWrapper(cardPortraitLayoutConfig, layoutInflater, CARD_MESSAGE_MODEL);

    this.actionListeners = new HashMap<>();
    cardBindingWrapper.inflate(actionListeners, onDismissListener);

    assertEquals(cardBindingWrapper.getTitleView().getVisibility(), View.VISIBLE);
    assertEquals(cardBindingWrapper.getScrollView().getVisibility(), View.VISIBLE);
    assertEquals(cardBindingWrapper.getImageView().getVisibility(), View.VISIBLE);
    assertEquals(cardBindingWrapper.getPrimaryButton().getVisibility(), View.VISIBLE);
    assertEquals(cardBindingWrapper.getSecondaryButton().getVisibility(), View.VISIBLE);
  }

  @Test
  public void inflate_setsSecondaryButtonInvisibleWithNoButton() throws Exception {
    TestActivity testActivity = rule.getActivity();
    LayoutInflater layoutInflater =
        (LayoutInflater) testActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    // This message has no secondary button, so it should be gone in the layout
    CardMessage message =
        CardMessage.builder()
            .setPrimaryAction(ACTION_MODEL_WITH_BUTTON)
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .setTitle(TITLE_MODEL)
            .setPortraitImageData(IMAGE_DATA)
            .build(CAMPAIGN_METADATA_MODEL, DATA);

    cardBindingWrapper = new CardBindingWrapper(cardPortraitLayoutConfig, layoutInflater, message);

    this.actionListeners = new HashMap<>();
    actionListeners.put(message.getPrimaryAction(), primaryActionListener);
    cardBindingWrapper.inflate(actionListeners, onDismissListener);

    assertEquals(cardBindingWrapper.getSecondaryButton().getVisibility(), View.GONE);
  }

  @Test
  public void inflate_setsPrimaryButtonVisibleWithoutUrl() throws Exception {
    TestActivity testActivity = rule.getActivity();
    LayoutInflater layoutInflater =
        (LayoutInflater) testActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    // This message has no secondary button, so it should be gone in the layout
    CardMessage message =
        CardMessage.builder()
            .setPrimaryAction(ACTION_MODEL_WITHOUT_URL)
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .setTitle(TITLE_MODEL)
            .setPortraitImageData(IMAGE_DATA)
            .build(CAMPAIGN_METADATA_MODEL, DATA);

    cardBindingWrapper = new CardBindingWrapper(cardPortraitLayoutConfig, layoutInflater, message);

    this.actionListeners = new HashMap<>();
    actionListeners.put(message.getPrimaryAction(), onDismissListener);
    cardBindingWrapper.inflate(actionListeners, onDismissListener);

    assertEquals(cardBindingWrapper.getPrimaryButton().getVisibility(), View.VISIBLE);
    assertEquals(cardBindingWrapper.getSecondaryButton().getVisibility(), View.GONE);
  }

  @Test
  public void inflate_setsBodyInvisibleWithNoBody() throws Exception {
    TestActivity testActivity = rule.getActivity();
    LayoutInflater layoutInflater =
        (LayoutInflater) testActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    // This message has body scroll, so it should be gone in the layout
    CardMessage message =
        CardMessage.builder()
            .setPrimaryAction(ACTION_MODEL_WITHOUT_URL)
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .setTitle(TITLE_MODEL)
            .setPortraitImageData(IMAGE_DATA)
            .build(CAMPAIGN_METADATA_MODEL, DATA);

    cardBindingWrapper = new CardBindingWrapper(cardPortraitLayoutConfig, layoutInflater, message);

    this.actionListeners = new HashMap<>();
    actionListeners.put(message.getPrimaryAction(), onDismissListener);
    cardBindingWrapper.inflate(actionListeners, onDismissListener);

    assertEquals(cardBindingWrapper.getScrollView().getVisibility(), View.GONE);
  }
}
