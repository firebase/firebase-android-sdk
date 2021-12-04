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

package com.googletest.firebase.perf.testapp.ui.home;

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

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.googletest.firebase.perf.testapp.R;
import com.googletest.firebase.perf.testapp.SharedViewModel;

public class HomeFragment extends Fragment {

  private HomeViewModel homeViewModel;
  private SharedViewModel sharedViewModel;

  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    homeViewModel =
        new ViewModelProvider(this, new ViewModelProvider.NewInstanceFactory())
            .get(HomeViewModel.class);
    View root = inflater.inflate(R.layout.fragment_home, container, false);
    final TextView textView = root.findViewById(R.id.text_home);
    homeViewModel
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
    final ImageView imageView = root.findViewById(R.id.img_home);
    sharedViewModel.getImageSrc().observe(getViewLifecycleOwner(), new Observer<String>() {
      @Override
      public void onChanged(String s) {
        Glide.with(requireActivity()).load(s).diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true).into(imageView);
      }
    });
    return root;
  }
}
