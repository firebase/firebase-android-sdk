// Copyright 2018 Google LLC
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

package com.google.firebase.inappmessaging.display.internal;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplay;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks;
import com.google.firebase.inappmessaging.model.InAppMessage;

/** @hide */
public class FirebaseInAppMessagingDisplayImpl
    implements FirebaseInAppMessagingDisplay, Application.ActivityLifecycleCallbacks {

  public FirebaseInAppMessagingDisplayImpl() {}

  @Override
  public void displayMessage(
      InAppMessage inAppMessage, FirebaseInAppMessagingDisplayCallbacks callbacks) {}

  /** @hide */
  @Override
  public void onActivityCreated(final Activity activity, Bundle bundle) {
    Logging.logd("Created activity: " + activity.getClass().getName());
  }

  /** @hide */
  @Override
  public void onActivityPaused(Activity activity) {
    Logging.logd("Pausing activity: " + activity.getClass().getName());
  }

  /** @hide */
  @Override
  public void onActivityStopped(Activity activity) {
    Logging.logd("Stopped activity: " + activity.getClass().getName());
  }

  /** @hide */
  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
    Logging.logd("SavedInstance activity: " + activity.getClass().getName());
  }

  /** @hide */
  @Override
  public void onActivityDestroyed(Activity activity) {
    Logging.logd("Destroyed activity: " + activity.getClass().getName());
  }

  /** @hide */
  @Override
  public void onActivityStarted(Activity activity) {
    Logging.logd("Started activity: " + activity.getClass().getName());
  }

  /** @hide */
  @Override
  public void onActivityResumed(Activity activity) {
    Logging.logd("Resumed activity: " + activity.getClass().getName());
  }
}
