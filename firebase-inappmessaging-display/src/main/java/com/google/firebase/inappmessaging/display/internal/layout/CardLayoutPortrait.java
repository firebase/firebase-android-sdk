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

package com.google.firebase.inappmessaging.display.internal.layout;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import com.google.firebase.inappmessaging.display.R;
import com.google.firebase.inappmessaging.display.internal.Logging;
import com.google.firebase.inappmessaging.display.internal.layout.util.MeasureUtils;

/**
 * Layout used for portrait modal view.
 *
 * @hide
 */
public class CardLayoutPortrait extends BaseModalLayout {

  private View imageChild;
  private View titleChild;
  private View scrollChild;
  private View actionBarChild;
  private static double IMAGE_MAX_HEIGHT_PCT = 0.80;

  public CardLayoutPortrait(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    imageChild = findChildById(R.id.image_view);
    titleChild = findChildById(R.id.message_title);
    scrollChild = findChildById(R.id.body_scroll);
    actionBarChild = findChildById(R.id.action_bar);

    int baseLayoutWidth = calculateBaseWidth(widthMeasureSpec);
    int baseLayoutHeight = calculateBaseHeight(heightMeasureSpec);
    int maxImageHeight = roundToNearest((int) (IMAGE_MAX_HEIGHT_PCT * baseLayoutHeight), 4);

    // Measure the image to determine how much space it wants to take up.
    Logging.logd("Measuring image");
    MeasureUtils.measureFullWidth(imageChild, baseLayoutWidth, baseLayoutHeight);

    // If the image takes up more than the max height percentage then resize it.
    if (getDesiredHeight(imageChild) > maxImageHeight) {
      Logging.logd("Image exceeded maximum height, remeasuring image");
      MeasureUtils.measureFullHeight(imageChild, baseLayoutWidth, maxImageHeight);
    }

    // From now on the image width defines the width of the whole dialog
    int imageWidth = getDesiredWidth(imageChild);

    // The title and the button should be given as much vertical space as they need to draw. The
    // scroll view is given any remaining content space.
    Logging.logd("Measuring title");
    MeasureUtils.measureFullWidth(titleChild, imageWidth, baseLayoutHeight);

    Logging.logd("Measuring action bar");
    MeasureUtils.measureFullWidth(actionBarChild, imageWidth, baseLayoutHeight);

    Logging.logd("Measuring scroll view");
    int maximumScrollHeight =
        baseLayoutHeight
            - getDesiredHeight(imageChild)
            - getDesiredHeight(titleChild)
            - getDesiredHeight(actionBarChild);
    MeasureUtils.measureFullWidth(scrollChild, imageWidth, maximumScrollHeight);

    int totalHeight = 0;
    int numVisible = getVisibleChildren().size();
    for (int i = 0; i < numVisible; i++) {
      View child = getVisibleChildren().get(i);
      totalHeight += getDesiredHeight(child);
    }
    setMeasuredDimension(imageWidth, totalHeight);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);

    int x = 0;
    int y = 0;

    int numVisible = getVisibleChildren().size();
    for (int i = 0; i < numVisible; i++) {
      View child = getVisibleChildren().get(i);

      int childHeight = child.getMeasuredHeight();
      int childWidth = child.getMeasuredWidth();

      int childTop = y;
      int childBottom = y + childHeight;

      int childLeft = x;
      int childRight = x + childWidth;

      Logging.logd("Layout child " + i);
      Logging.logdPair("\t(top, bottom)", childTop, childBottom);
      Logging.logdPair("\t(left, right)", childLeft, childRight);
      child.layout(childLeft, childTop, childRight, childBottom);

      // Move down by the height the child used
      Logging.logdPair(
          "Child " + i + " wants to be ", child.getMeasuredWidth(), child.getMeasuredHeight());
      y += child.getMeasuredHeight();
    }
  }
}
