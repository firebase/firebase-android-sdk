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

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** This activity displays a ListView which when scrolled generates slow and frozen frames. */
public class FirebasePerfScreenTracesActivity extends Activity {

  private static final int NUM_LIST_ITEMS = 100;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Note(b/141889626): Forcing hardware acceleration to capture screen traces
    getWindow()
        .setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

    setContentView(R.layout.activity_screen_traces);

    RecyclerView numbersList = findViewById(R.id.rv_numbers);
    LinearLayoutManager layoutManager = new LinearLayoutManager(this);
    ListAdapter listAdapter = new ListAdapter(NUM_LIST_ITEMS);

    numbersList.setLayoutManager(layoutManager);
    numbersList.setHasFixedSize(true);
    numbersList.setAdapter(listAdapter);
  }
}
