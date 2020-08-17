// Copyright 2020 Google LLC
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

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.iid.WakeLockHolder;
import com.google.firebase.iid.WithinAppServiceBinder;
import java.util.concurrent.ExecutorService;

/**
 * Base class for a special IntentService
 *
 * @hide
 */
@SuppressLint("UnwrappedWakefulBroadcastReceiver") // Not used within GmsCore
public abstract class EnhancedIntentService extends Service {
  private static final String TAG = "EnhancedIntentService";

  // Use a different thread per service instance, so it can be reclaimed once the service is done.
  @VisibleForTesting final ExecutorService executor = FcmExecutors.newIntentHandleExecutor();

  // Binder object returned by the onBind call.
  private Binder binder;

  /** @hide */
  @Override
  public final synchronized IBinder onBind(Intent intent) {
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "Service received bind request");
    }
    if (binder == null) {
      binder = new WithinAppServiceBinder(this::processIntent);
    }
    return binder;
  }

  @MainThread
  private Task<Void> processIntent(Intent intent) {
    // Maybe handle the intent on the main thread
    if (handleIntentOnMainThread(intent)) {
      return Tasks.forResult(null /* Void */);
    }
    // Otherwise execute on a background thread
    TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();
    executor.execute(
        () -> {
          try {
            handleIntent(intent);
          } finally {
            taskCompletionSource.setResult(null /* Void */);
          }
        });
    return taskCompletionSource.getTask();
  }

  // ##### STARTSERVICE STRATEGY

  private final Object lock = new Object();
  private int lastStartId;
  private int runningTasks = 0;

  /** @hide */
  @Override
  public final int onStartCommand(final Intent originalIntent, int flags, final int startId) {
    synchronized (lock) {
      lastStartId = startId;
      runningTasks++;
    }
    final Intent intent = getStartCommandIntent(originalIntent);
    if (intent == null) {
      finishTask(originalIntent);
      return START_NOT_STICKY;
    }

    Task<Void> task = processIntent(intent);
    if (task.isComplete()) {
      // Intent handled on main thread
      finishTask(originalIntent);
      return START_NOT_STICKY;
    }

    // finishTask is quick and thread safe, just do it on the same thread as executed the task
    task.addOnCompleteListener(Runnable::run, unusedTask -> finishTask(originalIntent));

    // Redeliver means that if the service is killed before it has finished processing the
    // intent, the intent will be redelivered.
    return START_REDELIVER_INTENT;
  }

  @Override
  @CallSuper
  public void onDestroy() {
    executor.shutdown();
    super.onDestroy();
  }

  private void finishTask(Intent originalIntent) {
    // Task finished, clear the wakelock for the intent, decrement the number of running tasks,
    // and if there are none left, stop the service with the last start ID.
    if (originalIntent != null) {
      WakeLockHolder.completeWakefulIntent(originalIntent);
    }
    synchronized (lock) {
      runningTasks--;
      if (runningTasks == 0) {
        stopSelfResultHook(lastStartId);
      }
    }
  }

  /** stopSelfResult() is final, but to test it this overrideable method should be used. */
  boolean stopSelfResultHook(int startId) {
    return stopSelfResult(startId);
  }

  // ##### OVERRIDABLE METHODS FOR SUBCLASSES

  /**
   * Override this method to change strategy to retrieve the intent that should be processed.
   *
   * <p>This is used for securely starting services where an empty intent is used to start the
   * service, then it checks an internal queue of intents for the actual intent to handle.
   *
   * @param originalIntent this is the intent sent to trigger a processing task.
   * @return the intent that should be processed
   * @hide
   */
  protected Intent getStartCommandIntent(Intent originalIntent) {
    return originalIntent;
  }

  /**
   * Override this method to handle the intent on the main thread.
   *
   * @return true if the intent has been handled and the service will finish. false if it hasn't
   *     been handled and it should be passed to a background thread.
   * @hide
   */
  public boolean handleIntentOnMainThread(Intent intent) {
    return false;
  }

  /**
   * Override this method to handle the intent on a background thread.
   *
   * <p>This will not be invoked if {@link #handleIntentOnMainThread} was overridden and returned
   * true for the intent.
   *
   * @hide
   */
  public abstract void handleIntent(Intent intent);
}
