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

package com.google.firebase.app.distribution;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Queue;

public class FirebaseAppDistributionLifecycleNotifier
    implements Application.ActivityLifecycleCallbacks {

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

  public static synchronized FirebaseAppDistributionLifecycleNotifier getInstance() {
    if (instance == null) {
      instance = new FirebaseAppDistributionLifecycleNotifier();
    }
    return instance;
  }

  public interface OnActivityCreatedListener {
    void onCreated(Activity activity);
  }

  public interface OnActivityStartedListener {
    void onStarted(Activity activity);
  }

  public interface OnActivityPausedListener {
    void onPaused(Activity activity);
  }

  public interface OnActivityDestroyedListener {
    void onDestroyed(Activity activity);
  }

  public Activity getCurrentActivity() {
    synchronized (lock) {
      return currentActivity;
    }
  }

  public void addOnActivityCreatedListener(@NonNull OnActivityCreatedListener listener) {
    synchronized (lock) {
      this.onActivityCreatedListeners.add(listener);
    }
  }

  public void addOnActivityStartedListener(@NonNull OnActivityStartedListener listener) {
    synchronized (lock) {
      this.onActivityStartedListeners.add(listener);
    }
  }

  public void addOnActivityDestroyedListener(@NonNull OnActivityDestroyedListener listener) {
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
