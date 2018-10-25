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

import android.os.CountDownTimer;
import javax.inject.Inject;

/**
 * Countdown timers cannot be renewed and need to be repeated created for each usage making it hard
 * to test without a factory. This timer encapsulates what could have been a factory
 *
 * <p>Callers are expected to cancel timers before starting new ones, failing which the strong
 * callback references could lead to memory leaks
 *
 * @hide
 */
public class RenewableTimer {
  private CountDownTimer mCountDownTimer;

  @Inject
  RenewableTimer() {}

  public void start(final Callback c, long duration, long interval) {
    mCountDownTimer =
        new CountDownTimer(duration, interval) {
          @Override
          public void onTick(long l) {}

          @Override
          public void onFinish() {
            c.onFinish();
          }
        }.start();
  }

  public void cancel() {
    if (mCountDownTimer != null) {
      mCountDownTimer.cancel();
      mCountDownTimer = null;
    }
  }

  public interface Callback {
    void onFinish();
  }
}
