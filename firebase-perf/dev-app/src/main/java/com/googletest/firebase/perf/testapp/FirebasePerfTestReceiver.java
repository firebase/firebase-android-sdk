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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * A BroadcastReceiver to receive an action that cause app to start in background. To send a
 * ACTION_START message: adb shell am broadcast -a com.googletest.firebase.perf.testapp.ACTION_START
 */
public class FirebasePerfTestReceiver extends BroadcastReceiver {

  public static final String ACTION_START = "com.googletest.firebase.perf.testapp.ACTION_START";

  @Override
  public void onReceive(Context context, Intent intent) {
    if (ACTION_START.equals(intent.getAction())) {
      Log.d("FirePerfTestReceiver", "Received ACTION_START");
    }
  }
}
