// Copyright 2020 Google LLC
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

package com.google.firebase.iid;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import com.google.android.gms.cloudmessaging.CloudMessage;
import com.google.android.gms.cloudmessaging.CloudMessagingReceiver;
import com.google.android.gms.cloudmessaging.CloudMessagingReceiver.IntentActionKeys;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.messaging.FcmBroadcastProcessor;
import com.google.firebase.messaging.MessagingAnalytics;
import com.google.firebase.messaging.ServiceStarter;
import java.util.concurrent.ExecutionException;

/**
 * Implementation of {@code CloudMessagingReceiver} that passes Intents to the {@code
 * FirebaseMessagingService}.
 *
 * @hide
 */
public final class FirebaseInstanceIdReceiver extends CloudMessagingReceiver {

  private static final String TAG = "FirebaseMessaging";

  private static Intent createServiceIntent(
      @NonNull Context context, @NonNull String action, @NonNull Bundle data) {
    return new Intent(action).putExtras(data);
  }

  /** @hide */
  @Override
  @WorkerThread
  protected int onMessageReceive(@NonNull Context context, @NonNull CloudMessage message) {
    try {
      return Tasks.await(new FcmBroadcastProcessor(context).process(message.getIntent()));
    } catch (ExecutionException | InterruptedException e) {
      Log.e(TAG, "Failed to send message to service.", e);
      return ServiceStarter.ERROR_UNKNOWN;
    }
  }

  /** @hide */
  @Override
  @WorkerThread
  protected void onNotificationDismissed(@NonNull Context context, @NonNull Bundle data) {
    // Reconstruct the service intent from the data.
    Intent notificationDismissedIntent =
        createServiceIntent(context, IntentActionKeys.NOTIFICATION_DISMISS, data);
    if (MessagingAnalytics.shouldUploadScionMetrics(notificationDismissedIntent)) {
      MessagingAnalytics.logNotificationDismiss(notificationDismissedIntent);
    }
  }
}
