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
package com.google.firebase.testing.fireperf.ui.slow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.testing.fireperf.R;
import com.google.firebase.testing.fireperf.SlowListAdapter;

public class SlowFragment extends Fragment {

  public static final int NUM_LIST_ITEMS = 100;

  private SlowViewModel slowViewModel;

  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    slowViewModel =
        new ViewModelProvider(this, new ViewModelProvider.NewInstanceFactory())
            .get(SlowViewModel.class);
    View root = inflater.inflate(R.layout.fragment_slow, container, false);
    TextView textView = root.findViewById(R.id.text_slow);
    slowViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

    RecyclerView numbersList = root.findViewById(R.id.rv_numbers_slow);
    numbersList.setLayoutManager(new LinearLayoutManager(requireContext()));
    numbersList.setHasFixedSize(true);
    numbersList.setAdapter(new SlowListAdapter(NUM_LIST_ITEMS));
    return root;
  }
}
