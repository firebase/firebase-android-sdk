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
import android.widget.ScrollView;

/**
 * Metadata class used to hold some attributes of a view during measurement.
 *
 * @hide
 */
public class ViewMeasure {

  private View view;
  private boolean flex;

  private int maxWidth;
  private int maxHeight;

  /**
   * Instantiate a ViewMeasure
   *
   * @param view the child view.
   * @param flex true if the child is flexible, false if its size demands should always be
   *     considered "fixed".
   */
  public ViewMeasure(View view, boolean flex) {
    this.view = view;
    this.flex = flex;
  }

  public void preMeasure(int w, int h) {
    MeasureUtils.measureAtMost(view, w, h);
  }

  public View getView() {
    return view;
  }

  public boolean isFlex() {
    return flex;
  }

  public int getDesiredHeight() {
    if (view.getVisibility() == View.GONE) {
      return 0;
    }

    if (view instanceof ScrollView) {
      ScrollView sv = (ScrollView) view;
      return sv.getPaddingBottom() + sv.getPaddingTop() + sv.getChildAt(0).getMeasuredHeight();
    }

    return view.getMeasuredHeight();
  }

  public int getDesiredWidth() {
    if (view.getVisibility() == View.GONE) {
      return 0;
    }

    return view.getMeasuredHeight();
  }

  public int getMaxHeight() {
    return maxHeight;
  }

  public int getMaxWidth() {
    return maxWidth;
  }

  public void setMaxDimens(int w, int h) {
    this.maxWidth = w;
    this.maxHeight = h;
  }
}
