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
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import com.google.firebase.inappmessaging.display.R;
import com.google.firebase.inappmessaging.display.internal.Logging;
import com.google.firebase.inappmessaging.display.internal.layout.util.MeasureUtils;
import com.google.firebase.inappmessaging.display.internal.layout.util.VerticalViewGroupMeasure;
import com.google.firebase.inappmessaging.display.internal.layout.util.ViewMeasure;

/**
 * Layout used for portrait modal view.
 *
 * @hide
 */
public class ModalLayoutPortrait extends BaseModalLayout {

  private static final int ITEM_SPACING_DP = 24;

  private VerticalViewGroupMeasure vgm;

  private int vertItemSpacing;

  public ModalLayoutPortrait(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    vgm = new VerticalViewGroupMeasure();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    vertItemSpacing = dpToPixels(ITEM_SPACING_DP);

    int horizPadding = getPaddingRight() + getPaddingLeft();
    int vertPadding = getPaddingBottom() + getPaddingTop();

    int baseLayoutWidth = calculateBaseWidth(widthMeasureSpec);
    int baseLayoutHeight = calculateBaseHeight(heightMeasureSpec);

    int totalVerticalSpacing = (getVisibleChildren().size() - 1) * vertItemSpacing;
    int reservedHeight = vertPadding + totalVerticalSpacing;

    vgm.reset(baseLayoutWidth, baseLayoutHeight);
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      vgm.add(child, isFlex(child));
    }

    Logging.logd("Screen dimens: " + getDisplayMetrics());
    Logging.logdPair("Max pct", getMaxWidthPct(), getMaxHeightPct());
    Logging.logdPair("Base dimens", baseLayoutWidth, baseLayoutHeight);

    // Do an initial pass and see if we will fit vertically, by measuring each child to
    // find out how big it would be if it took up all space.
    int totalDesiredHeight = reservedHeight;
    for (ViewMeasure vm : vgm.getViews()) {
      Logging.logd("Pre-measure child");
      vm.preMeasure(baseLayoutWidth, baseLayoutHeight);
    }
    totalDesiredHeight += vgm.getTotalHeight();

    Logging.logdNumber("Total reserved height", reservedHeight);
    Logging.logdNumber("Total desired height", totalDesiredHeight);

    // If the height is vertically constrained, re-flow flexible items
    // to fit proportionally within the space available.
    boolean isHeightConstrained = (totalDesiredHeight > baseLayoutHeight);
    Logging.logd("Total height constrained: " + isHeightConstrained);

    if (isHeightConstrained) {
      int vertSpaceAvail = baseLayoutHeight - reservedHeight;
      int flexAvail = vertSpaceAvail - vgm.getTotalFixedHeight();
      vgm.allocateSpace(flexAvail);
    }

    // Lay out each child, keeping track of height used as we go.
    int heightUsed = reservedHeight;
    int maxChildWidth = baseLayoutWidth - horizPadding;
    for (ViewMeasure vm : vgm.getViews()) {
      Logging.logd("Measuring child");
      MeasureUtils.measureAtMost(vm.getView(), maxChildWidth, vm.getMaxHeight());
      heightUsed += getDesiredHeight(vm.getView());
    }

    Logging.logdPair("Measured dims", baseLayoutWidth, heightUsed);
    setMeasuredDimension(baseLayoutWidth, heightUsed);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);

    int y = getPaddingTop();
    int x = getPaddingLeft();

    int numVisible = getVisibleChildren().size();
    for (int i = 0; i < numVisible; i++) {
      View child = getVisibleChildren().get(i);
      FrameLayout.LayoutParams layoutParams = getLayoutParams(child);

      int childHeight = child.getMeasuredHeight();
      int childWidth = child.getMeasuredWidth();

      int childTop = y;
      int childBottom = y + childHeight;

      // We only allow center_horizontal custom gravity, otherwise
      // we use top|left gravity by default.
      int childLeft;
      int childRight;
      if ((layoutParams.gravity & Gravity.CENTER_HORIZONTAL) == 1) {
        int centerOffset = (right - left) / 2;
        int halfWidth = childWidth / 2;

        childLeft = centerOffset - halfWidth;
        childRight = centerOffset + halfWidth;
      } else {
        childLeft = x;
        childRight = x + childWidth;
      }

      Logging.logd("Layout child " + i);
      Logging.logdPair("\t(top, bottom)", childTop, childBottom);
      Logging.logdPair("\t(left, right)", childLeft, childRight);
      child.layout(childLeft, childTop, childRight, childBottom);

      // Move down by the height the child used
      y += child.getMeasuredHeight();

      // Add the same spacing after each item (besides the last)
      if (i < (numVisible - 1)) {
        y += vertItemSpacing;
      }
    }
  }

  // TODO: Mark flexible items with an attribute and then don't care about IDs at all in this class
  private boolean isFlex(View child) {
    return (child.getId() == R.id.body_scroll || child.getId() == R.id.image_view);
  }
}
