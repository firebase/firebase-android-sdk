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

package com.google.firebase.inappmessaging.display.internal.layout.util;

import android.view.View;
import com.google.firebase.inappmessaging.display.internal.Logging;

/** @hide */
public class MeasureUtils {

  /** Call "measure" on a view with the AT_MOST measurespec for the given height/width. */
  public static void measureAtMost(View child, int width, int height) {
    measure(child, width, height, View.MeasureSpec.AT_MOST, View.MeasureSpec.AT_MOST);
  }

  /** Call "measure" on a view with the EXACTLY measurespec for the given height and width. */
  public static void measureExactly(View child, int width, int height) {
    measure(child, width, height, View.MeasureSpec.EXACTLY, View.MeasureSpec.EXACTLY);
  }

  /** Call "measure" on a view with the EXACTLY measurespec for the given width. */
  public static void measureFullWidth(View child, int width, int height) {
    measure(child, width, height, View.MeasureSpec.EXACTLY, View.MeasureSpec.AT_MOST);
  }

  /** Call "measure" on a view with the EXACTLY measurespec for the given height. */
  public static void measureFullHeight(View child, int width, int height) {
    measure(child, width, height, View.MeasureSpec.AT_MOST, View.MeasureSpec.EXACTLY);
  }

  /**
   * Call "measure" on a view the provided measure specifications. The "Exactly" measure spec will
   * force a view to be exactly the specified size in the specified dimension while the "At most"
   * spec will tell the view how large it can be at most in the given dimension.
   */
  private static void measure(View child, int width, int height, int widthSpec, int heightSpec) {
    Logging.logdPair("\tdesired (w,h)", child.getMeasuredWidth(), child.getMeasuredHeight());
    if (child.getVisibility() == View.GONE) {
      width = 0;
      height = 0;
    }

    child.measure(
        View.MeasureSpec.makeMeasureSpec(width, widthSpec),
        View.MeasureSpec.makeMeasureSpec(height, heightSpec));
    Logging.logdPair("\tactual (w,h)", child.getMeasuredWidth(), child.getMeasuredHeight());
  }
}
