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

package com.google.firebase.inappmessaging.display.internal.injection.modules;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import com.google.firebase.inappmessaging.display.internal.InAppMessageLayoutConfig;
import com.google.firebase.inappmessaging.display.internal.injection.keys.LayoutConfigKey;
import com.google.firebase.inappmessaging.model.MessageType;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;

/** @hide */
@Module
public class InflaterConfigModule {

  // visible for testing
  public static int DISABLED_BG_FLAG =
      WindowManager.LayoutParams.FLAG_DIM_BEHIND
          | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
          | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
          | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;

  public static int DISMISSIBLE_DIALOG_FLAG =
      WindowManager.LayoutParams.FLAG_DIM_BEHIND
          | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
          | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
          | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
          | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;

  private int ENABLED_BG_FLAG =
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
          | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
          | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

  public static String configFor(MessageType type, int orientation) {
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      switch (type) {
        case MODAL:
          return LayoutConfigKey.MODAL_PORTRAIT;
        case CARD:
          return LayoutConfigKey.CARD_PORTRAIT;
        case IMAGE_ONLY:
          return LayoutConfigKey.IMAGE_ONLY_PORTRAIT;
        case BANNER:
          return LayoutConfigKey.BANNER_PORTRAIT;
        default:
          return null;
      }
    } else {
      switch (type) {
        case MODAL:
          return LayoutConfigKey.MODAL_LANDSCAPE;
        case CARD:
          return LayoutConfigKey.CARD_LANDSCAPE;
        case IMAGE_ONLY:
          return LayoutConfigKey.IMAGE_ONLY_LANDSCAPE;
        case BANNER:
          return LayoutConfigKey.BANNER_LANDSCAPE;
        default:
          return null;
      }
    }
  }

  @Provides
  DisplayMetrics providesDisplayMetrics(Application application) {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    WindowManager windowManager =
        (WindowManager) application.getSystemService(Context.WINDOW_SERVICE);
    windowManager.getDefaultDisplay().getMetrics(displayMetrics);

    return displayMetrics;
  }

  // visible for testing
  @Provides
  @IntoMap
  @StringKey(LayoutConfigKey.IMAGE_ONLY_PORTRAIT)
  public InAppMessageLayoutConfig providesPortraitImageLayoutConfig(DisplayMetrics displayMetrics) {
    return InAppMessageLayoutConfig.builder()
        .setMaxDialogHeightPx((int) (0.9f * displayMetrics.heightPixels))
        .setMaxDialogWidthPx((int) (0.9f * displayMetrics.widthPixels))
        .setMaxImageWidthWeight(0.8f)
        .setMaxImageHeightWeight(0.8f)
        .setViewWindowGravity(Gravity.CENTER)
        .setWindowFlag(DISABLED_BG_FLAG)
        .setWindowWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
        .setWindowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
        .setBackgroundEnabled(false)
        .setAnimate(false)
        .setAutoDismiss(false)
        .build();
  }

  // visible for testing
  @Provides
  @IntoMap
  @StringKey(LayoutConfigKey.IMAGE_ONLY_LANDSCAPE)
  public InAppMessageLayoutConfig providesLandscapeImageLayoutConfig(
      DisplayMetrics displayMetrics) {
    return InAppMessageLayoutConfig.builder()
        .setMaxDialogHeightPx((int) (0.9f * displayMetrics.heightPixels))
        .setMaxDialogWidthPx((int) (0.9f * displayMetrics.widthPixels))
        .setMaxImageWidthWeight(0.8f)
        .setMaxImageHeightWeight(0.8f)
        .setViewWindowGravity(Gravity.CENTER)
        .setWindowFlag(DISABLED_BG_FLAG)
        .setWindowWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
        .setWindowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
        .setBackgroundEnabled(false)
        .setAnimate(false)
        .setAutoDismiss(false)
        .build();
  }

  // visible for testing
  @Provides
  @IntoMap
  @StringKey(LayoutConfigKey.MODAL_LANDSCAPE)
  public InAppMessageLayoutConfig providesModalLandscapeConfig(DisplayMetrics displayMetrics) {
    return InAppMessageLayoutConfig.builder()
        .setMaxDialogHeightPx((int) (0.8 * displayMetrics.heightPixels))
        .setMaxDialogWidthPx(displayMetrics.widthPixels)
        .setMaxImageHeightWeight(1f) // entire dialog height
        .setMaxImageWidthWeight(0.4f)
        .setMaxBodyHeightWeight(0.6f)
        .setMaxBodyWidthWeight(0.4f)
        .setViewWindowGravity(Gravity.CENTER)
        .setWindowFlag(DISABLED_BG_FLAG)
        .setWindowWidth(ViewGroup.LayoutParams.MATCH_PARENT)
        .setWindowHeight(ViewGroup.LayoutParams.MATCH_PARENT)
        .setBackgroundEnabled(false)
        .setAnimate(false)
        .setAutoDismiss(false)
        .build();
  }

  // visible for testing
  @Provides
  @IntoMap
  @StringKey(LayoutConfigKey.MODAL_PORTRAIT)
  public InAppMessageLayoutConfig providesModalPortraitConfig(DisplayMetrics displayMetrics) {
    return InAppMessageLayoutConfig.builder()
        .setMaxDialogHeightPx((int) (0.8 * displayMetrics.heightPixels))
        .setMaxDialogWidthPx((int) (0.7f * displayMetrics.widthPixels))
        .setMaxImageHeightWeight(0.6f)
        .setMaxBodyHeightWeight(0.1f)
        .setMaxImageWidthWeight(0.9f) // entire dialog width
        .setMaxBodyWidthWeight(0.9f) // entire dialog width
        .setViewWindowGravity(Gravity.CENTER)
        .setWindowFlag(DISABLED_BG_FLAG)
        .setWindowWidth(ViewGroup.LayoutParams.MATCH_PARENT)
        .setWindowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
        .setBackgroundEnabled(false)
        .setAnimate(false)
        .setAutoDismiss(false)
        .build();
  }

  // visible for testing
  @Provides
  @IntoMap
  @StringKey(LayoutConfigKey.CARD_LANDSCAPE)
  public InAppMessageLayoutConfig providesCardLandscapeConfig(DisplayMetrics displayMetrics) {
    return InAppMessageLayoutConfig.builder()
        .setMaxDialogHeightPx((int) (0.8 * displayMetrics.heightPixels))
        .setMaxDialogWidthPx(displayMetrics.widthPixels)
        .setMaxImageHeightWeight(1f) // entire dialog height
        .setMaxImageWidthWeight(0.5f)
        .setViewWindowGravity(Gravity.CENTER)
        .setWindowFlag(DISMISSIBLE_DIALOG_FLAG)
        .setWindowWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
        .setWindowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
        .setBackgroundEnabled(false)
        .setAnimate(false)
        .setAutoDismiss(false)
        .build();
  }

  // visible for testing
  @Provides
  @IntoMap
  @StringKey(LayoutConfigKey.CARD_PORTRAIT)
  public InAppMessageLayoutConfig providesCardPortraitConfig(DisplayMetrics displayMetrics) {
    return InAppMessageLayoutConfig.builder()
        .setMaxDialogHeightPx((int) (0.8 * displayMetrics.heightPixels))
        .setMaxDialogWidthPx((int) (0.7f * displayMetrics.widthPixels))
        .setMaxImageHeightWeight(0.6f)
        .setMaxImageWidthWeight(1f) // entire dialog width
        .setMaxBodyHeightWeight(0.1f)
        .setMaxBodyWidthWeight(0.9f) // entire dialog width
        .setViewWindowGravity(Gravity.CENTER)
        .setWindowFlag(DISMISSIBLE_DIALOG_FLAG)
        .setWindowWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
        .setWindowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
        .setBackgroundEnabled(false)
        .setAnimate(false)
        .setAutoDismiss(false)
        .build();
  }

  // visible for testing
  @Provides
  @IntoMap
  @StringKey(LayoutConfigKey.BANNER_PORTRAIT)
  public InAppMessageLayoutConfig providesBannerPortraitLayoutConfig(
      DisplayMetrics displayMetrics) {
    return InAppMessageLayoutConfig.builder()
        .setMaxImageHeightWeight(0.3f)
        .setMaxImageWidthWeight(0.3f)
        .setMaxDialogHeightPx((int) (0.5f * displayMetrics.heightPixels))
        .setMaxDialogWidthPx((int) (0.9f * displayMetrics.widthPixels))
        .setViewWindowGravity(Gravity.TOP)
        .setWindowFlag(ENABLED_BG_FLAG)
        .setWindowWidth(ViewGroup.LayoutParams.MATCH_PARENT)
        .setWindowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
        .setBackgroundEnabled(true)
        .setAnimate(true)
        .setAutoDismiss(true)
        .build();
  }

  // visible for testing
  @Provides
  @IntoMap
  @StringKey(LayoutConfigKey.BANNER_LANDSCAPE)
  public InAppMessageLayoutConfig providesBannerLandscapeLayoutConfig(
      DisplayMetrics displayMetrics) {
    return InAppMessageLayoutConfig.builder()
        .setMaxImageHeightWeight(0.3f)
        .setMaxImageWidthWeight(0.3f)
        .setMaxDialogHeightPx((int) (0.5f * displayMetrics.heightPixels))
        .setMaxDialogWidthPx((int) (0.9f * displayMetrics.widthPixels))
        .setViewWindowGravity(Gravity.TOP)
        .setWindowFlag(ENABLED_BG_FLAG)
        .setWindowWidth(ViewGroup.LayoutParams.MATCH_PARENT)
        .setWindowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
        .setBackgroundEnabled(true)
        .setAnimate(true)
        .setAutoDismiss(true)
        .build();
  }
}
