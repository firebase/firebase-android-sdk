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

/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.inappmessaging.display.internal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import androidx.annotation.Nullable;

/**
 * A {@link View.OnTouchListener} that makes any {@link View} dismissable when the user swipes
 * (drags her finger) horizontally across the view.
 *
 * <p>Example usage:
 *
 * <pre>
 * view.setOnTouchListener(new SwipeDismissTouchListener(
 *         view,
 *         null, // Optional token/cookie object
 *         new SwipeDismissTouchListener.OnDismissCallback() {
 *             public void onDismiss(View view, Object token) {
 *                 parent.removeView(view);
 *             }
 *         }));
 * </pre>
 *
 * <p>This class Requires API level 12 or later due to use of {@link
 * android.view.ViewPropertyAnimator}.
 *
 * @hide
 */
public class SwipeDismissTouchListener implements View.OnTouchListener {
  // Cached ViewConfiguration and system-wide constant values
  private int mSlop;
  private int mMinFlingVelocity;
  private int mMaxFlingVelocity;
  private long mAnimationTime;

  // Fixed properties
  private View mView;
  private DismissCallbacks mDismissCallbacks;
  private int mViewWidth = 1; // 1 and not 0 to prevent dividing by zero

  // Transient properties
  private float mDownX;
  private float mDownY;
  private boolean mSwiping;
  private int mSwipingSlop;
  private Object mToken;
  private VelocityTracker mVelocityTracker;
  private float mTranslationX;

  /**
   * The callback interface used by {@link SwipeDismissTouchListener} to inform its client about a
   * successful dismissal of the view for which it was created.
   */
  public interface DismissCallbacks {
    /** Called to determine whether the view can be dismissed. */
    boolean canDismiss(Object token);

    /**
     * Called when the user has indicated they she would like to dismiss the view.
     *
     * @param view The originating {@link View} to be dismissed.
     * @param token The optional token passed to this object's constructor.
     */
    void onDismiss(View view, Object token);
  }

  /**
   * Constructs a new swipe-to-dismiss touch listener for the given view.
   *
   * @param view The view to make dismissable.
   * @param token An optional token/cookie object to be passed through to the callback.
   * @param callbacks The callback to trigger when the user has indicated that she would like to
   *     dismiss this view.
   */
  public SwipeDismissTouchListener(View view, Object token, DismissCallbacks callbacks) {
    ViewConfiguration vc = ViewConfiguration.get(view.getContext());
    mSlop = vc.getScaledTouchSlop();
    mMinFlingVelocity = vc.getScaledMinimumFlingVelocity() * 16;
    mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
    mAnimationTime =
        view.getContext().getResources().getInteger(android.R.integer.config_shortAnimTime);
    mView = view;
    mToken = token;
    mDismissCallbacks = callbacks;
  }

