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

package com.googletest.firebase.perf.testapp;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import com.google.firebase.perf.metrics.AddTrace;
import com.google.firebase.perf.metrics.Trace;

/** An empty Activity to test the Trace parcelability */
public class AnotherActivity extends Activity {

  private static final String LOG_TAG = "FirebasePerfTestApp";

  @Override
  @AddTrace(name = "AnotherActivity.onCreate", enabled = true)
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Log.d(LOG_TAG, "AnotherActivity.onCreate");

    setContentView(R.layout.another_activity);
    Trace trace = getIntent().getParcelableExtra("trace");
    trace.stop();
    finish();
  }
}
