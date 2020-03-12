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

package com.google.firebase.inappmessaging.internal;

import static com.google.firebase.inappmessaging.internal.InAppMessageStreamManager.ON_FOREGROUND;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import io.reactivex.BackpressureStrategy;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.subjects.BehaviorSubject;

/**
 * The {@link ForegroundNotifier} notifies listeners via {@link #foregroundFlowable()} when an
 * application comes to the foreground.
 *
 * <p>Supported foreground scenarios
 *
 * <ul>
 *   <li>App resumed phone screen is unlocked
 *   <li>App starts when app icon is clicked
 *   <li>App resumes aftercompletion of phone call
 *   <li>App is chosen from recent apps menu
 * </ul>
 *
 * <p>This works as follows
 *
 * <ul>
 *   <li>When an app is foregrounded for the first time after app icon is clicked, it is moved to
 *       the foreground state and an event is published
 *   <li>When any activity in the app is paused and {@link #onActivityPaused(Activity)} callback is
 *       received, the app is considered to be paused until the next activity starts and the {@link
 *       #onActivityResumed(Activity)} callback is received. A runnable is simultaneously scheduled
 *       to be run after a {@link #DELAY_MILLIS} which will put the app into background state.
 *   <li>If some other activity subsequently starts and beats execution of the runnable by invoking
 *       the {@link #onActivityResumed(Activity)}, the app never went out of view for the user and
 *       is considered to have never gone to the background. The runnable is removed and the app
 *       remains in the foreground.
 *   <li>Similar to the first step, an event is published in the {@link
 *       #onActivityResumed(Activity)} callback if the app was deemed to be in the background</>
 * </ul>
 *
 * @hide
 */
public class ForegroundNotifier implements Application.ActivityLifecycleCallbacks {
  public static final long DELAY_MILLIS = 1000;
  private final Handler handler = new Handler();
  private boolean foreground = false, paused = true;
  private Runnable check;
  private final BehaviorSubject<String> foregroundSubject = BehaviorSubject.create();

  /** @returns a {@link ConnectableFlowable} representing a stream of foreground events */
  public ConnectableFlowable<String> foregroundFlowable() {
    return foregroundSubject.toFlowable(BackpressureStrategy.BUFFER).publish();
  }

  @Override
  public void onActivityResumed(Activity activity) {
    paused = false;
    boolean wasBackground = !foreground;
    foreground = true;

    if (check != null) {
      handler.removeCallbacks(check);
    }

    if (wasBackground) {
      Logging.logi("went foreground");
      foregroundSubject.onNext(ON_FOREGROUND);
    }
  }

  @Override
  public void onActivityPaused(Activity activity) {
    paused = true;

    if (check != null) {
      handler.removeCallbacks(check);
    }

    handler.postDelayed(
        check = () -> foreground = (!foreground || !paused) && foreground, DELAY_MILLIS);
  }

  @Override
  public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

  @Override
  public void onActivityStarted(Activity activity) {}

  @Override
  public void onActivityStopped(Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

  @Override
  public void onActivityDestroyed(Activity activity) {}
}
