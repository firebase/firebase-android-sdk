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

import android.view.GestureDetector;
import android.view.MotionEvent;

/** @hide */
public class OnSwipeUpListener extends GestureDetector.SimpleOnGestureListener {
  private static final int SWIPE_MIN_DISTANCE = 120;
  private static final int SWIPE_MAX_OFF_PATH = 250;
  private static final int SWIPE_THRESHOLD_VELOCITY = 200;

  @Override
  public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    if (Math.abs(e1.getX() - e2.getX()) > SWIPE_MAX_OFF_PATH) {
      return false;
    }

    if (e1.getY() - e2.getY() > SWIPE_MIN_DISTANCE
        && Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY) {
      return onSwipeUp();
    }

    return false;
  }

  public boolean onSwipeUp() {
    return false;
  }
}
