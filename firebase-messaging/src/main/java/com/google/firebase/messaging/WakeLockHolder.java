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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.stats.WakeLock;
import com.google.errorprone.annotations.RestrictedApi;
import java.util.concurrent.TimeUnit;

/**
 * Helper class to manage wake locks in FCM.
 *
 * <p>This class assumes the caller has checked the wake lock permission prior to using the class.
 *
 * <p>All the {@link #startWakefulService(Context, Intent)} should be accompanied by a {@link
 * #completeWakefulIntent(Intent)} once the service is completed.
 */
final class WakeLockHolder {

  // Extra to identify a wakeful intent
  private static final String EXTRA_WAKEFUL_INTENT =
      "com.google.firebase.iid.WakeLockHolder.wakefulintent";
  /** Release wakelocks after 60s, because we don't expect operations to take longer than that. */
  static final long WAKE_LOCK_ACQUIRE_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(1);
  // Object to sync threads
  private static final Object syncObject = new Object();

  @GuardedBy("WakeLockHolder.syncObject")
  private static WakeLock wakeLock;

  /** Lazily initializes the wake lock if it wasn't initialized before. */
  @GuardedBy("WakeLockHolder.syncObject")
  private static void checkAndInitWakeLock(Context context) {
    if (wakeLock == null) {
      wakeLock =
          new WakeLock(
              context,
              PowerManager.PARTIAL_WAKE_LOCK,
              "wake:com.google.firebase.iid.WakeLockHolder");
      wakeLock.setReferenceCounted(true);
    }
  }

  /**
   * Starts a wakeful service based on the intent provided.
   *
   * @param context Application context.
   * @param intent Intent for starting the service.
   * @return Component name of service which is started.
   */
  static ComponentName startWakefulService(@NonNull Context context, @NonNull Intent intent) {
    synchronized (syncObject) {
      checkAndInitWakeLock(context);

      boolean isWakeLockAlreadyAcquired = isWakefulIntent(intent);

      setAsWakefulIntent(intent, true);

      ComponentName comp = context.startService(intent);
      if (comp == null) {
        return null;
      }

      if (!isWakeLockAlreadyAcquired) {
        wakeLock.acquire(WAKE_LOCK_ACQUIRE_TIMEOUT_MILLIS);
      }

      return comp;
    }
  }

  /**
   * Sends an Intent to a Service, binding to it, if necessary. Acquires a WakeLock based on the
   * Intent and holds until the Service has finished processing the Intent or after a certain amount
   * of time.
   *
   * @param context Application context.
   * @param connection ServiceConnection to send the Intent to.
   * @param intent Intent for starting the service.
   */
  // TODO(b/261013992): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  static void sendWakefulServiceIntent(
      Context context, WithinAppServiceConnection connection, Intent intent) {
    synchronized (syncObject) {
      checkAndInitWakeLock(context);

      boolean isWakeLockAlreadyAcquired = isWakefulIntent(intent);

      setAsWakefulIntent(intent, true);

      if (!isWakeLockAlreadyAcquired) {
        wakeLock.acquire(WAKE_LOCK_ACQUIRE_TIMEOUT_MILLIS);
      }

      connection.sendIntent(intent).addOnCompleteListener(t -> completeWakefulIntent(intent));
    }
  }

  private static void setAsWakefulIntent(@NonNull Intent intent, boolean isWakeful) {
    intent.putExtra(EXTRA_WAKEFUL_INTENT, isWakeful);
  }

  @VisibleForTesting
  static boolean isWakefulIntent(@NonNull Intent intent) {
    return intent.getBooleanExtra(EXTRA_WAKEFUL_INTENT, false);
  }

  /**
   * This Method will release the wake lock if the associated intent is a wakeful intent. This
   * method is supposed to be called after completing the service started by {@link
   * #startWakefulService(Context, Intent)}.
   *
   * <p>Note : Number of calls made to {@link #completeWakefulIntent(Intent)} should be always less
   * than or equals to the number of calls made to {@link #startWakefulService(Context, Intent)},
   * otherwise this method has no effect.
   *
   * <p>Make sure to pass the same intent used to start the service via {@link
   * #startWakefulService(Context, Intent)}.
   *
   * @param intent Intent for starting the service.
   */
  static void completeWakefulIntent(@NonNull Intent intent) {
    synchronized (syncObject) {
      if (wakeLock != null && isWakefulIntent(intent)) {
        setAsWakefulIntent(intent, false);
        wakeLock.release();
      }
    }
  }

  /**
   * Method to acquire the wake lock for provided time period.
   *
   * <p>Note : Only for testing. Do not use in production code.
   *
   * @param millis time in mills
   * @param intent Intent upon which the wakelock needs to be acquired.
   */
  @RestrictedApi(
      explanation = "To be used for testing purpose only",
      link = "",
      allowedOnPath = ".*firebase(-|_)(iid|messaging)/.*")
  static void acquireWakeLock(Intent intent, long millis) {
    synchronized (syncObject) {
      if (wakeLock != null) {
        setAsWakefulIntent(intent, true);
        wakeLock.acquire(millis);
      }
    }
  }

  /**
   * Method to init the wakelock for executing tests.
   *
   * <p>Note : Only for testing. Do not use in production code.
   *
   * @param context Context to init Wake lock if required.
   */
  @RestrictedApi(
      explanation = "To be used for testing purpose only",
      link = "",
      allowedOnPath = ".*firebase(-|_)(iid|messaging)/.*")
  static void initWakeLock(Context context) {
    synchronized (syncObject) {
      checkAndInitWakeLock(context);
    }
  }

  /**
   * This method should be used for unit testing to reset wake lock in between multiple tests.
   *
   * <p>Note : Only for testing. Do not use in production code.
   */
  @RestrictedApi(
      explanation = "To be used for testing purpose only",
      link = "",
      allowedOnPath = ".*firebase(-|_)(iid|messaging)/.*")
  static void reset() {
    synchronized (syncObject) {
      wakeLock = null;
    }
  }
}
