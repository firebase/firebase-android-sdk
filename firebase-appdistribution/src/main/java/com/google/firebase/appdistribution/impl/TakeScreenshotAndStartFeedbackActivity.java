// Copyright 2022 Google LLC
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

package com.google.firebase.appdistribution.impl;

import android.app.Activity;
import android.os.Bundle;
import com.google.firebase.appdistribution.FirebaseAppDistribution;

public class TakeScreenshotAndStartFeedbackActivity extends Activity {

  private static final String TAG = "TakeScreenshotAndStartFeedbackActivity";

  public static final String INFO_TEXT_EXTRA_KEY =
      "com.google.firebase.appdistribution.TakeScreenshotAndStartFeedbackActivity.INFO_TEXT";

  private CharSequence infoText;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    infoText = getIntent().getCharSequenceExtra(INFO_TEXT_EXTRA_KEY);
    LogWrapper.i(TAG, "Capturing screenshot and starting feedback");
    FirebaseAppDistribution.getInstance().startFeedback(infoText);
    finish();
  }
}
