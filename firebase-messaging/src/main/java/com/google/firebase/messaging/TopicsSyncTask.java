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

import static com.google.firebase.messaging.Constants.FCM_WAKE_LOCK;
import static com.google.firebase.messaging.Constants.TAG;
import static com.google.firebase.messaging.Constants.WAKE_LOCK_ACQUIRE_TIMEOUT_MILLIS;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.iid.Metadata;
import java.io.IOException;

/** Background task to perform topic sync operations with the Firebase backend using a bg thread */
class TopicsSyncTask implements Runnable {
  private final Context context;
  private final Metadata metadata;
  private final WakeLock syncWakeLock;
  private final TopicsSubscriber topicsSubscriber;

  private final long nextDelaySeconds;

  private static final Object TOPIC_SYNC_TASK_LOCK = new Object();

  /**
   * Cache of whether this app has the wake lock permission. This saves checking the permission
   * every time which is a moderately expensive call.
   *
   * <p>Initially null, set to true or false on first use.
   */
  @GuardedBy("TOPIC_SYNC_TASK_LOCK")
  private static Boolean hasWakeLockPermission = null;

  @GuardedBy("TOPIC_SYNC_TASK_LOCK")
  private static Boolean hasAccessNetworkStatePermission = null;

  TopicsSyncTask(
      TopicsSubscriber topicsSubscriber,
      Context context,
      Metadata metadata,
      long nextDelaySeconds) {
    this.topicsSubscriber = topicsSubscriber;
    this.context = context;
    this.nextDelaySeconds = nextDelaySeconds;
    this.metadata = metadata;

    PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    this.syncWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, FCM_WAKE_LOCK);
  }

  @Override
  @SuppressLint("Wakelock") // WakeLock will always be released
  @SuppressWarnings("CatchSpecificExceptionsChecker") // errorProne's recommended way to handle
  // already released wakelock.
  public void run() {
    if (hasWakeLockPermission(context)) {
      syncWakeLock.acquire(WAKE_LOCK_ACQUIRE_TIMEOUT_MILLIS);
    }

    try {
      // this should have already been set to true, do it again for safety.
      topicsSubscriber.setSyncScheduledOrRunning(true);

      if (!metadata.isGmscorePresent()) {
        topicsSubscriber.setSyncScheduledOrRunning(false);
        return;
      }

      if (hasAccessNetworkStatePermission(context)) {
        if (!isDeviceConnected()) {
          ConnectivityChangeReceiver receiver = new ConnectivityChangeReceiver(this);
          receiver.registerReceiver();
          return; // return and wait for connectivity
        }
      }

      if (topicsSubscriber.syncTopics()) {
        topicsSubscriber.setSyncScheduledOrRunning(false);
      } else {
        topicsSubscriber.syncWithDelaySecondsInternal(nextDelaySeconds);
      }
    } catch (IOException e) {
      Log.e(TAG, "Failed to sync topics. Won't retry sync. " + e.getMessage());
      topicsSubscriber.setSyncScheduledOrRunning(false);
    } finally {
      if (hasWakeLockPermission(context)) {
        try {
          syncWakeLock.release();
        } catch (RuntimeException unused) {
          // Ignore: already released by timeout.
          Log.i(TAG, "TopicsSyncTask's wakelock was already released due to timeout.");
        }
      }
    }
  }

  private synchronized boolean isDeviceConnected() {
    ConnectivityManager cm =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo networkInfo = (cm != null) ? cm.getActiveNetworkInfo() : null;
    return networkInfo != null && networkInfo.isConnected();
  }

  private static boolean isLoggable() {
    // special workaround for Log.isLoggable being flaky in Android M: b/27572147
    return Log.isLoggable(TAG, Log.DEBUG)
        || (Build.VERSION.SDK_INT == Build.VERSION_CODES.M && Log.isLoggable(TAG, Log.DEBUG));
  }

  private static boolean hasWakeLockPermission(Context context) {
    synchronized (TOPIC_SYNC_TASK_LOCK) {
      hasWakeLockPermission =
          hasWakeLockPermission == null
              ? hasPermission(context, Manifest.permission.WAKE_LOCK, hasWakeLockPermission)
              : hasWakeLockPermission;
      return hasWakeLockPermission;
    }
  }

  private static boolean hasAccessNetworkStatePermission(Context context) {
    synchronized (TOPIC_SYNC_TASK_LOCK) {
      hasAccessNetworkStatePermission =
          hasAccessNetworkStatePermission == null
              ? hasPermission(
                  context,
                  Manifest.permission.ACCESS_NETWORK_STATE,
                  hasAccessNetworkStatePermission)
              : hasAccessNetworkStatePermission;
      return hasAccessNetworkStatePermission;
    }
  }

  private static boolean hasPermission(Context context, String permission, Boolean cachedState) {
    if (cachedState != null) {
      return cachedState;
    }

    boolean granted =
        context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;

    if (!granted && Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, createPermissionMissingLog(permission));
    }

    return granted;
  }

  private static String createPermissionMissingLog(String permission) {
    return "Missing Permission: "
        + permission
        + ". This permission should normally be included by the manifest merger, "
        + "but may needed to be manually added to your manifest";
  }

  @VisibleForTesting
  class ConnectivityChangeReceiver extends BroadcastReceiver {
    @GuardedBy("this")
    @Nullable
    private TopicsSyncTask task; // task is set to null after it has been fired.

    public ConnectivityChangeReceiver(TopicsSyncTask task) {
      this.task = task;
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public synchronized void onReceive(Context context, Intent intent) {
      // checking if task is null is free from race-conditions because reads/writes to the task
      // variable happen only inside #onReceive(), which is always executed on the main thread.
      if (task == null) {
        // Task has already been triggered and receiver unregistered. stop here.
        return;
      }
      if (!task.isDeviceConnected()) {
        // still don't have connectivity, do nothing
        return;
      }
      if (TopicsSyncTask.isLoggable()) {
        Log.d(TAG, "Connectivity changed. Starting background sync.");
      }
      task.topicsSubscriber.scheduleSyncTaskWithDelaySeconds(task, 0);

      context.unregisterReceiver(this);
      task = null;
    }

    public void registerReceiver() {
      if (TopicsSyncTask.isLoggable()) {
        Log.d(TAG, "Connectivity change received registered");
      }
      context.registerReceiver(this, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }
  }
}
