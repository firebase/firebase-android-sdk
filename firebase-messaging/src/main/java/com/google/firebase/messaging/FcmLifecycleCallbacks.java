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

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

class FcmLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

  /** Keep a list of intents that we've seen to avoid accidentally logging events twice. */
  private final Set<Intent> seenIntents =
      Collections.newSetFromMap(new WeakHashMap<Intent, Boolean>());

  @Override
  public void onActivityCreated(Activity createdActivity, Bundle instanceState) {
    Intent startingIntent = createdActivity.getIntent();
    if (startingIntent == null || !seenIntents.add(startingIntent)) {
      // already seen (and logged) this, no need to go any further.
      return;
    }

    Bundle extras = startingIntent.getExtras();
    if (extras != null) {
      Bundle analyticsData = extras.getBundle(Constants.MessageNotificationKeys.ANALYTICS_DATA);
      if (MessagingAnalytics.shouldUploadScionMetrics(analyticsData)) {
        MessagingAnalytics.logNotificationOpen(analyticsData);
      }
    }
  }

  @Override
  public void onActivityPaused(Activity pausedActivity) {
    if (pausedActivity.isFinishing()) {
      // iff the activity is finished we can remove the intent from our "seen" list
      seenIntents.remove(pausedActivity.getIntent());
    }
  }

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
}
