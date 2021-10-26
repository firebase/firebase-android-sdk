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

package com.google.firebase.appdistribution.internal;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.TaskExecutors;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

public class FirebaseAppDistributionLifecycleNotifier
    implements Application.ActivityLifecycleCallbacks {
  private Activity currentActivity;
  private static FirebaseAppDistributionLifecycleNotifier instance;
  private final Object lock = new Object();

  /** A queue of listeners that trigger when the activity is foregrounded */
  @GuardedBy("lock")
  private final Queue<ManagedListener<OnActivityStartedListener>> onActivityStartedListeners =
      new ArrayDeque<>();

  /** A queue of listeners that trigger when the activity is backgrounded */
  @GuardedBy("lock")
  private final Queue<ManagedListener<OnActivityPausedListener>> onActivityPausedListeners =
      new ArrayDeque<>();

  /** A queue of listeners that trigger when the activity is destroyed */
  @GuardedBy("lock")
  private final Queue<ManagedListener<OnActivityDestroyedListener>> onDestroyedListeners =
      new ArrayDeque<>();

  private FirebaseAppDistributionLifecycleNotifier() {}

  public static FirebaseAppDistributionLifecycleNotifier getInstance() {
    if (instance == null) {
      instance = new FirebaseAppDistributionLifecycleNotifier();
    }
    return instance;
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
    return currentActivity;
  }

  public void addOnActivityStartedListener(
      @Nullable Executor executor, @NonNull OnActivityStartedListener listener) {
    ManagedListener managedListener = new ManagedListener(executor, listener);
    synchronized (lock) {
      this.onActivityStartedListeners.add(managedListener);
    }
  }

  public void addOnActivityPausedListener(
      @Nullable Executor executor, @NonNull OnActivityPausedListener listener) {
    ManagedListener managedListener = new ManagedListener(executor, listener);
    synchronized (lock) {
      this.onActivityPausedListeners.add(managedListener);
    }
  }

  public void addOnActivityDestroyedListener(
      @Nullable Executor executor, @NonNull OnActivityDestroyedListener listener) {
    ManagedListener managedListener = new ManagedListener(executor, listener);
    synchronized (lock) {
      this.onDestroyedListeners.add(managedListener);
    }
  }

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {}

  @Override
  public void onActivityStarted(@NonNull Activity activity) {
    currentActivity = activity;
    synchronized (lock) {
      for (ManagedListener<OnActivityStartedListener> managedListener :
          onActivityStartedListeners) {
        managedListener.executor.execute(() -> managedListener.listener.onStarted(activity));
      }
    }
  }

  @Override
  public void onActivityResumed(@NonNull Activity activity) {}

  @Override
  public void onActivityPaused(@NonNull Activity activity) {
    if (this.currentActivity == activity) {
      this.currentActivity = null;
    }
    synchronized (lock) {
      for (ManagedListener<OnActivityPausedListener> listener : onActivityPausedListeners) {
        listener.executor.execute(() -> listener.listener.onPaused(activity));
      }
    }
  }

  @Override
  public void onActivityStopped(@NonNull Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {}

  @Override
  public void onActivityDestroyed(@NonNull Activity activity) {
    if (this.currentActivity == activity) {
      this.currentActivity = null;
    }
    synchronized (lock) {
      for (ManagedListener<OnActivityDestroyedListener> listener : onDestroyedListeners) {
        listener.executor.execute(() -> listener.listener.onDestroyed(activity));
      }
    }
  }

  /** Wraps a listener and its corresponding executor. */
  private static class ManagedListener<T> {
    Executor executor;
    T listener;

    ManagedListener(@Nullable Executor executor, T listener) {
      this.executor = executor != null ? executor : TaskExecutors.MAIN_THREAD;
      this.listener = listener;
    }
  }
}
