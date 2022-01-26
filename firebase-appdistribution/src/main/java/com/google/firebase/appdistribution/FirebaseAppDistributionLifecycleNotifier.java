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
import com.google.android.gms.tasks.Tasks;
import java.util.ArrayDeque;
import java.util.Queue;

class FirebaseAppDistributionLifecycleNotifier implements Application.ActivityLifecycleCallbacks {

  /** A functional interface for a function that takes an activity and returns a value. */
  interface ActivityFunction<T> {
    T apply(Activity activity) throws FirebaseAppDistributionException;
  }

  /** A functional interface for a function that takes an activity and returns a {@link Task}. */
  interface ActivityChainingFunction<T> {
    Task<T> apply(Activity activity) throws FirebaseAppDistributionException;
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

  // /**
  //  * Get a {@link Task} that will succeed with a result of the app's foregrounded {@link Activity},
  //  * when one is available.
  //  *
  //  * <p>The returned task will never fail. It will instead remain pending indefinitely until some
  //  * activity comes to the foreground.
  //  */
  // Task<Activity> getForegroundActivity() {
  //   return getForegroundActivity(activity -> {});
  // }

  // /**
  //  * Get a {@link Task} that results from applying a {@link ActivityFunction} applied to the app's
  //  * foregrounded {@link Activity}, when one is available.
  //  *
  //  * <p>The returned task will fail if the {@link ActivityFunction} throws, or the task it returns
  //  * fails. Otherwise it will never fail, and will wait indefinitely for a foreground activity
  //  * before applying the function.
  //  */
  // <T> Task<T> applyToForegroundActivityChaining(ActivityFunction<Task<T>> function) {
  //   synchronized (lock) {
  //     TaskCompletionSource<T> task = new TaskCompletionSource<>();
  //     if (currentActivity != null) {
  //       chainToActivity(task, currentActivity, function);
  //     } else {
  //       addOnActivityResumedListener(
  //           new OnActivityResumedListener() {
  //             @Override
  //             public void onResumed(Activity activity) {
  //               chainToActivity(task, activity, function);
  //               removeOnActivityResumedListener(this);
  //             }
  //           });
  //     }
  //
  //     return task.getTask();
  //   }
  // }
  //
  // <T> Task<T> chainToActivity(
  //     TaskCompletionSource<T> task, Activity activity, ActivityFunction<Task<T>> function) {
  //   MoreExecutors
  //   try {
  //     function.apply(activity)
  //         .addOnSuccessListener(task::setResult)
  //         .addOnFailureListener(task::setException)
  //         .addOnCanceledListener(task::canc)
  //     continuation.on
  //     task.setResult(function.apply(activity));
  //   } catch (Throwable t) {
  //     task.setException(FirebaseAppDistributionException.wrap(t));
  //   }
  // }
  //
  // /**
  //  * Get a {@link Task} that will succeed with a result of applying an {@link ActivityFunction} to
  //  * the app's foregrounded {@link Activity}, when one is available.
  //  *
  //  * <p>The returned task will fail with a {@link FirebaseAppDistributionException} if the {@link
  //  * ActivityFunction} throws. Otherwise it will never fail, and will wait indefinitely for a
  //  * foreground activity before applying the function.
  //  */
  // <T> Task<T> applyToForegroundActivity(ActivityFunction<T> function) {
  //   synchronized (lock) {
  //     TaskCompletionSource<T> task = new TaskCompletionSource<>();
  //     if (currentActivity != null) {
  //       applyToActivityAndCompleteTask(task, currentActivity, function);
  //     } else {
  //       addOnActivityResumedListener(
  //           new OnActivityResumedListener() {
  //             @Override
  //             public void onResumed(Activity activity) {
  //               applyToActivityAndCompleteTask(task, activity, function);
  //               removeOnActivityResumedListener(this);
  //             }
  //           });
  //     }
  //
  //     return task.getTask();
  //   }
  // }
  //
  // <T> void applyToActivityAndCompleteTask(
  //     TaskCompletionSource<T> task, Activity activity, ActivityFunction<T> function) {
  //   try {
  //     task.setResult(function.apply(activity));
  //   } catch (Throwable t) {
  //     task.setException(FirebaseAppDistributionException.wrap(t));
  //   }
  // }

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
