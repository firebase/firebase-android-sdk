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
    Logging.logdPair("\tdesired (w,h)", child.getMeasuredWidth(), child.getMeasuredHeight());
    if (child.getVisibility() == View.GONE) {
      width = 0;
      height = 0;
    }

    child.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
    Logging.logdPair("\tactual (w,h)", child.getMeasuredWidth(), child.getMeasuredHeight());
  }
}
