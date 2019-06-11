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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import com.google.firebase.inappmessaging.display.internal.InAppMessageLayoutConfig;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.inappmessaging.model.MessageType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ImageBindingWrapperTest {
  private static final String IMAGE_URL = "https://www.imgur.com";
  private static final String CAMPAIGN_ID = "campaign_id";
  private static final String ACTION_URL = "https://www.google.com";
  private static final String CAMPAIGN_NAME = "campaign_name";
  private static final InAppMessage.Action ACTION =
      InAppMessage.Action.builder().setActionUrl(ACTION_URL).build();
  private static final InAppMessageLayoutConfig inappMessageLayoutConfig =
      InAppMessageLayoutConfig.builder()
          .setMaxDialogHeightPx((int) (0.8 * 1000))
          .setMaxDialogWidthPx((int) (0.7f * 1000))
          .setMaxImageHeightWeight(0.6f)
          .setMaxBodyHeightWeight(0.1f)
          .setMaxImageWidthWeight(0.9f) // entire dialog width
          .setMaxBodyWidthWeight(0.9f) // entire dialog width
          .setViewWindowGravity(Gravity.CENTER)
          .setWindowFlag(0)
          .setWindowWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
          .setWindowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
          .setBackgroundEnabled(false)
          .setAnimate(false)
          .setAutoDismiss(false)
          .build();
  private static final InAppMessage IMAGE_ONLY_MESSAGE =
      InAppMessage.builder()
          .setCampaignId(CAMPAIGN_ID)
          .setIsTestMessage(false)
          .setCampaignName(CAMPAIGN_NAME)
          .setAction(ACTION)
          .setMessageType(MessageType.IMAGE_ONLY)
          .setImageUrl(IMAGE_URL)
          .build();
  @Rule public ActivityTestRule<TestActivity> rule = new ActivityTestRule<>(TestActivity.class);
  @Mock private View.OnClickListener onDismissListener;
  @Mock private View.OnClickListener actionListener;

  private ImageBindingWrapper imageBindingWrapper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    TestActivity testActivity = rule.getActivity();
    LayoutInflater layoutInflater =
        (LayoutInflater) testActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    imageBindingWrapper =
        new ImageBindingWrapper(inappMessageLayoutConfig, layoutInflater, IMAGE_ONLY_MESSAGE);
  }

  @Test
  public void inflate_setsMessage() throws Exception {
    imageBindingWrapper.inflate(actionListener, onDismissListener);

    assertEquals(imageBindingWrapper.message, IMAGE_ONLY_MESSAGE);
  }

  @Test
  public void inflate_setsLayoutConfig() throws Exception {
    imageBindingWrapper.inflate(actionListener, onDismissListener);

    assertEquals(imageBindingWrapper.config, inappMessageLayoutConfig);
  }

  @Test
  public void inflate_setsDismissListener() throws Exception {
    imageBindingWrapper.inflate(actionListener, onDismissListener);

    assertTrue(imageBindingWrapper.getCollapseButton().hasOnClickListeners());
  }

  @Test
  public void inflate_setsActionListener() throws Exception {
    imageBindingWrapper.inflate(actionListener, onDismissListener);

    assertTrue(imageBindingWrapper.getImageView().hasOnClickListeners());
  }
}
