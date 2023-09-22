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

import static com.google.firebase.messaging.FirebaseMessaging.TAG;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.util.concurrent.NamedThreadFactory;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Background task to perform sync operations with the Firebase backend using a bg thread */
class SyncTask implements Runnable {

  private final long nextDelaySeconds;
  private final WakeLock syncWakeLock;

  private final FirebaseMessaging firebaseMessaging;

  @VisibleForTesting
  // TODO(b/258424124): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  ExecutorService processorExecutor =
      new ThreadPoolExecutor(
          /* corePoolSize= */ 0,
          /* maximumPoolSize= */ 1,
          /* keepAliveTime= */ 30,
          TimeUnit.SECONDS,
          /* workQueue= */ new LinkedBlockingQueue<>(),
          /* threadFactory= */ new NamedThreadFactory(/* name= */ "firebase-iid-executor"));

  @VisibleForTesting
  @SuppressLint("InvalidWakeLockTag") // Legacy name.
  public SyncTask(FirebaseMessaging firebaseMessaging, long nextDelaySeconds) {
    this.firebaseMessaging = firebaseMessaging;
    this.nextDelaySeconds = nextDelaySeconds;
    PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
    this.syncWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fiid-sync");

    syncWakeLock.setReferenceCounted(false);
  }

  @Override
  @SuppressLint("WakelockTimeout") // WakeLock will always be released
  public void run() {
    if (ServiceStarter.getInstance().hasWakeLockPermission(getContext())) {
      syncWakeLock.acquire();
    }

    try {
      // this should have already been set to true, do it again for safety.
      firebaseMessaging.setSyncScheduledOrRunning(true);

      if (!firebaseMessaging.isGmsCorePresent()) {
        firebaseMessaging.setSyncScheduledOrRunning(false);
        return;
      }

      if (ServiceStarter.getInstance().hasAccessNetworkStatePermission(getContext())) {
        if (!isDeviceConnected()) {
          ConnectivityChangeReceiver receiver = new ConnectivityChangeReceiver(this);
          receiver.registerReceiver();
          return; // return and wait for connectivity
        }
      }

      if (maybeRefreshToken()) {
        firebaseMessaging.setSyncScheduledOrRunning(false);
      } else {
        firebaseMessaging.syncWithDelaySecondsInternal(nextDelaySeconds);
      }
    } catch (IOException e) {
      Log.e(
          TAG,
          "Topic sync or token retrieval failed on hard failure exceptions: "
              + e.getMessage()
              + ". Won't retry the operation.");
      firebaseMessaging.setSyncScheduledOrRunning(false);
    } finally {
      if (ServiceStarter.getInstance().hasWakeLockPermission(getContext())) {
        syncWakeLock.release();
      }
    }
  }

  /**
   * Refreshes the token if needed
   *
   * @return {@code true} if successful, {@code false} if needs to be rescheduled.
   * @throws IOException on a hard failure that should not be retried. Hard failures are failures
   *     except {@link GmsRpc#ERROR_SERVICE_NOT_AVAILABLE} and {@link
   *     GmsRpc#ERROR_INTERNAL_SERVER_ERROR}
   */
  @VisibleForTesting
  boolean maybeRefreshToken() throws IOException {
    try {
      String newToken = firebaseMessaging.blockingGetToken();
      if (newToken == null) {
        Log.e(TAG, "Token retrieval failed: null");
        return false;
      }
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "Token successfully retrieved");
      }
      return true;
    } catch (IOException e) {
      // Retry failed registration requests only if errors from backend are "soft failures"
      if (GmsRpc.isErrorMessageForRetryableError(e.getMessage())) {
        Log.w(TAG, "Token retrieval failed: " + e.getMessage() + ". Will retry token retrieval");
        return false;
      } else if (e.getMessage() == null) {
        Log.w(TAG, "Token retrieval failed without exception message. Will retry token retrieval");
        return false;
      } else {
        throw e; // will not retry. Rethrow to #run for logging
      }
    } catch (SecurityException e) {
      Log.w(TAG, "Token retrieval failed with SecurityException. Will retry token retrieval");
      return false;
    }
  }

  Context getContext() {
    return firebaseMessaging.getApplicationContext();
  }

  boolean isDeviceConnected() {
    ConnectivityManager cm =
        (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo networkInfo = (cm != null) ? cm.getActiveNetworkInfo() : null;
    return networkInfo != null && networkInfo.isConnected();
  }

  @VisibleForTesting
  static class ConnectivityChangeReceiver extends BroadcastReceiver {

    @Nullable private SyncTask task; // task is set to null after it has been fired.

    public ConnectivityChangeReceiver(SyncTask task) {
      this.task = task;
    }

    public void registerReceiver() {
      if (isDebugLogEnabled()) {
        Log.d(TAG, "Connectivity change received registered");
      }
      IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
      task.getContext().registerReceiver(this, intentFilter);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void onReceive(Context context, Intent intent) {
      // checking if task is null is free from race-conditions because reads/writes to the task
      // variable happen only inside #onReceive(), which is always executed on the main thread.
      if (task == null) {
        // Task has already been triggered and recevier unregistered. stop here.
        return;
      }
      if (!task.isDeviceConnected()) {
        // still don't have connectivity, do nothing
        return;
      }
      if (isDebugLogEnabled()) {
        Log.d(TAG, "Connectivity changed. Starting background sync.");
      }
      task.firebaseMessaging.enqueueTaskWithDelaySeconds(task, 0);
      task.getContext().unregisterReceiver(this);
      task = null;
    }
  }

  static boolean isDebugLogEnabled() {
    // special workaround for Log.isLoggable being flaky in Android M: b/27572147
    return Log.isLoggable(TAG, Log.DEBUG)
        || (Build.VERSION.SDK_INT == Build.VERSION_CODES.M && Log.isLoggable(TAG, Log.DEBUG));
  }
}
