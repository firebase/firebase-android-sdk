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
import javax.inject.Inject;

public class TakeScreenshotAndStartFeedbackActivity extends Activity {

  private static final String TAG = "TakeScreenshotAndStartFeedbackActivity";

  public static final String ADDITIONAL_FORM_TEXT_EXTRA_KEY =
      "com.google.firebase.appdistribution.TakeScreenshotAndStartFeedbackActivity.ADDITIONAL_FORM_TEXT";

  @Inject FirebaseAppDistributionImpl firebaseAppDistribution;

  private CharSequence additionalFormText;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    // Inject members before calling super.onCreate to avoid issues with fragment restoration
    AppDistroComponent.getInstance().inject(this);

    super.onCreate(savedInstanceState);
    additionalFormText = getIntent().getCharSequenceExtra(ADDITIONAL_FORM_TEXT_EXTRA_KEY);
    LogWrapper.i(TAG, "Capturing screenshot and starting feedback");
    firebaseAppDistribution.startFeedback(additionalFormText, FeedbackTrigger.NOTIFICATION);

    finish();
  }
}
