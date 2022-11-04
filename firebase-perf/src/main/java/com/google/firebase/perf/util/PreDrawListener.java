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

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import java.util.concurrent.atomic.AtomicReference;

public class PreDrawListener implements ViewTreeObserver.OnPreDrawListener {
  private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
  private final AtomicReference<View> viewReference;
  private final Runnable callback;

  /** Registers a post-draw callback for the next draw of a view. */
  public static void registerForNextDraw(View view, Runnable drawDoneCallback) {
    final PreDrawListener listener = new PreDrawListener(view, drawDoneCallback);
    view.getViewTreeObserver().addOnPreDrawListener(listener);
  }

  private PreDrawListener(View view, Runnable callback) {
    this.viewReference = new AtomicReference<>(view);
    this.callback = callback;
  }

  @Override
  public boolean onPreDraw() {
    // Set viewReference to null so any onDraw past the first is a no-op
    View view = viewReference.getAndSet(null);
    if (view == null) {
      return true;
    }
    // OnDrawListeners cannot be removed within onDraw, so we remove it with a
    // GlobalLayoutListener
    view.getViewTreeObserver().removeOnPreDrawListener(this);
    mainThreadHandler.post(callback);
    return true;
  }
}
