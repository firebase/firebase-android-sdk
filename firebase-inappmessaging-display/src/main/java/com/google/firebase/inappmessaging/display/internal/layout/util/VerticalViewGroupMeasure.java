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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Helper class when laying out a vertical group of {@link ViewMeasure}.
 *
 * @hide
 */
public class VerticalViewGroupMeasure {

  private List<ViewMeasure> vms = new ArrayList<>();
  private int w;
  private int h;

  /**
   * Instantiate a VerticalViewGroupMeasure
   *
   * @param w the max width of the group.
   * @param h the max height of the group.
   */
  public VerticalViewGroupMeasure(int w, int h) {
    this.w = w;
    this.h = h;
  }

  /** Instantiate an empty VerticalViewGroupMeasure */
  public VerticalViewGroupMeasure() {
    this.w = 0;
    this.h = 0;
  }

  public void reset(int w, int h) {
    this.w = w;
    this.h = h;
    vms = new ArrayList<>();
  }

  public void add(View view, boolean flex) {
    ViewMeasure vm = new ViewMeasure(view, flex);
    vm.setMaxDimens(w, h);
    vms.add(vm);
  }

  public List<ViewMeasure> getViews() {
    return vms;
  }

  public int getTotalHeight() {
    int sum = 0;
    for (ViewMeasure vm : vms) {
      sum += vm.getDesiredHeight();
    }

    return sum;
  }

  /** Get the total height of all fixed children. */
  public int getTotalFixedHeight() {
    int sum = 0;
    for (ViewMeasure vm : vms) {
      if (!vm.isFlex()) {
        sum += vm.getDesiredHeight();
      }
    }

    return sum;
  }

  /**
   * Given a certain amount of space left, allocate the space proportionally among all flexible
   * children.
   */
  public void allocateSpace(int flexAvail) {
    List<ViewMeasure> flexVms = new ArrayList<>();
    for (ViewMeasure vm : vms) {
      if (vm.isFlex()) {
        flexVms.add(vm);
      }
    }

    // Sort the ViewMeasures biggest to smallest (hence the reverse comparator).
    Collections.sort(
        flexVms,
        new Comparator<ViewMeasure>() {
          @Override
          public int compare(ViewMeasure o1, ViewMeasure o2) {
            if (o1.getDesiredHeight() > o2.getDesiredHeight()) {
              return -1;
            } else if (o1.getDesiredHeight() < o2.getDesiredHeight()) {
              return 1;
            }

            return 0;
          }
        });

    // First pass, add up requested space by flexible items
    int flexSum = 0;
    for (ViewMeasure vm : flexVms) {
      flexSum += vm.getDesiredHeight();
    }

    // 2items --> 80/20 max
    // 3items --> 60/20/20 max
    // 4items --> 40/20/20/20 max
    // 5items --> 20/20/20/20/20 max
    // 6items --> this layout will break!
    int flexCount = flexVms.size();
    if (flexCount >= 6) {
      throw new IllegalStateException("VerticalViewGroupMeasure only supports up to 5 children");
    }
    float minFrac = 0.20f;
    float maxFrac = 1.0f - ((flexCount - 1) * minFrac);

    Logging.logdPair("VVGM (minFrac, maxFrac)", minFrac, maxFrac);

    // Second pass, allocate proportionally
    float extraFracPool = 0f;
    for (ViewMeasure vm : flexVms) {
      float desiredFrac = (float) vm.getDesiredHeight() / flexSum;
      float grantedFrac = desiredFrac;

      // If the view is greater than the max we want it to be, it should "return" the
      // extra pixels to a pool we can use to expand the smaller views.
      if (desiredFrac > maxFrac) {
        extraFracPool += (grantedFrac - maxFrac);
        grantedFrac = maxFrac;
      }

      // If the view is smaller than the min we want, add as much as was given back to the pool,
      // up to the amount that brings the view into line with the minimum.
      if (desiredFrac < minFrac) {
        float remainder = minFrac - desiredFrac;
        float addOn = Math.min(remainder, extraFracPool);

        grantedFrac = desiredFrac + addOn;
        extraFracPool -= addOn;
      }

      Logging.logdPair("\t(desired, granted)", desiredFrac, grantedFrac);
      int maxHeight = (int) (grantedFrac * flexAvail);
      vm.setMaxDimens(w, maxHeight);
    }
  }
}
