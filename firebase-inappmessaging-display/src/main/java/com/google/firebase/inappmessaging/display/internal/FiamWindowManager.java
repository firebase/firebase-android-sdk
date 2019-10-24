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

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import com.google.firebase.inappmessaging.display.internal.bindingwrappers.BindingWrapper;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Class encapsulating the popup window into which we inflate the in app message. The window manager
 * keeps state of the binding that is currently in view
 *
 * @hide
 */
@Singleton
public class FiamWindowManager {

  static final int DEFAULT_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;

  private BindingWrapper bindingWrapper;

  @Inject
  FiamWindowManager() {}

  /** Inflate the container into a new popup window */
  public void show(@NonNull final BindingWrapper bindingWrapper, @NonNull Activity activity) {
    if (isFiamDisplayed()) {
      Logging.loge("Fiam already active. Cannot show new Fiam.");
      return;
    }

    InAppMessageLayoutConfig config = bindingWrapper.getConfig();
    final WindowManager.LayoutParams layoutParams = getLayoutParams(config, activity);

    final WindowManager windowManager = getWindowManager(activity);
    final View rootView = bindingWrapper.getRootView();
    windowManager.addView(rootView, layoutParams);

    // Set 'window' left and right padding from the inset, this prevents
    // anything from touching the navigation bar when in landscape.
    Rect insetDimensions = getInsetDimensions(activity);
    Logging.logdPair("Inset (top, bottom)", insetDimensions.top, insetDimensions.bottom);
    Logging.logdPair("Inset (left, right)", insetDimensions.left, insetDimensions.right);

    // TODO: Should use WindowInsetCompat to make sure we don't overlap with the status bar
    //       action bar or anything else. This will become more pressing as notches
    //       become more common on Android phones.

    if (bindingWrapper.canSwipeToDismiss()) {
      SwipeDismissTouchListener listener =
          getSwipeListener(config, bindingWrapper, windowManager, layoutParams);
      bindingWrapper.getDialogView().setOnTouchListener(listener);
    }

    this.bindingWrapper = bindingWrapper;
  }

  public boolean isFiamDisplayed() {
    if (bindingWrapper == null) {
      return false;
    }
    return bindingWrapper.getRootView().isShown();
  }

  /** Removes the in app message from the surrounding window */
  public void destroy(@NonNull Activity activity) {
    if (isFiamDisplayed()) {
      getWindowManager(activity).removeViewImmediate(bindingWrapper.getRootView());
      bindingWrapper = null;
    }
  }

  private WindowManager.LayoutParams getLayoutParams(
      @NonNull InAppMessageLayoutConfig layoutConfig, @NonNull Activity activity) {
    final WindowManager.LayoutParams layoutParams =
        new WindowManager.LayoutParams(
            layoutConfig.windowWidth(),
            layoutConfig.windowHeight(),
            DEFAULT_TYPE,
            layoutConfig.windowFlag(),
            PixelFormat.TRANSLUCENT);

    // If the window gravity is TOP, we move down to avoid hitting the status bar (if shown).
    Rect insetDimensions = getInsetDimensions(activity);
    if ((layoutConfig.viewWindowGravity() & Gravity.TOP) == Gravity.TOP) {
      layoutParams.y = insetDimensions.top;
    }

    layoutParams.dimAmount = 0.3f;
    layoutParams.gravity = layoutConfig.viewWindowGravity();
    layoutParams.windowAnimations = 0;

    return layoutParams;
  }

  private WindowManager getWindowManager(@NonNull Activity activity) {
    return (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
  }

  /**
   * Get the total size of the display in pixels, with no exclusions. For example on a Pixel this
   * would return 1920x1080 rather than the content frame which gives up 63 pixels to the status bar
   * and 126 pixels to the navigation bar.
   */
  private Point getDisplaySize(@NonNull Activity activity) {
    Point size = new Point();

    Display display = getWindowManager(activity).getDefaultDisplay();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      display.getRealSize(size);
    } else {
      display.getSize(size);
    }

    return size;
  }

  /**
   * Determine how much content should be inset on all sides in order to not overlap with system UI.
   *
   * <p>Ex: Pixel in portrait top = 63 bottom = 126 left = 0 right = 0
   *
   * <p>Ex: Pixel in landscape, nav bar on right top = 63 bottom = 0 left = 0 right = 126
   */
  private Rect getInsetDimensions(@NonNull Activity activity) {
    Rect padding = new Rect();

    Rect visibleFrame = getVisibleFrame(activity);
    Point size = getDisplaySize(activity);

    padding.top = visibleFrame.top;
    padding.left = visibleFrame.left;
    padding.right = size.x - visibleFrame.right;
    padding.bottom = size.y - visibleFrame.bottom;

    return padding;
  }

  private Rect getVisibleFrame(@NonNull Activity activity) {
    Rect visibleFrame = new Rect();

    Window window = activity.getWindow();
    window.getDecorView().getWindowVisibleDisplayFrame(visibleFrame);

    return visibleFrame;
  }

  /** Get a swipe listener, using knowledge of the LayoutConfig to dictate the behavior. */
  private SwipeDismissTouchListener getSwipeListener(
      InAppMessageLayoutConfig layoutConfig,
      final BindingWrapper bindingWrapper,
      final WindowManager windowManager,
      final WindowManager.LayoutParams layoutParams) {

    // The dismiss callbacks are the same in any case.
    SwipeDismissTouchListener.DismissCallbacks callbacks =
        new SwipeDismissTouchListener.DismissCallbacks() {

          @Override
          public boolean canDismiss(Object token) {
            return true;
          }

          @Override
          public void onDismiss(View view, Object token) {
            if (bindingWrapper.getDismissListener() != null) {
              bindingWrapper.getDismissListener().onClick(view);
            }
          }
        };

    if (layoutConfig.windowWidth() == ViewGroup.LayoutParams.MATCH_PARENT) {
      // When we are using the entire view width we can use the default behavior
      return new SwipeDismissTouchListener(bindingWrapper.getDialogView(), null, callbacks);
    } else {
      // When we are not using the entire view width we need to use the WindowManager to animate.
      return new SwipeDismissTouchListener(bindingWrapper.getDialogView(), null, callbacks) {
        @Override
        protected float getTranslationX() {
          return layoutParams.x;
        }

        @Override
        protected void setTranslationX(float translationX) {
          layoutParams.x = (int) translationX;
          windowManager.updateViewLayout(bindingWrapper.getRootView(), layoutParams);
        }
      };
    }
  }
}
