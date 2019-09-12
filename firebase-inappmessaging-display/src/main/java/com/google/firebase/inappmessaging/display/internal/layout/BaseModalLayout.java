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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.inappmessaging.display.R;
import com.google.firebase.inappmessaging.display.internal.Logging;
import java.util.ArrayList;
import java.util.List;

/**
 * Base custom layout class for the Modal type
 *
 * @hide
 */
public abstract class BaseModalLayout extends FrameLayout {

  private static final float DEFAULT_MAX_WIDTH_PCT = -1f;
  private static final float DEFAULT_MAX_HEIGHT_PCT = -1f;

  private float mMaxWidthPct;
  private float mMaxHeightPct;
  private DisplayMetrics mDisplay;

  private List<View> mVisibleChildren = new ArrayList<View>();

  public BaseModalLayout(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);

    TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ModalLayout, 0, 0);
    try {
      mMaxWidthPct = a.getFloat(R.styleable.ModalLayout_maxWidthPct, DEFAULT_MAX_WIDTH_PCT);
      mMaxHeightPct = a.getFloat(R.styleable.ModalLayout_maxHeightPct, DEFAULT_MAX_HEIGHT_PCT);
    } finally {
      a.recycle();
    }

    mDisplay = context.getResources().getDisplayMetrics();
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    Logging.logdHeader("BEGIN LAYOUT");
    Logging.logd("onLayout: l: " + left + ", t: " + top + ", r: " + right + ", b: " + bottom);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    Logging.logdHeader("BEGIN MEASURE");
    Logging.logdPair("Display", getDisplayMetrics().widthPixels, getDisplayMetrics().heightPixels);

    // Get a list of all visible children
    mVisibleChildren.clear();
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child.getVisibility() != GONE) {
        mVisibleChildren.add(child);
      } else {
        Logging.logdNumber("Skipping GONE child", i);
      }
    }
  }

  protected float getMaxWidthPct() {
    return mMaxWidthPct;
  }

  protected float getMaxHeightPct() {
    return mMaxHeightPct;
  }

  protected DisplayMetrics getDisplayMetrics() {
    return mDisplay;
  }

  protected List<View> getVisibleChildren() {
    return mVisibleChildren;
  }

  protected int calculateBaseWidth(int widthMeasureSpec) {
    int baseLayoutWidth;
    if (getMaxWidthPct() > 0) {
      // Some percentage of screen, rounded to the nearest 4px
      Logging.logd("Width: restrict by pct");
      baseLayoutWidth =
          roundToNearest((int) (getDisplayMetrics().widthPixels * getMaxWidthPct()), 4);
    } else {
      // Width as specified by the layout
      Logging.logd("Width: restrict by spec");
      baseLayoutWidth = MeasureSpec.getSize(widthMeasureSpec);
    }

    return baseLayoutWidth;
  }

  protected int calculateBaseHeight(int heightMeasureSpec) {
    int baseLayoutHeight;
    if (getMaxHeightPct() > 0) {
      // Some percentage of screen, rounded to the nearest 4px
      Logging.logd("Height: restrict by pct");
      baseLayoutHeight =
          roundToNearest((int) (getDisplayMetrics().heightPixels * getMaxHeightPct()), 4);
    } else {
      // Height as specified by the layout
      Logging.logd("Height: restrict by spec");
      baseLayoutHeight = MeasureSpec.getSize(heightMeasureSpec);
    }

    return baseLayoutHeight;
  }

  /** Override to add logging. */
  @Override
  protected void measureChildWithMargins(
      View child,
      int parentWidthMeasureSpec,
      int widthUsed,
      int parentHeightMeasureSpec,
      int heightUsed) {
    Logging.logdPair("\tdesired (w,h)", child.getMeasuredWidth(), child.getMeasuredHeight());
    super.measureChildWithMargins(
        child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed);
    Logging.logdPair("\tactual  (w,h)", child.getMeasuredWidth(), child.getMeasuredHeight());
  }

  /** Layout a child specifying only the (top,left) */
  protected void layoutChild(View view, int left, int top) {
    layoutChild(view, left, top, left + getDesiredWidth(view), top + getDesiredHeight(view));
  }

  /** Layout wrapper method that logs. */
  protected void layoutChild(View view, int left, int top, int right, int bottom) {
    Logging.logdPair("\tleft, right", left, right);
    Logging.logdPair("\ttop, bottom", top, bottom);
    view.layout(left, top, right, bottom);
  }

  @NonNull
  protected View findChildById(@IdRes int id) {
    View v = findViewById(id);
    if (v == null) {
      throw new IllegalStateException("No such child: " + id);
    }

    return v;
  }

  /** Get the total height a child needs. */
  protected int getHeightWithMargins(View child) {
    if (child.getVisibility() == View.GONE) {
      return 0;
    }

    FrameLayout.LayoutParams params = getLayoutParams(child);
    return getDesiredHeight(child) + params.topMargin + params.bottomMargin;
  }

  /** Visibility-sensitive bottom margin. */
  protected int getMarginBottom(View child) {
    if (child.getVisibility() == View.GONE) {
      return 0;
    }

    return getLayoutParams(child).bottomMargin;
  }

  /** Visibility-sensitive top margin. */
  protected int getMarginTop(View child) {
    if (child.getVisibility() == View.GONE) {
      return 0;
    }

    return getLayoutParams(child).topMargin;
  }

  /** Get the total height a child needs. */
  protected int getWidthWithMargins(View child) {
    if (child.getVisibility() == View.GONE) {
      return 0;
    }

    FrameLayout.LayoutParams params = getLayoutParams(child);
    return getDesiredWidth(child) + params.leftMargin + params.rightMargin;
  }

  /** Get the total width a child needs. */
  protected int getDesiredWidth(View child) {
    if (child.getVisibility() == View.GONE) {
      return 0;
    }

    return child.getMeasuredWidth();
  }

  /** Find out how big a child wants to be. */
  protected int getDesiredHeight(View child) {
    if (child.getVisibility() == View.GONE) {
      return 0;
    }

    return child.getMeasuredHeight();
  }

  /** Convenience method to save us repeated casting. */
  protected FrameLayout.LayoutParams getLayoutParams(View child) {
    return (LayoutParams) child.getLayoutParams();
  }

  /** Round "num" to the nearest multiple of "unit". */
  protected int roundToNearest(int num, int unit) {
    return unit * Math.round(num / (float) unit);
  }

  /** Convert a value in "dp" to a "px" value for the current display. */
  protected int dpToPixels(int dp) {
    return (int) Math.floor(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, mDisplay));
  }
}
