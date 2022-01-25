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
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import java.util.ArrayDeque;
import java.util.Queue;

class FirebaseAppDistributionLifecycleNotifier implements Application.ActivityLifecycleCallbacks {

  /** A functional interface for a function that takes an activity and does something with it. */
  interface ActivityConsumer {
    void consume(Activity activity) throws FirebaseAppDistributionException;
  }

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

  /** A queue of listeners that trigger when the activity is resumed */
  @GuardedBy("lock")
  private final Queue<OnActivityResumedListener> onActivityResumedListeners = new ArrayDeque<>();

  /** A queue of listeners that trigger when the activity is backgrounded */
  @GuardedBy("lock")
  private final Queue<OnActivityPausedListener> onActivityPausedListeners = new ArrayDeque<>();

  /** A queue of listeners that trigger when the activity is destroyed */
  @GuardedBy("lock")
  private final Queue<OnActivityDestroyedListener> onDestroyedListeners = new ArrayDeque<>();

  @VisibleForTesting
  FirebaseAppDistributionLifecycleNotifier() {}

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

  interface OnActivityResumedListener {
    void onResumed(Activity activity);
  }

  interface OnActivityPausedListener {
    void onPaused(Activity activity);
  }

  interface OnActivityDestroyedListener {
    void onDestroyed(Activity activity);
  }

  /**
   * Get a {@link Task} that will succeed with a result of the app's foregrounded {@link Activity},
   * when one is available.
   *
   * <p>The returned task will never fail. It will instead remain pending indefinitely until some
   * activity comes to the foreground.
   */
  Task<Activity> getForegroundActivity() {
    return getForegroundActivity(activity -> {});
  }

  /**
   * Get a {@link Task} that will succeed with a result of the app's foregrounded {@link Activity},
   * when one is available, after passing the activity to an {@link ActivityConsumer}.
   *
   * <p>The returned task will fail with a {@link FirebaseAppDistributionException} if the consumer
   * throws. Otherwise it will never fail, and will wait indefinitely for a foreground activity
   * before applying the consumer.
   */
  Task<Activity> getForegroundActivity(ActivityConsumer consumer) {
    synchronized (lock) {
      TaskCompletionSource<Activity> task = new TaskCompletionSource<>();
      if (currentActivity != null) {
        consumeActivityAndCompleteTask(task, currentActivity, consumer);
      } else {
        addOnActivityResumedListener(
            new OnActivityResumedListener() {
              @Override
              public void onResumed(Activity activity) {
                consumeActivityAndCompleteTask(task, activity, consumer);
                removeOnActivityResumedListener(this);
              }
            });
      }

      return task.getTask();
    }
  }

  void consumeActivityAndCompleteTask(
      TaskCompletionSource task, Activity activity, ActivityConsumer consumer) {
    try {
      consumer.consume(activity);
      task.setResult(activity);
    } catch (Throwable t) {
      task.setException(FirebaseAppDistributionException.wrap(t));
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

  void addOnActivityResumedListener(@NonNull OnActivityResumedListener listener) {
    synchronized (lock) {
      this.onActivityResumedListeners.add(listener);
    }
  }

  void removeOnActivityResumedListener(@NonNull OnActivityResumedListener listener) {
    synchronized (lock) {
      this.onActivityResumedListeners.remove(listener);
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
  public void onActivityResumed(@NonNull Activity activity) {
    synchronized (lock) {
      currentActivity = activity;
      for (OnActivityResumedListener listener : onActivityResumedListeners) {
        listener.onResumed(activity);
      }
    }
  }

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
