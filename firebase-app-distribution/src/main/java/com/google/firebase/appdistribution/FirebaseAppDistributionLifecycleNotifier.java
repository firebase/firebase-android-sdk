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

package com.google.firebase.appdistribution;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.appdistribution.Constants.ErrorMessages;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import java.util.ArrayDeque;
import java.util.Queue;

class FirebaseAppDistributionLifecycleNotifier implements Application.ActivityLifecycleCallbacks {

  private static FirebaseAppDistributionLifecycleNotifier instance;
  private final Object lock = new Object();

  @GuardedBy("lock")
  private Activity currentActivity;

  /** A queue of listeners that trigger when the activity is foregrounded */
  @GuardedBy("lock")
  private final Queue<OnActivityCreatedListener> onActivityCreatedListeners = new ArrayDeque<>();

  /** A queue of listeners that trigger when the activity is foregrounded */
  @GuardedBy("lock")
  private final Queue<OnActivityStartedListener> onActivityStartedListeners = new ArrayDeque<>();

  /** A queue of listeners that trigger when the activity is backgrounded */
  @GuardedBy("lock")
  private final Queue<OnActivityPausedListener> onActivityPausedListeners = new ArrayDeque<>();

  /** A queue of listeners that trigger when the activity is destroyed */
  @GuardedBy("lock")
  private final Queue<OnActivityDestroyedListener> onDestroyedListeners = new ArrayDeque<>();

  private FirebaseAppDistributionLifecycleNotifier() {}

  static synchronized FirebaseAppDistributionLifecycleNotifier getInstance() {
    if (instance == null) {
      instance = new FirebaseAppDistributionLifecycleNotifier();
    }
    return instance;
  }

  interface OnActivityCreatedListener {
    void onCreated(Activity activity);
  }

  interface OnActivityStartedListener {
    void onStarted(Activity activity);
  }

  interface OnActivityPausedListener {
    void onPaused(Activity activity);
  }

  interface OnActivityDestroyedListener {
    void onDestroyed(Activity activity);
  }

  Activity getCurrentActivity() {
    synchronized (lock) {
      return currentActivity;
    }
  }

  Activity getNonNullCurrentActivity() throws FirebaseAppDistributionException {
    synchronized (lock) {
      if (currentActivity == null) {
        throw new FirebaseAppDistributionException(
            ErrorMessages.APP_BACKGROUNDED, Status.FOREGROUND_ACTIVITY_NOT_AVAILABLE);
      }
      return currentActivity;
    }
  }

  void addOnActivityCreatedListener(@NonNull OnActivityCreatedListener listener) {
    synchronized (lock) {
      this.onActivityCreatedListeners.add(listener);
    }
  }

  void addOnActivityStartedListener(@NonNull OnActivityStartedListener listener) {
    synchronized (lock) {
      this.onActivityStartedListeners.add(listener);
    }
  }

  void addOnActivityDestroyedListener(@NonNull OnActivityDestroyedListener listener) {
    synchronized (lock) {
      this.onDestroyedListeners.add(listener);
    }
  }

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
    synchronized (lock) {
      currentActivity = activity;
      for (OnActivityCreatedListener listener : onActivityCreatedListeners) {
        listener.onCreated(activity);
      }
    }
  }

  @Override
  public void onActivityStarted(@NonNull Activity activity) {
    synchronized (lock) {
      currentActivity = activity;
      for (OnActivityStartedListener listener : onActivityStartedListeners) {
        listener.onStarted(activity);
      }
    }
  }

  @Override
  public void onActivityResumed(@NonNull Activity activity) {}

  @Override
  public void onActivityPaused(@NonNull Activity activity) {
    synchronized (lock) {
      if (this.currentActivity == activity) {
        this.currentActivity = null;
      }
      for (OnActivityPausedListener listener : onActivityPausedListeners) {
        listener.onPaused(activity);
      }
    }
  }

  @Override
  public void onActivityStopped(@NonNull Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {}

  @Override
  public void onActivityDestroyed(@NonNull Activity activity) {
    synchronized (lock) {
      if (this.currentActivity == activity) {
        this.currentActivity = null;
      }

      for (OnActivityDestroyedListener listener : onDestroyedListeners) {
        listener.onDestroyed(activity);
      }
    }
  }
}
