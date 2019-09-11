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

import static com.google.firebase.inappmessaging.testutil.TestData.IMAGE_MESSAGE_MODEL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import com.google.firebase.inappmessaging.display.internal.InAppMessageLayoutConfig;
import com.google.firebase.inappmessaging.display.internal.injection.modules.InflaterConfigModule;
import com.google.firebase.inappmessaging.model.Action;
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
public class ImageBindingWrapperTest {

  private DisplayMetrics testDisplayMetrics = new DisplayMetrics();
  private InflaterConfigModule inflaterConfigModule = new InflaterConfigModule();
  private InAppMessageLayoutConfig imagePortraitLayoutConfig;

  @Rule public ActivityTestRule<TestActivity> rule = new ActivityTestRule<>(TestActivity.class);
  @Mock private View.OnClickListener onDismissListener;
  @Mock private View.OnClickListener actionListener;
  private Map<Action, View.OnClickListener> actionListeners;

  private ImageBindingWrapper imageBindingWrapper;

  @Before
  public void setup() {

    testDisplayMetrics.widthPixels = 1000;
    testDisplayMetrics.widthPixels = 2000;
    imagePortraitLayoutConfig =
        inflaterConfigModule.providesBannerPortraitLayoutConfig(testDisplayMetrics);

    MockitoAnnotations.initMocks(this);
    TestActivity testActivity = rule.getActivity();
    LayoutInflater layoutInflater =
        (LayoutInflater) testActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    imageBindingWrapper =
        new ImageBindingWrapper(imagePortraitLayoutConfig, layoutInflater, IMAGE_MESSAGE_MODEL);
    this.actionListeners = new HashMap<>();
    actionListeners.put(IMAGE_MESSAGE_MODEL.getAction(), actionListener);
  }

  @Test
  public void inflate_setsMessage() throws Exception {
    imageBindingWrapper.inflate(actionListeners, onDismissListener);

    assertEquals(imageBindingWrapper.message, IMAGE_MESSAGE_MODEL);
  }

  @Test
  public void inflate_setsLayoutConfig() throws Exception {
    imageBindingWrapper.inflate(actionListeners, onDismissListener);

    assertEquals(imageBindingWrapper.config, imagePortraitLayoutConfig);
  }

  @Test
  public void inflate_setsDismissListener() throws Exception {
    imageBindingWrapper.inflate(actionListeners, onDismissListener);

    assertTrue(imageBindingWrapper.getCollapseButton().hasOnClickListeners());
  }

  @Test
  public void inflate_setsActionListener() throws Exception {
    imageBindingWrapper.inflate(actionListeners, onDismissListener);

    assertTrue(imageBindingWrapper.getImageView().hasOnClickListeners());
  }
}
