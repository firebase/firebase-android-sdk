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

import static com.google.firebase.inappmessaging.testutil.TestData.ACTION_MODEL_WITH_BUTTON;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_METADATA_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.DATA;
import static com.google.firebase.inappmessaging.testutil.TestData.IMAGE_DATA;
import static com.google.firebase.inappmessaging.testutil.TestData.MESSAGE_BACKGROUND_HEX_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.MODAL_MESSAGE_MODEL;
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
import com.google.firebase.inappmessaging.model.ModalMessage;
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
public class ModalBindingWrapperTest {

  private DisplayMetrics testDisplayMetrics = new DisplayMetrics();
  private InflaterConfigModule inflaterConfigModule = new InflaterConfigModule();
  private InAppMessageLayoutConfig modalPortraitLayoutConfig;

  @Rule public ActivityTestRule<TestActivity> rule = new ActivityTestRule<>(TestActivity.class);
  @Mock private View.OnClickListener onDismissListener;
  @Mock private View.OnClickListener actionListener;

  private Map<Action, View.OnClickListener> actionListeners;
  private ModalBindingWrapper modalBindingWrapper;

  @Before
  public void setup() {
    testDisplayMetrics.widthPixels = 1000;
    testDisplayMetrics.widthPixels = 2000;
    modalPortraitLayoutConfig =
        inflaterConfigModule.providesModalPortraitConfig(testDisplayMetrics);

    MockitoAnnotations.initMocks(this);
    TestActivity testActivity = rule.getActivity();
    LayoutInflater layoutInflater =
        (LayoutInflater) testActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    modalBindingWrapper =
        new ModalBindingWrapper(modalPortraitLayoutConfig, layoutInflater, MODAL_MESSAGE_MODEL);
    this.actionListeners = new HashMap<>();
    actionListeners.put(ACTION_MODEL_WITH_BUTTON, actionListener);
  }

  @Test
  public void inflate_setsMessage() throws Exception {
    modalBindingWrapper.inflate(actionListeners, onDismissListener);

    assertEquals(modalBindingWrapper.message, MODAL_MESSAGE_MODEL);
  }

  @Test
  public void inflate_setsLayoutConfig() throws Exception {
    modalBindingWrapper.inflate(actionListeners, onDismissListener);

    assertEquals(modalBindingWrapper.config, modalPortraitLayoutConfig);
  }

  @Test
  public void inflate_setsDismissListener() throws Exception {
    modalBindingWrapper.inflate(actionListeners, onDismissListener);

    assertTrue(modalBindingWrapper.getCollapseButton().hasOnClickListeners());
  }

  @Test
  public void inflate_setsActionListener() throws Exception {
    modalBindingWrapper.inflate(actionListeners, onDismissListener);

    assertTrue(modalBindingWrapper.getActionButton().hasOnClickListeners());
  }

  @Test
  public void inflate_setsButtonTextColor() throws Exception {
    modalBindingWrapper.inflate(actionListeners, onDismissListener);

    int textColor = modalBindingWrapper.getActionButton().getTextColors().getDefaultColor();
    int expectedTextColor =
        Color.parseColor(MODAL_MESSAGE_MODEL.getAction().getButton().getText().getHexColor());

    assertEquals(textColor, expectedTextColor);
    assertEquals(modalBindingWrapper.getActionButton().getVisibility(), View.VISIBLE);
  }

  @Test
  public void inflate_setsAllViewsVisibleWithCompleteMessage() throws Exception {
    TestActivity testActivity = rule.getActivity();
    LayoutInflater layoutInflater =
        (LayoutInflater) testActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    modalBindingWrapper =
        new ModalBindingWrapper(modalPortraitLayoutConfig, layoutInflater, MODAL_MESSAGE_MODEL);
    modalBindingWrapper.inflate(actionListeners, onDismissListener);

    assertEquals(modalBindingWrapper.getImageView().getVisibility(), View.VISIBLE);
    assertEquals(modalBindingWrapper.getActionButton().getVisibility(), View.VISIBLE);
    assertEquals(modalBindingWrapper.getScrollView().getVisibility(), View.VISIBLE);
    assertEquals(modalBindingWrapper.getTitleView().getVisibility(), View.VISIBLE);
  }

  @Test
  public void inflate_setsButtonInvisibleWithNoButton() throws Exception {
    TestActivity testActivity = rule.getActivity();
    LayoutInflater layoutInflater =
        (LayoutInflater) testActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    // This message has no button, so it should be gone in the layout
    ModalMessage message =
        ModalMessage.builder()
            .setImageData(IMAGE_DATA)
            .setTitle(TITLE_MODEL)
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .build(CAMPAIGN_METADATA_MODEL, DATA);

    modalBindingWrapper =
        new ModalBindingWrapper(modalPortraitLayoutConfig, layoutInflater, message);

    this.actionListeners = new HashMap<>();
    modalBindingWrapper.inflate(actionListeners, onDismissListener);

    assertEquals(modalBindingWrapper.getActionButton().getVisibility(), View.GONE);
  }

  @Test
  public void inflate_setsImageInvisibleWithNoImage() throws Exception {
    TestActivity testActivity = rule.getActivity();
    LayoutInflater layoutInflater =
        (LayoutInflater) testActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    // This message has no image, so it should be gone in the layout
    ModalMessage message =
        ModalMessage.builder()
            .setAction(ACTION_MODEL_WITH_BUTTON)
            .setTitle(TITLE_MODEL)
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .build(CAMPAIGN_METADATA_MODEL, DATA);

    modalBindingWrapper =
        new ModalBindingWrapper(modalPortraitLayoutConfig, layoutInflater, message);

    modalBindingWrapper.inflate(actionListeners, onDismissListener);
    assertEquals(modalBindingWrapper.getImageView().getVisibility(), View.GONE);
  }

  @Test
  public void inflate_setsBodyInvisibleWithNoBody() throws Exception {
    TestActivity testActivity = rule.getActivity();
    LayoutInflater layoutInflater =
        (LayoutInflater) testActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    // This message has no image, so it should be gone in the layout
    ModalMessage message =
        ModalMessage.builder()
            .setAction(ACTION_MODEL_WITH_BUTTON)
            .setImageData(IMAGE_DATA)
            .setTitle(TITLE_MODEL)
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .build(CAMPAIGN_METADATA_MODEL, DATA);

    modalBindingWrapper =
        new ModalBindingWrapper(modalPortraitLayoutConfig, layoutInflater, message);

    modalBindingWrapper.inflate(actionListeners, onDismissListener);
    assertEquals(modalBindingWrapper.getScrollView().getVisibility(), View.GONE);
  }
}
