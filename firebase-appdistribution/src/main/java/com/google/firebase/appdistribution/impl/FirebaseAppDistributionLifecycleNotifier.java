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

package com.google.firebase.appdistribution.impl;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

class FirebaseAppDistributionLifecycleNotifier implements Application.ActivityLifecycleCallbacks {

  /** An {@link Executor} that runs tasks on the current thread. */
  private static final Executor DIRECT_EXECUTOR = Runnable::run;

  /** A functional interface for a function that takes an activity and does something with it. */
  interface ActivityConsumer {
    void consume(Activity activity);
  }

  /** A functional interface for a function that takes an activity and produces a new value. */
  interface ActivityFunction<T> {
    T apply(Activity activity) throws FirebaseAppDistributionException;
  }

  private static FirebaseAppDistributionLifecycleNotifier instance;
  private final Object lock = new Object();

  @GuardedBy("lock")
  private Activity currentActivity;

  /** A queue of listeners that trigger when the activity is foregrounded. */
  @GuardedBy("lock")
  private final Queue<OnActivityCreatedListener> onActivityCreatedListeners = new ArrayDeque<>();

  /** A queue of listeners that trigger when the activity is foregrounded. */
  @GuardedBy("lock")
  private final Queue<OnActivityStartedListener> onActivityStartedListeners = new ArrayDeque<>();

  /** A queue of listeners that trigger when the activity is resumed. */
  @GuardedBy("lock")
  private final Queue<OnActivityResumedListener> onActivityResumedListeners = new ArrayDeque<>();

  /** A queue of listeners that trigger when the activity is backgrounded. */
  @GuardedBy("lock")
  private final Queue<OnActivityPausedListener> onActivityPausedListeners = new ArrayDeque<>();

  /** A queue of listeners that trigger when the activity is destroyed. */
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
   * Apply a function to a foreground activity, when one is available, returning a {@link Task} that
   * will complete immediately after the function is applied.
   *
   * <p>The function will be called immediately once the activity is available. This may be main
   * thread or the calling thread, depending on whether or not there is already a foreground
   * activity available when this method is called.
   */
  <T> Task<T> applyToForegroundActivity(ActivityFunction<T> function) {
    return getForegroundActivity()
        .onSuccessTask(
            // Use direct executor to ensure the consumer is called while Activity is in foreground
            DIRECT_EXECUTOR,
            activity -> {
              try {
                return Tasks.forResult(function.apply(activity));
              } catch (Throwable t) {
                return Tasks.forException(FirebaseAppDistributionExceptions.wrap(t));
              }
            });
  }

  /** A version of {@link #applyToForegroundActivity} that does not produce a value. */
  Task<Void> consumeForegroundActivity(ActivityConsumer consumer) {
    return getForegroundActivity()
        .onSuccessTask(
            // Use direct executor to ensure the consumer is called while Activity is in foreground
            DIRECT_EXECUTOR,
            activity -> {
              try {
                consumer.consume(activity);
                return Tasks.forResult(null);
              } catch (Throwable t) {
                return Tasks.forException(FirebaseAppDistributionExceptions.wrap(t));
              }
            });
  }

  /**
   * Apply a function to a foreground activity, when one is available, returning a {@link Task} that
   * will complete with the result of the Task returned by that function.
   *
   * <p>The continuation function will be called immediately once the activity is available. This
   * may be on the main thread or the calling thread, depending on whether or not there is already a
   * foreground activity available when this method is called.
   */
  <T> Task<T> applyToForegroundActivityTask(SuccessContinuation<Activity, T> continuation) {
    return getForegroundActivity()
        .onSuccessTask(
            // Use direct executor to ensure the consumer is called while Activity is in foreground
            DIRECT_EXECUTOR,
            activity -> {
              try {
                return continuation.then(activity);
              } catch (Throwable t) {
                return Tasks.forException(FirebaseAppDistributionExceptions.wrap(t));
              }
            });
  }

  Task<Activity> getForegroundActivity() {
    synchronized (lock) {
      if (currentActivity != null) {
        return Tasks.forResult(currentActivity);
      }
      TaskCompletionSource<Activity> task = new TaskCompletionSource<>();

      addOnActivityResumedListener(
          new OnActivityResumedListener() {
            @Override
            public void onResumed(Activity activity) {
              task.setResult(activity);
              removeOnActivityResumedListener(this);
            }
          });

      return task.getTask();
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

  void addOnActivityPausedListener(@NonNull OnActivityPausedListener listener) {
    synchronized (lock) {
      this.onActivityPausedListeners.add(listener);
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
