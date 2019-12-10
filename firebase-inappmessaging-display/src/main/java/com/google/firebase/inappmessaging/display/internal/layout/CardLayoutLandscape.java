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
import java.util.Arrays;
import java.util.List;

/** @hide */
public class CardLayoutLandscape extends BaseModalLayout {

  private View imageChild;
  private View titleChild;
  private View scrollChild;
  private View actionBarChild;
  private static double IMAGE_MAX_WIDTH_PCT = 0.60;

  public CardLayoutLandscape(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    imageChild = findChildById(R.id.image_view);
    titleChild = findChildById(R.id.message_title);
    scrollChild = findChildById(R.id.body_scroll);
    actionBarChild = findChildById(R.id.action_bar);

    List<View> rightCol = Arrays.asList(titleChild, scrollChild, actionBarChild);

    int baseLayoutWidth = calculateBaseWidth(widthMeasureSpec);
    int baseLayoutHeight = calculateBaseHeight(heightMeasureSpec);
    int maxImageWidth = roundToNearest((int) (IMAGE_MAX_WIDTH_PCT * baseLayoutWidth), 4);

    // Measure the image to determine how much space it wants to take up.
    Logging.logd("Measuring image");
    MeasureUtils.measureFullHeight(imageChild, baseLayoutWidth, baseLayoutHeight);

    // If the image takes up more than the max width percentage then resize it.
    if (getDesiredWidth(imageChild) > maxImageWidth) {
      Logging.logd("Image exceeded maximum width, remeasuring image");
      MeasureUtils.measureFullWidth(imageChild, maxImageWidth, baseLayoutHeight);
    }

    // From now on the image height defines the height of the whole dialog
    int imageHeight = getDesiredHeight(imageChild);

    // The maximum right column width is the base dialog width minus the size of the image.
    int leftColumnWidth = getDesiredWidth(imageChild);
    int rightColumnMaxWidth = baseLayoutWidth - leftColumnWidth;
    Logging.logdPair("Max col widths (l, r)", leftColumnWidth, rightColumnMaxWidth);

    // The title and the button should be given as much vertical space as they need to draw. The
    // scroll view is given any remaining content space.
    Logging.logd("Measuring title");
    MeasureUtils.measureAtMost(titleChild, rightColumnMaxWidth, imageHeight);

    Logging.logd("Measuring action bar");
    MeasureUtils.measureAtMost(actionBarChild, rightColumnMaxWidth, imageHeight);

    // measure the scroll view using exactly with maximum height it can take up
    Logging.logd("Measuring scroll view");
    int scrollHeight =
        imageHeight - getDesiredHeight(titleChild) - getDesiredHeight(actionBarChild);
    MeasureUtils.measureFullHeight(scrollChild, rightColumnMaxWidth, scrollHeight);

    // The right column shrinks horizontally based on the size of the widest member.
    int rightColumnWidth = 0;
    for (View view : rightCol) {
      rightColumnWidth = Math.max(getDesiredWidth(view), rightColumnWidth);
    }

    Logging.logdPair("Measured columns (l, r)", leftColumnWidth, rightColumnWidth);
    int totalWidth = leftColumnWidth + rightColumnWidth;

    // Measure this view
    Logging.logdPair("Measured dims", totalWidth, imageHeight);
    setMeasuredDimension(totalWidth, imageHeight);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);

    int CONTAINER_LEFT = 0;
    int CONTAINER_TOP = 0;
    int CONTAINER_RIGHT = getMeasuredWidth();
    int CONTAINER_BOTTOM = getMeasuredHeight();

    Logging.logd("Layout image");
    // Image is set at the top right and takes up as much height and width as it desires.
    int imageLeft = CONTAINER_LEFT;
    int imageTop = CONTAINER_TOP;
    int imageRight = getDesiredWidth(imageChild);
    int imageBottom = getDesiredHeight(imageChild);
    layoutChild(imageChild, imageLeft, imageTop, imageRight, imageBottom);

    // The left side of the right column starts at the right of the image.
    int rightColLeft = imageRight;

    Logging.logd("Layout title");
    // Title starts to the right of the image and the top of the container.
    // It spans the width of the container.
    int titleLeft = rightColLeft;
    int titleTop = CONTAINER_TOP;
    int titleRight = CONTAINER_RIGHT;
    int titleBottom = getDesiredHeight(titleChild);
    layoutChild(titleChild, titleLeft, titleTop, titleRight, titleBottom);

    Logging.logd("Layout scroll");
    // The scroll view starts to the right of the image and the bottom of the title.
    // It spans the width of the container.
    int scrollLeft = rightColLeft;
    int scrollTop = titleBottom;
    int scrollBottom = scrollTop + getDesiredHeight(scrollChild);
    int scrollRight = CONTAINER_RIGHT;
    layoutChild(scrollChild, scrollLeft, scrollTop, scrollRight, scrollBottom);

    Logging.logd("Layout action bar");
    // Layout the action bar from the bottom. This is kinda a hack to get around a potentially
    // totally missing scroll view but we know this will not have issues because we've
    // already measured at this point.
    int actionBarLeft = rightColLeft;
    int actionBarTop = CONTAINER_BOTTOM - getDesiredHeight(actionBarChild);
    int actionBarRight = CONTAINER_RIGHT;
    int actionBarBottom = CONTAINER_BOTTOM;
    layoutChild(actionBarChild, actionBarLeft, actionBarTop, actionBarRight, actionBarBottom);
  }
}
