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
public class ModalLayoutLandscape extends BaseModalLayout {

  private static final int ITEM_SPACING_DP = 24;
  private static final float MAX_IMG_WIDTH_PCT = 0.40f;

  private View imageChild;
  private View titleChild;
  private View scrollChild;
  private View buttonChild;

  private int barrierWidth;
  private int vertItemSpacing;

  private int leftContentHeight;
  private int rightContentHeight;

  public ModalLayoutLandscape(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    imageChild = findChildById(R.id.image_view);

    titleChild = findChildById(R.id.message_title);
    scrollChild = findChildById(R.id.body_scroll);
    buttonChild = findChildById(R.id.button);

    barrierWidth = imageChild.getVisibility() == View.GONE ? 0 : dpToPixels(ITEM_SPACING_DP);
    vertItemSpacing = dpToPixels(ITEM_SPACING_DP);

    List<View> rightCol = Arrays.asList(titleChild, scrollChild, buttonChild);

    int horizPadding = getPaddingLeft() + getPaddingRight();
    int vertPadding = getPaddingBottom() + getPaddingTop();

    int baseLayoutWidth = calculateBaseWidth(widthMeasureSpec);
    int baseLayoutHeight = calculateBaseHeight(heightMeasureSpec);

    int innerHeight = baseLayoutHeight - vertPadding;
    int innerWidth = baseLayoutWidth - horizPadding;

    // Measure the image to be max of all the height and x% of the width
    Logging.logd("Measuring image");
    MeasureUtils.measureAtMost(imageChild, (int) (innerWidth * MAX_IMG_WIDTH_PCT), innerHeight);

    // The maximum right column width is the max dialog width minus the size of the image and
    // any necessary padding, including the "barrier" between the image and the right col.
    int leftColumnWidth = getDesiredWidth(imageChild);
    int rightColumnMaxWidth = innerWidth - (leftColumnWidth + barrierWidth);
    Logging.logdPair("Max col widths (l, r)", leftColumnWidth, rightColumnMaxWidth);

    // Determine how many things in right col are visible. If there are "n" items we need "n-1"
    // reserved vertical spaces between them. The available height for all of the actual content
    // in the right column is the inner dialog height minus this spacing.
    int rightVisible = 0;
    for (View view : rightCol) {
      if (view.getVisibility() != View.GONE) {
        rightVisible++;
      }
    }

    int rightSpacingTotal = Math.max(0, (rightVisible - 1) * vertItemSpacing);
    int rightHeightAvail = innerHeight - rightSpacingTotal;

    // The title and the button should be given as much vertical space as they need to draw. The
    // scroll view is given any remaining content space.
    Logging.logd("Measuring getTitle");
    MeasureUtils.measureAtMost(titleChild, rightColumnMaxWidth, rightHeightAvail);

    Logging.logd("Measuring button");
    MeasureUtils.measureAtMost(buttonChild, rightColumnMaxWidth, rightHeightAvail);

    Logging.logd("Measuring scroll view");
    int scrollHeight =
        rightHeightAvail - getDesiredHeight(titleChild) - getDesiredHeight(buttonChild);
    MeasureUtils.measureAtMost(scrollChild, rightColumnMaxWidth, scrollHeight);

    // Need to track the total height of each column. This information is used in the layout
    // step, since the "shorter" column needs to be centered vertically with respect to the
    // "taller" column, which sets the height of the whole modal.
    leftContentHeight = getDesiredHeight(imageChild);
    rightContentHeight = rightSpacingTotal;
    for (View view : rightCol) {
      rightContentHeight += getDesiredHeight(view);
    }

    int leftHeight = leftContentHeight + vertPadding;
    int rightHeight = rightContentHeight + vertPadding;

    int totalHeight = Math.max(leftHeight, rightHeight);

    // The right column shrinks horizontally based on the size of the widest member.
    int rightColumnWidth = 0;
    for (View view : rightCol) {
      rightColumnWidth = Math.max(getDesiredWidth(view), rightColumnWidth);
    }

    Logging.logdPair("Measured columns (l, r)", leftColumnWidth, rightColumnWidth);
    int totalWidth = leftColumnWidth + rightColumnWidth + barrierWidth + horizPadding;

    // Measure this view
    Logging.logdPair("Measured dims", totalWidth, totalHeight);
    setMeasuredDimension(totalWidth, totalHeight);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);

    // Define a "frame" inside the layout to account for padding
    int childrenLeft = getPaddingLeft();
    int childrenTop = getPaddingTop();
    int childrenRight = getMeasuredWidth() - getPaddingRight();

    // One column is taller than the other, so the shorter one needs to be offset
    // vertically to create vertical centering.
    int leftTopOffset = 0;
    int rightTopOffset = 0;

    if (leftContentHeight < rightContentHeight) {
      leftTopOffset = (rightContentHeight - leftContentHeight) / 2;
    } else {
      rightTopOffset = (leftContentHeight - rightContentHeight) / 2;
    }

    Logging.logd("Layout image");
    int imageLeft = childrenLeft;
    int imageTop = childrenTop + leftTopOffset;
    int imageRight = imageLeft + getDesiredWidth(imageChild);
    int imageBottom = imageTop + getDesiredHeight(imageChild);
    layoutChild(imageChild, imageLeft, imageTop, imageRight, imageBottom);

    int rightColLeft = imageRight + barrierWidth;

    Logging.logd("Layout getTitle");
    int titleLeft = rightColLeft;
    int titleTop = childrenTop + rightTopOffset;
    int titleRight = childrenRight;
    int titleBottom = titleTop + getDesiredHeight(titleChild);
    layoutChild(titleChild, titleLeft, titleTop, titleRight, titleBottom);

    Logging.logd("Layout getBody");
    int scrollMarginTop = titleChild.getVisibility() == View.GONE ? 0 : vertItemSpacing;
    int scrollLeft = rightColLeft;
    int scrollTop = titleBottom + scrollMarginTop;
    int scrollBottom = scrollTop + getDesiredHeight(scrollChild);
    int scrollRight = childrenRight;
    layoutChild(scrollChild, scrollLeft, scrollTop, scrollRight, scrollBottom);

    Logging.logd("Layout button");
    int buttonMarginTop = scrollChild.getVisibility() == View.GONE ? 0 : vertItemSpacing;
    int buttonLeft = rightColLeft;
    int buttonTop = scrollBottom + buttonMarginTop;
    layoutChild(buttonChild, buttonLeft, buttonTop);
  }

  protected void layoutCenterHorizontal(View child, int left, int top, int right, int bottom) {
    int centerOffset = (right - left) / 2;
    int halfWidth = child.getMeasuredWidth() / 2;

    int childLeft = left + centerOffset - halfWidth;
    int childRight = left + centerOffset + halfWidth;

    layoutChild(child, childLeft, top, childRight, bottom);
  }
}
