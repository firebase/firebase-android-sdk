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

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;

/** Sends tester feedback to the Tester API. */
class FeedbackSender {

  private final FirebaseAppDistributionTesterApiClient testerApiClient;

  FeedbackSender(FirebaseAppDistributionTesterApiClient testerApiClient) {
    this.testerApiClient = testerApiClient;
  }

  @NonNull
  static FeedbackSender getInstance() {
    return FirebaseApp.getInstance().get(FeedbackSender.class);
  }

  /** Send feedback text and screenshot to the Tester API for the given release. */
  Task<Void> sendFeedback(String releaseName, String feedbackText, Bitmap screenshot) {
    return testerApiClient
        .createFeedback(releaseName, feedbackText)
        .onSuccessTask(feedbackName -> testerApiClient.attachScreenshot(feedbackName, screenshot))
        .onSuccessTask(testerApiClient::commitFeedback);
  }
}
