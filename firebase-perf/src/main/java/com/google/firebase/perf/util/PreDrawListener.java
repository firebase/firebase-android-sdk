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

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OnPreDraw listener that unregisters itself and post a callback to the main thread during
 * OnPreDraw. This is an approximation of the initial-display time defined by Android Vitals.
 */
public class PreDrawListener implements ViewTreeObserver.OnPreDrawListener {
  // TODO(b/258263016): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

  private final AtomicReference<View> viewReference;
  private final Runnable callbackBoQ;
  private final Runnable callbackFoQ;

  /** Registers a post-draw callback for the next draw of a view. */
  public static void registerForNextDraw(
      View view, Runnable drawDoneCallbackBoQ, Runnable drawDoneCallbackFoQ) {
    final PreDrawListener listener =
        new PreDrawListener(view, drawDoneCallbackBoQ, drawDoneCallbackFoQ);
    view.getViewTreeObserver().addOnPreDrawListener(listener);
  }

  private PreDrawListener(View view, Runnable callbackBoQ, Runnable callbackFoQ) {
    this.viewReference = new AtomicReference<>(view);
    this.callbackBoQ = callbackBoQ;
    this.callbackFoQ = callbackFoQ;
  }

  @Override
  public boolean onPreDraw() {
    // Set viewReference to null so any onPreDraw past the first is a no-op
    View view = viewReference.getAndSet(null);
    if (view == null) {
      return true;
    }
    view.getViewTreeObserver().removeOnPreDrawListener(this);
    mainThreadHandler.post(callbackBoQ);
    mainThreadHandler.postAtFrontOfQueue(callbackFoQ);
    return true;
  }
}