  @Override
  @SuppressLint("ClickableViewAccessibility")
  public boolean onTouch(View view, MotionEvent motionEvent) {
    // offset because the view is translated during swipe
    motionEvent.offsetLocation(mTranslationX, 0);

    if (mViewWidth < 2) {
      mViewWidth = mView.getWidth();
    }

    switch (motionEvent.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        {
          // TODO: ensure this is a finger, and set a flag
          mDownX = motionEvent.getRawX();
          mDownY = motionEvent.getRawY();
          if (mDismissCallbacks.canDismiss(mToken)) {
            mVelocityTracker = VelocityTracker.obtain();
            mVelocityTracker.addMovement(motionEvent);
          }
          return false;
        }

      case MotionEvent.ACTION_UP:
        {
          if (mVelocityTracker == null) {
            break;
          }

          float deltaX = motionEvent.getRawX() - mDownX;
          mVelocityTracker.addMovement(motionEvent);
          mVelocityTracker.computeCurrentVelocity(1000);
          float velocityX = mVelocityTracker.getXVelocity();
          float absVelocityX = Math.abs(velocityX);
          float absVelocityY = Math.abs(mVelocityTracker.getYVelocity());
          boolean dismiss = false;
          boolean dismissRight = false;
          if (Math.abs(deltaX) > mViewWidth / 2 && mSwiping) {
            dismiss = true;
            dismissRight = deltaX > 0;
          } else if (mMinFlingVelocity <= absVelocityX
              && absVelocityX <= mMaxFlingVelocity
              && absVelocityY < absVelocityX
              && absVelocityY < absVelocityX
              && mSwiping) {
            // dismiss only if flinging in the same direction as dragging
            dismiss = (velocityX < 0) == (deltaX < 0);
            dismissRight = mVelocityTracker.getXVelocity() > 0;
          }
          if (dismiss) {
            // dismiss
            startDismissAnimation(dismissRight);
          } else if (mSwiping) {
            // cancel
            startCancelAnimation();
          }

          if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
          }
          mVelocityTracker = null;
          mTranslationX = 0;
          mDownX = 0;
          mDownY = 0;
          mSwiping = false;
          break;
        }

      case MotionEvent.ACTION_CANCEL:
        {
          if (mVelocityTracker == null) {
            break;
          }
          startCancelAnimation();
          mVelocityTracker.recycle();
          mVelocityTracker = null;
          mTranslationX = 0;
          mDownX = 0;
          mDownY = 0;
          mSwiping = false;
          break;
        }

      case MotionEvent.ACTION_MOVE:
        {
          if (mVelocityTracker == null) {
            break;
          }

          mVelocityTracker.addMovement(motionEvent);
          float deltaX = motionEvent.getRawX() - mDownX;
          float deltaY = motionEvent.getRawY() - mDownY;
          if (Math.abs(deltaX) > mSlop && Math.abs(deltaY) < Math.abs(deltaX) / 2) {
            mSwiping = true;
            mSwipingSlop = (deltaX > 0 ? mSlop : -mSlop);
            mView.getParent().requestDisallowInterceptTouchEvent(true);

            // Cancel listview's touch
            MotionEvent cancelEvent = MotionEvent.obtain(motionEvent);
            cancelEvent.setAction(
                MotionEvent.ACTION_CANCEL
                    | (motionEvent.getActionIndex() << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
            mView.onTouchEvent(cancelEvent);
            cancelEvent.recycle();
          }

          if (mSwiping) {
            mTranslationX = deltaX;
            setTranslationX(deltaX - mSwipingSlop);
            // TODO: use an ease-out interpolator or such
            setAlpha(Math.max(0f, Math.min(1f, 1f - 2f * Math.abs(deltaX) / mViewWidth)));
            return true;
          }
          break;
        }
    }
    return false;
  }

  protected void setTranslationX(float translationX) {
    mView.setTranslationX(translationX);
  }

  protected float getTranslationX() {
    return mView.getTranslationX();
  }

  protected void setAlpha(float alpha) {
    mView.setAlpha(alpha);
  }

  protected void startDismissAnimation(boolean dismissRight) {
    // Animate the view from the current X position to the edge, while also fading to 0 opacity.
    final float endTranslation = dismissRight ? mViewWidth : -mViewWidth;
    final float endAlpha = 0;

    animateTo(
        endTranslation,
        endAlpha,
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            performDismiss();
          }
        });
  }

  protected void startCancelAnimation() {
    // Animate the view from current X position to x=0, also raise the opacity back to 1.
    animateTo(0, 1, null);
  }

  private void animateTo(
      float translationX, float alpha, @Nullable AnimatorListenerAdapter listener) {
    // Animate the view from the current X position to the edge, while also fading to 0 opacity.
    final float beginTranslation = getTranslationX();
    final float translationDiff = (translationX - beginTranslation);

    final float beginAlpha = mView.getAlpha();
    final float alphaDiff = (alpha - beginAlpha);

    ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
    animator.setDuration(mAnimationTime);

    animator.addUpdateListener(
        new ValueAnimator.AnimatorUpdateListener() {
          @Override
          public void onAnimationUpdate(ValueAnimator valueAnimator) {
            float translationX =
                beginTranslation + (valueAnimator.getAnimatedFraction() * translationDiff);
            float alpha = beginAlpha + (valueAnimator.getAnimatedFraction() * alphaDiff);
            setTranslationX(translationX);
            setAlpha(alpha);
          }
        });

    if (listener != null) {
      animator.addListener(listener);
    }

    animator.start();
  }

  private void performDismiss() {
    // Animate the dismissed view to zero-height and then fire the dismiss callback.
    // This triggers layout on each animation frame; in the future we may want to do something
    // smarter and more performant.

    final ViewGroup.LayoutParams lp = mView.getLayoutParams();
    final int originalHeight = mView.getHeight();

    ValueAnimator animator = ValueAnimator.ofInt(originalHeight, 1).setDuration(mAnimationTime);

    animator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            mDismissCallbacks.onDismiss(mView, mToken);
            // Reset view presentation
            mView.setAlpha(1f);
            mView.setTranslationX(0);
            lp.height = originalHeight;
            mView.setLayoutParams(lp);
          }
        });

    animator.addUpdateListener(
        new ValueAnimator.AnimatorUpdateListener() {
          @Override
          public void onAnimationUpdate(ValueAnimator valueAnimator) {
            lp.height = (Integer) valueAnimator.getAnimatedValue();
            mView.setLayoutParams(lp);
          }
        });

    animator.start();
  }
}
