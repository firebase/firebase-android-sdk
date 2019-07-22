// Copyright 2019 Google LLC
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

package com.google.apksize;

import android.app.Activity;
import android.content.Context;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks;
import com.google.firebase.inappmessaging.display.FirebaseInAppMessagingDisplay;
import com.google.firebase.inappmessaging.model.InAppMessage;

public class InAppMessagingDisplay implements SampleCode {
  private static final String SAMPLE_TEXT = "My sample text";
  private static final String ACTION_URL = "https://www.example.com";
  private static final String CAMPAIGN_ID = "my_campaign";
  private static final String TITLE = "Title";

  public static class DisplayCallback implements FirebaseInAppMessagingDisplayCallbacks {
    @Override
    public Task<Void> impressionDetected() {
      return new TaskCompletionSource<Void>().getTask();
    }

    @Override
    public Task<Void> messageDismissed(InAppMessagingDismissType dismissType) {
      return new TaskCompletionSource<Void>().getTask();
    }

    @Override
    public Task<Void> messageClicked() {
      return new TaskCompletionSource<Void>().getTask();
    }

    @Override
    public Task<Void> displayErrorEncountered(InAppMessagingErrorReason InAppMessagingErrorReason) {
      return new TaskCompletionSource<Void>().getTask();
    }
  }

  @Override
  public void runSample(Context context) {
    InAppMessage message =
        InAppMessage.builder()
            .setBody(InAppMessage.Text.builder().setText(SAMPLE_TEXT).build())
            .setAction(InAppMessage.Action.builder().setActionUrl(ACTION_URL).build())
            .setCampaignId(CAMPAIGN_ID)
            .setTitle(InAppMessage.Text.builder().setText(TITLE).build())
            .build();

    // NOTE: Context is *not guaranteed* to be an Activity. This is **fine** in this case because we
    // only want to compile the APK to measure it size, and it will not be run.
    FirebaseInAppMessagingDisplay.getInstance()
        .testMessage((Activity) context, message, new DisplayCallback());
  }
}
