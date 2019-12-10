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

package com.google.firebase.inappmessaging.display.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.inappmessaging.testutil.TestData.IMAGE_MESSAGE_MODEL;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;
import com.google.firebase.inappmessaging.display.internal.bindingwrappers.BindingWrapper;
import com.google.firebase.inappmessaging.display.internal.bindingwrappers.ImageBindingWrapper;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, qualifiers = "port")
public class FiamWindowManagerTest {
  private static final Context appContext = RuntimeEnvironment.application.getApplicationContext();
  private static final int WINDOW_GRAVITY = Gravity.CENTER;
  private static final InAppMessageLayoutConfig inappMessageLayoutConfig =
      InAppMessageLayoutConfig.builder()
          .setMaxDialogHeightPx((int) (0.9f * 1000))
          .setMaxDialogWidthPx((int) (0.9f * 1000))
          .setMaxImageWidthWeight(0.8f)
          .setMaxImageHeightWeight(0.8f)
          .setViewWindowGravity(WINDOW_GRAVITY)
          .setWindowFlag(1)
          .setWindowWidth(ViewGroup.LayoutParams.FILL_PARENT)
          .setWindowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
          .setBackgroundEnabled(false)
          .setAnimate(false)
          .setAutoDismiss(false)
          .build();

  private FiamWindowManager fiamWindowManager;
  private TestActivity activity;
  private BindingWrapper bindingWrapper;
  private WindowManager windowManager;

  @Captor ArgumentCaptor<WindowManager.LayoutParams> layoutArgCaptor;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    activity = Robolectric.buildActivity(TestActivity.class).create().get();
    fiamWindowManager = new FiamWindowManager();

    LayoutInflater inflater = LayoutInflater.from(appContext);
    bindingWrapper =
        spy(new ImageBindingWrapper(inappMessageLayoutConfig, inflater, IMAGE_MESSAGE_MODEL));
    bindingWrapper.inflate(new HashMap<>(), null);

    windowManager = spy((WindowManager) activity.getSystemService(Context.WINDOW_SERVICE));
    activity.setWindowManager(windowManager);
  }

  @Test
  public void show_addsViewOnlyOnce() {
    final WindowManager.LayoutParams expectedLayoutParams =
        new WindowManager.LayoutParams(
            inappMessageLayoutConfig.windowWidth(),
            inappMessageLayoutConfig.windowHeight(),
            FiamWindowManager.DEFAULT_TYPE,
            inappMessageLayoutConfig.windowFlag(),
            PixelFormat.TRANSLUCENT);
    expectedLayoutParams.dimAmount = 0.3f;
    expectedLayoutParams.gravity = WINDOW_GRAVITY;
    expectedLayoutParams.windowAnimations = 0;

    fiamWindowManager.show(bindingWrapper, activity);
    fiamWindowManager.show(bindingWrapper, activity);
    fiamWindowManager.show(bindingWrapper, activity);

    verify(windowManager, times(1))
        .addView(eq(bindingWrapper.getRootView()), layoutArgCaptor.capture());

    // We test their toString representations since these are not implemented as value objects
    assertThat(layoutArgCaptor.getValue().toString()).isEqualTo(expectedLayoutParams.toString());
  }

  @Test
  public void destroy_whenActiveFiamIsPresent_removesViewOnlyOnce() {
    final WindowManager.LayoutParams expectedLayoutParams =
        new WindowManager.LayoutParams(
            inappMessageLayoutConfig.windowWidth(),
            inappMessageLayoutConfig.windowHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            inappMessageLayoutConfig.windowFlag(),
            PixelFormat.TRANSLUCENT);
    expectedLayoutParams.dimAmount = 0.3f;
    expectedLayoutParams.gravity = WINDOW_GRAVITY;
    expectedLayoutParams.windowAnimations = 0;
    fiamWindowManager.show(bindingWrapper, activity);

    fiamWindowManager.destroy(activity);
    fiamWindowManager.destroy(activity);
    fiamWindowManager.destroy(activity);

    verify(windowManager, times(1)).removeViewImmediate(bindingWrapper.getRootView());
    // We test their toString representations since these are not implemented as value objects
  }
}
