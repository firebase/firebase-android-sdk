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
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.android.gms.common.util.PlatformVersion;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Processes incoming FCM broadcasts.
 *
 * @hide
 */
@KeepForSdk
public class FcmBroadcastProcessor {

  private static final String EXTRA_BINARY_DATA = "rawData";

  private static final String EXTRA_BINARY_DATA_BASE_64 = "gcm.rawData64";

  private static final Object lock = new Object();

  // static so that it survives after the receiver has finished
  @GuardedBy("lock")
  private static WithinAppServiceConnection fcmServiceConn;

  private final Context context;
  private final Executor executor;

  public FcmBroadcastProcessor(Context context) {
    this.context = context;
    this.executor = Runnable::run;
  }

  public FcmBroadcastProcessor(Context context, ExecutorService executor) {
    this.context = context;
    this.executor = executor;
  }

  @KeepForSdk
  public Task<Integer> process(Intent intent) {
    // For legacy versions, a base 64 encoded string of binary data may be included
    String binaryData64 = intent.getStringExtra(EXTRA_BINARY_DATA_BASE_64);
    if (binaryData64 != null) {
      intent.putExtra(EXTRA_BINARY_DATA, Base64.decode(binaryData64, 0 /* flags */));
      intent.removeExtra(EXTRA_BINARY_DATA_BASE_64);
    }

    return startMessagingService(context, intent);
  }

  /** Start FCM/FIID service based on action and bind settings. */
  @SuppressLint({"InlinedApi"}) // FLAG_RECEIVER_FOREGROUND only introduced in JB
  public Task<Integer> startMessagingService(Context context, Intent intent) {
    // App is subject to background check if targeting O+ and device is O+
    boolean subjectToBackgroundCheck =
        (PlatformVersion.isAtLeastO()
            && context.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.O);

    // Apps are temporarily whitelisted for high priority messages. Check if this is high priority
    // by seeing if it's a foreground broadcast, and if so start the service instead of binding to
    // avoid blocking the foreground broadcast queue.
    boolean isHighPriority = (intent.getFlags() & Intent.FLAG_RECEIVER_FOREGROUND) != 0;

    if (subjectToBackgroundCheck && !isHighPriority) {
      return bindToMessagingService(context, intent, isHighPriority);
    }

    // If app isn't subject to background check or if message is high priority, use startService
    Task<Integer> startServiceResult =
        Tasks.call(
            executor, () -> ServiceStarter.getInstance().startMessagingService(context, intent));

    return startServiceResult.continueWithTask(
        executor,
        r -> {
          if (!PlatformVersion.isAtLeastO()
              || r.getResult() != ServiceStarter.ERROR_ILLEGAL_STATE_EXCEPTION) {
            return r;
          }

          // On O, if we're not able to start the service fall back to binding. This could happen if
          // the app isn't targeting O, but the user manually applied restrictions, or if the temp
          // whitelist has already expired.
          return bindToMessagingService(context, intent, isHighPriority)
              .continueWith(
                  // ok to use direct executor because we're just immediately returning an int
                  Runnable::run,
                  t -> ServiceStarter.ERROR_ILLEGAL_STATE_EXCEPTION_FALLBACK_TO_BIND);
        });
  }

  private static Task<Integer> bindToMessagingService(
      Context context, Intent intent, boolean isForegroundBroadcast) {
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "Binding to service");
    }

    WithinAppServiceConnection connection =
        getServiceConnection(context, ServiceStarter.ACTION_MESSAGING_EVENT);

    if (isForegroundBroadcast) {
      // Foreground broadcast queue, finish the broadcast immediately
      // (by returning a completed Task) to avoid ANRs.
      if (ServiceStarter.getInstance().hasWakeLockPermission(context)) {
        WakeLockHolder.sendWakefulServiceIntent(context, connection, intent);
      } else {
        connection.sendIntent(intent);
      }
      return Tasks.forResult(ServiceStarter.SUCCESS);
    } else {
      // Background broadcast queue, finish the broadcast after the message has been handled
      // (which times out after 20 seconds to avoid ANRs and to limit how long the app is active).
      return connection
          .sendIntent(intent)
          // ok to use direct executor because we're just immediately returning an int
          .continueWith(Runnable::run, t -> ServiceStarter.SUCCESS);
    }
  }

  /** Connect to a service via bind. This is used to process intents in Android O+ */
  private static WithinAppServiceConnection getServiceConnection(Context context, String action) {
    synchronized (lock) {
      if (fcmServiceConn == null) {
        fcmServiceConn = new WithinAppServiceConnection(context, action);
      }
      return fcmServiceConn;
    }
  }

  /**
   * Resets static state for tests.
   *
   * @hide
   */
  @VisibleForTesting
  public static void reset() {
    synchronized (lock) {
      fcmServiceConn = null;
    }
  }

  /**
   * Sets WithinAppServiceConnection for testing.
   *
   * @hide
   */
  @VisibleForTesting
  public static void setServiceConnection(WithinAppServiceConnection connection) {
    synchronized (lock) {
      fcmServiceConn = connection;
    }
  }
}
