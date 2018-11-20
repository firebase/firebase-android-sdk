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

package com.google.firebase.inappmessaging.display;

import static com.google.common.truth.Truth.assertThat;

import android.view.MotionEvent;
import com.google.firebase.inappmessaging.display.internal.OnSwipeUpListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21)
public class OnSwipeListenerTest {
  private OnSwipeUpListener onSwipeListener;
  private boolean sweptUp;
  private static final MotionEvent TOP_LEFT = MotionEvent.obtain(0, 0, 0, 0, 0, 0);
  private static final MotionEvent BOTTOM_LEFT = MotionEvent.obtain(0, 0, 0, 0, 300, 0);

  @Before
  public void setup() {
    sweptUp = false;

    onSwipeListener =
        new OnSwipeUpListener() {
          @Override
          public boolean onSwipeUp() {
            sweptUp = true;
            return true;
          }
        };
  }

  @Test
  public void onFling_detectsSwipeUp() {
    onSwipeListener.onFling(BOTTOM_LEFT, TOP_LEFT, 20, 250);

    assertThat(sweptUp).isTrue();
  }

  @Test
  public void onFling_whenTooSlow_doesNotdetectsSwipeUp() {
    onSwipeListener.onFling(BOTTOM_LEFT, TOP_LEFT, 20, 20);

    assertThat(sweptUp).isFalse();
  }

  @Test
  public void onFling_whenTooShort_doesNotdetectsSwipeUp() {
    onSwipeListener.onFling(
        MotionEvent.obtain(0, 0, 0, 0, 300, 0), MotionEvent.obtain(0, 0, 0, 0, 290, 0), 20, 250);

    assertThat(sweptUp).isFalse();
  }

  @Test
  public void onFling_whenTooDiagonal_doesNotdetectsSwipeUp() {
    onSwipeListener.onFling(
        MotionEvent.obtain(0, 0, 0, 0, 300, 0), MotionEvent.obtain(0, 0, 0, 300, 0, 0), 20, 250);

    assertThat(sweptUp).isFalse();
  }
}
