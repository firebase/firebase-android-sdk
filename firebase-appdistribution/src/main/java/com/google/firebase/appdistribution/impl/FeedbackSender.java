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

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.annotations.concurrent.Lightweight;
import java.util.concurrent.Executor;
import javax.inject.Inject;

/** Sends tester feedback to the Tester API. */
class FeedbackSender {
  private final FirebaseAppDistributionTesterApiClient testerApiClient;
  @Lightweight private final Executor lightweightExecutor;

  @Inject
  FeedbackSender(
      FirebaseAppDistributionTesterApiClient testerApiClient,
      @Lightweight Executor lightweightExecutor) {
    this.testerApiClient = testerApiClient;
    this.lightweightExecutor = lightweightExecutor;
  }

  /** Send feedback text and optionally a screenshot to the Tester API for the given release. */
  Task<Void> sendFeedback(
      String releaseName,
      String feedbackText,
      @Nullable Uri screenshotUri,
      FeedbackTrigger trigger) {
    return testerApiClient
        .createFeedback(releaseName, feedbackText)
        .onSuccessTask(
            lightweightExecutor, feedbackName -> attachScreenshot(feedbackName, screenshotUri))
        .onSuccessTask(
            lightweightExecutor,
            feedbackName -> testerApiClient.commitFeedback(feedbackName, trigger));
  }

  private Task<String> attachScreenshot(String feedbackName, @Nullable Uri screenshotUri) {
    if (screenshotUri == null) {
      return Tasks.forResult(feedbackName);
    }
    return testerApiClient.attachScreenshot(feedbackName, screenshotUri);
  }
}
