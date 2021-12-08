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

package com.googletest.firebase.perf.testapp.ui.notifications;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.googletest.firebase.perf.testapp.ListAdapter;
import com.googletest.firebase.perf.testapp.R;
import com.googletest.firebase.perf.testapp.SharedViewModel;

public class NotificationsFragment extends Fragment {

  public static final int NUM_LIST_ITEMS = 100;

  private NotificationsViewModel notificationsViewModel;
  private SharedViewModel sharedViewModel;

  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    notificationsViewModel =
        new ViewModelProvider(this, new ViewModelProvider.NewInstanceFactory())
            .get(NotificationsViewModel.class);
    View root = inflater.inflate(R.layout.fragment_notifications, container, false);
    final TextView textView = root.findViewById(R.id.text_notifications);
    notificationsViewModel
        .getText()
        .observe(
            getViewLifecycleOwner(),
            new Observer<String>() {
              @Override
              public void onChanged(@Nullable String s) {
                textView.setText(s);
              }
            });
    // Gif loading for testing
    sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
    final ImageView imageView = root.findViewById(R.id.img_notifications);
    sharedViewModel
        .getImageSrc()
        .observe(
            getViewLifecycleOwner(),
            new Observer<String>() {
              @Override
              public void onChanged(String s) {
                Glide.with(requireActivity())
                    .load(s)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(imageView);
              }
            });

    // Recycler View setup
    RecyclerView numbersList = root.findViewById(R.id.rv_fragment_notifications);
    LinearLayoutManager layoutManager = new LinearLayoutManager(requireActivity());
    ListAdapter listAdapter = new ListAdapter(NUM_LIST_ITEMS);

    numbersList.setLayoutManager(layoutManager);
    numbersList.setHasFixedSize(true);
    numbersList.setAdapter(listAdapter);
    return root;
  }
}
