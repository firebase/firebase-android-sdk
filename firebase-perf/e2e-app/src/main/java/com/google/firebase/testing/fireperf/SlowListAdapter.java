// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.testing.fireperf;

import android.util.Log;
import org.jetbrains.annotations.NotNull;

public class SlowListAdapter extends ListAdapter {
  private static final String LOG_TAG = SlowListAdapter.class.getSimpleName();
  /**
   * Constructor for ListAdapter that accepts a number of items to display.
   *
   * @param numberOfItems Number of items to display in list
   */
  public SlowListAdapter(int numberOfItems) {
    super(numberOfItems);
  }

  @Override
  public void onBindViewHolder(@NotNull NumberViewHolder holder, int position) {
    // sleep thread to simulate jank
    try {
      if (position % 15 == 0) {
        Thread.sleep(900);
      } else if (position % 5 == 0) {
        Thread.sleep(50);
      }
    } catch (InterruptedException e) {
      Log.e(LOG_TAG, e.getMessage(), e);
    }

    super.onBindViewHolder(holder, position);
  }
}
