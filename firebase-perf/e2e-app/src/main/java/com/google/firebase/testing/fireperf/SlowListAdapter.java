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
    try {
      if (position % 15 == 0) {
        Thread.sleep(900);
      } else if (position % 5 == 0) {
        Thread.sleep(50);
      }
    } catch (InterruptedException e) {
      Log.e(LOG_TAG, e.getMessage(), e);
    }

    holder.bind(position);
  }
}
