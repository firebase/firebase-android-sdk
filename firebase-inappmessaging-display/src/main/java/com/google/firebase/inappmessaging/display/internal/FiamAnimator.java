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

package com.google.firebase.inappmessaging.display.internal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Application;
import android.graphics.Point;
import android.view.View;
import android.view.ViewGroup;
import com.google.firebase.inappmessaging.display.internal.injection.scopes.FirebaseAppScope;
import javax.inject.Inject;

/** @hide */
@FirebaseAppScope
public class FiamAnimator {
  @Inject
  FiamAnimator() {}

  /**
   * This method currently assumes that the passed in view has {@link ViewGroup.LayoutParams} set to
   * WRAP_CONTENT
   */
  public void slideIntoView(final Application app, final View view, Position startPosition) {
    view.setAlpha(0.0f);
    Point start = Position.getPoint(startPosition, view);

    view.animate()
        .translationX(start.x)
        .translationY(start.y)
        .setDuration(1)
        .setListener(
            new AnimatorListenerAdapter() {
              @Override
              public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);

                view.animate()
                    .translationX(0)
                    .translationY(0)
                    .alpha(1.0f)
                    .setDuration(
                        app.getResources().getInteger(android.R.integer.config_longAnimTime))
                    .setListener(null);
              }
            });
  }

  public void slideOutOfView(
      final Application app,
      final View view,
      Position end,
      final AnimationCompleteListener completeListener) {
    Point start = Position.getPoint(end, view);

    AnimatorListenerAdapter animatorListenerAdapter =
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            // Remove fiam from window only after the animation is complete
            completeListener.onComplete();
          }
        };

    view.animate()
        .translationX(start.x)
        .translationY(start.y)
        .setDuration(app.getResources().getInteger(android.R.integer.config_longAnimTime))
        .setListener(animatorListenerAdapter);
  }

  public enum Position {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM;

    private static Point getPoint(Position d, View view) {
      view.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

      switch (d) {
        case LEFT:
          return new Point(-1 * view.getMeasuredWidth(), 0);
        case RIGHT:
          return new Point(1 * view.getMeasuredWidth(), 0);
        case TOP:
          return new Point(0, -1 * view.getMeasuredHeight());
        case BOTTOM:
          return new Point(0, 1 * view.getMeasuredHeight());
        default:
          return new Point(0, -1 * view.getMeasuredHeight());
      }
    }
  }

  public interface AnimationCompleteListener {
    void onComplete();
  }
}
