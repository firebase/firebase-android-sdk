// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.perf.util;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.gms.common.util.VisibleForTesting;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OnDrawListener that unregisters itself and invokes callback when the next draw is done. This API
 * 16+ implementation is an approximation of the initial display time. {@link
 * android.view.Choreographer#postFrameCallback} is an Android API that provides a simpler and more
 * accurate initial display time, but it was bugged before API 30, hence we use this backported
 * implementation.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
public class FirstDrawDoneListener implements ViewTreeObserver.OnDrawListener {
  private final AtomicReference<View> viewReference;
  private final Runnable callback;
  private final Handler mainThreadHandler;

  public static void registerForNextDraw(View view, Runnable drawDoneCallback) {
    // Handle bug prior to API 26 where OnDrawListener from the floating ViewTreeObserver is not
    // merged into the real ViewTreeObserver.
    // https://android.googlesource.com/platform/frameworks/base/+/9f8ec54244a5e0343b9748db3329733f259604f3
    if (Build.VERSION.SDK_INT < 26
        && (!view.getViewTreeObserver().isAlive() || view.getWindowToken() == null)) {
      view.addOnAttachStateChangeListener(
          new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
              view.getViewTreeObserver()
                  .addOnDrawListener(new FirstDrawDoneListener(view, drawDoneCallback));
              view.removeOnAttachStateChangeListener(this);
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
              view.removeOnAttachStateChangeListener(this);
            }
          });
    } else {
      view.getViewTreeObserver()
          .addOnDrawListener(new FirstDrawDoneListener(view, drawDoneCallback));
    }
  }

  private FirstDrawDoneListener(@Nullable View view, Runnable callback) {
    this(view, callback, new Handler(Looper.getMainLooper()));
  }

  @VisibleForTesting
  FirstDrawDoneListener(@Nullable View view, Runnable callback, Handler mainThreadHandler) {
    this.viewReference = new AtomicReference<>(view);
    this.callback = callback;
    this.mainThreadHandler = mainThreadHandler;
  }

  @Override
  public void onDraw() {
    View view = viewReference.getAndSet(null);
    if (view == null) {
      return;
    }
    // OnDrawListeners cannot be removed within onDraw, so we remove it with a
    // GlobalLayoutListener
    view.getViewTreeObserver().addOnGlobalLayoutListener(new LayoutChangeListener(view, this));
    mainThreadHandler.postAtFrontOfQueue(callback);
  }

  @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
  private static final class LayoutChangeListener
      implements ViewTreeObserver.OnGlobalLayoutListener {
    private final View view;
    private final FirstDrawDoneListener listener;

    private LayoutChangeListener(View view, FirstDrawDoneListener firstDrawDoneListener) {
      this.view = view;
      this.listener = firstDrawDoneListener;
    }

    @Override
    public void onGlobalLayout() {
      view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
      view.getViewTreeObserver().removeOnDrawListener(listener);
    }
  }
}
