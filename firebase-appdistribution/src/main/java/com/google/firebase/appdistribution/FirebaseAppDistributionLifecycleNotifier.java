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
import android.os.Looper;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.TaskExecutors;
import com.google.android.gms.tasks.Tasks;
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
   * Apply a function to a foreground activity, when one is available, returning a {@link Task} that
   * will complete immediately after the function is applied.
   *
   * <p>The consumer function will always be called on the main thread.
   */
  Task<Void> applyToForegroundActivity(ActivityConsumer consumer) {
    return applyToForegroundActivityTask(
        activity -> {
          consumer.consume(activity);
          return Tasks.forResult(null);
        });
  }

  /**
   * Apply a function to a foreground activity, when one is available, returning a {@link Task} that
   * will complete with the result of the Task returned by that function.
   *
   * <p>The continuation function will always be called on the main thread.
   */
  <T> Task<T> applyToForegroundActivityTask(SuccessContinuation<Activity, T> continuation) {
    synchronized (lock) {
      return getForegroundActivity()
          .onSuccessTask(
              getForegroundActivityCallbackExecutor(),
              activity -> {
                try {
                  return continuation.then(activity);
                } catch (Throwable t) {
                  return Tasks.forException(FirebaseAppDistributionException.wrap(t));
                }
              });
    }
  }

  private Executor getForegroundActivityCallbackExecutor() {
    synchronized (lock) {
      // We can run the callback immediately if:
      //   1. There is no current activity (since the callback will already be run in the main
      //      thread when the activity comes into the foreground) OR
      //   2. If there is a current activity and we are already on the main thread
      //
      // Otherwise, we want to queue the callback to run on the main thread.
      return currentActivity == null || Looper.myLooper() == Looper.getMainLooper()
          ? DIRECT_EXECUTOR
          : TaskExecutors.MAIN_THREAD;
    }
  }

  private Task<Activity> getForegroundActivity() {
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
