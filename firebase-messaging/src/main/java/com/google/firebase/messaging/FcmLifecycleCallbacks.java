// Copyright 2021 Google LLC
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
package com.google.firebase.messaging;

import static com.google.firebase.messaging.Constants.TAG;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import java.util.ArrayDeque;
import java.util.Queue;

class FcmLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

  private static final int RECENTLY_LOGGED_MESSAGE_IDS_MAX_SIZE = 10;

  /** Last N message IDs that have been logged to prevent duplicate logging. */
  private final Queue<String> recentlyLoggedMessageIds =
      new ArrayDeque<>(RECENTLY_LOGGED_MESSAGE_IDS_MAX_SIZE);

  // TODO(b/258424124): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  @Override
  public void onActivityCreated(Activity createdActivity, Bundle instanceState) {
    Intent startingIntent = createdActivity.getIntent();
    if (startingIntent == null) {
      return;
    }

    if (VERSION.SDK_INT <= VERSION_CODES.N_MR1) {
      // On Android 7.1 and lower Bundle unparceling is not thread safe. Wait to log notification
      // open after Activity.onCreate() has completed to try to avoid race conditions with other
      // code that may be trying to access the Intent extras Bundle in onCreate() on a different
      // thread.
      new Handler(Looper.getMainLooper()).post(() -> logNotificationOpen(startingIntent));
    } else {
      logNotificationOpen(startingIntent);
    }
  }

  @Override
  public void onActivityPaused(Activity pausedActivity) {}

  @Override
  public void onActivityDestroyed(Activity destroyedActivity) {}

  @Override
  public void onActivityStarted(Activity activity) {}

  @Override
  public void onActivityStopped(Activity activity) {}

  @Override
  public void onActivityResumed(Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {}

  private void logNotificationOpen(Intent startingIntent) {
    Bundle analyticsData = null;
    try {
      Bundle extras = startingIntent.getExtras();
      if (extras != null) {
        String messageId = MessagingAnalytics.getMessageId(extras);
        if (!TextUtils.isEmpty(messageId)) {
          if (recentlyLoggedMessageIds.contains(messageId)) {
            // Already logged, don't log again.
            return;
          }
          recentlyLoggedMessageIds.add(messageId);
        }
        analyticsData = extras.getBundle(Constants.MessageNotificationKeys.ANALYTICS_DATA);
      }
    } catch (RuntimeException e) {
      // Don't crash if there was a problem trying to get the analytics data Bundle since the
      // Intent could be coming from anywhere and could be incorrectly formatted.
      Log.w(TAG, "Failed trying to get analytics data from Intent extras.", e);
    }
    if (MessagingAnalytics.shouldUploadScionMetrics(analyticsData)) {
      MessagingAnalytics.logNotificationOpen(analyticsData);
    }
  }
}
