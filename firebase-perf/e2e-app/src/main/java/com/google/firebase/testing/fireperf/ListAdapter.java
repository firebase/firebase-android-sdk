// Copyright 2020 Google LLC
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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.jetbrains.annotations.NotNull;

/** The Adapter for the ScreenTraces test ListView. */
public class ListAdapter extends RecyclerView.Adapter<ListAdapter.NumberViewHolder> {

  private final int numberOfItems;

  /**
   * Constructor for ListAdapter that accepts a number of items to display.
   *
   * @param numberOfItems Number of items to display in list
   */
  public ListAdapter(int numberOfItems) {
    this.numberOfItems = numberOfItems;
  }

  @NotNull
  @Override
  public NumberViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
    return new NumberViewHolder(
        LayoutInflater.from(viewGroup.getContext())
            .inflate(R.layout.number_list_item, viewGroup, /* attachToRoot= */ false));
  }

  @Override
  public void onBindViewHolder(@NotNull NumberViewHolder holder, int position) {
    holder.bind(position);
  }

  @Override
  public int getItemCount() {
    return numberOfItems;
  }

  /** Cache of the children views for a list item. */
  static class NumberViewHolder extends RecyclerView.ViewHolder {

    // Displays the position in the list, i.e 0 through getItemCount() - 1
    TextView listItemNumberView;

    /**
     * Constructor for our ViewHolder.
     *
     * @param itemView The View that you inflated in {@link
     *     ListAdapter#onCreateViewHolder(ViewGroup, int)}
     */
    NumberViewHolder(View itemView) {
      super(itemView);

      listItemNumberView = itemView.findViewById(R.id.tv_item_number);
    }

    /**
     * Sets the new index number for the view as it is to be displayed.
     *
     * @param listIndex The new listIndex this view will displayed for.
     */
    void bind(int listIndex) {
      listItemNumberView.setText(String.valueOf(listIndex));
    }
  }
}
