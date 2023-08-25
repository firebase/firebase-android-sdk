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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.stats.ConnectionTracker;
import com.google.android.gms.common.util.concurrent.NamedThreadFactory;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Helper object to abstract the ServiceConnection lifecycle for binding to services within the same
 * app into a single send(...) method.
 */
class WithinAppServiceConnection implements ServiceConnection {

  static class BindRequest {
    final Intent intent;
    private final TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();

    BindRequest(Intent intent) {
      this.intent = intent;
    }

    void arrangeTimeout(ScheduledExecutorService executor) {
      // Timeout after 20 seconds by finishing the Task. This will finish a background broadcast,
      // which waits for the message to be handled.
      ScheduledFuture<?> timeoutFuture =
          executor.schedule(
              () -> {
                Log.w(
                    TAG,
                    "Service took too long to process intent: "
                        + intent.getAction()
                        + " finishing.");
                finish();
              },
              EnhancedIntentService.MESSAGE_TIMEOUT_S,
              TimeUnit.SECONDS);

      getTask()
          .addOnCompleteListener(
              executor,
              t -> {
                timeoutFuture.cancel(false /* don't interrupt */);
              });
    }

    Task<Void> getTask() {
      return taskCompletionSource.getTask();
    }

    void finish() {
      taskCompletionSource.trySetResult(null);
    }
  }

  private final Context context;
  /** Intent used during service connection to connect to the correct service */
  private final Intent connectionIntent;

  /** Scheduled executor used for timing out requests, not for running client code. */
  private final ScheduledExecutorService scheduledExecutorService;

  /** Queue of intents to be passes to the service once the connection is active */
  private final Queue<BindRequest> intentQueue = new ArrayDeque<>();

  @Nullable private WithinAppServiceBinder binder;

  @GuardedBy("this")
  private boolean connectionInProgress = false;

  // TODO(b/258424124): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  WithinAppServiceConnection(Context context, String action) {
    // Class instances are owned by a static variable in FirebaseInstanceIdReceiver
    // and GcmReceiver so that they survive getting gc'd and reinstantiated, so use a
    // scheduled thread pool executor with core size of 0 so that the no threads will be
    // kept idle.
    this(
        context,
        action,
        new ScheduledThreadPoolExecutor(
            0, new NamedThreadFactory("Firebase-FirebaseInstanceIdServiceConnection")));
  }

  @VisibleForTesting
  WithinAppServiceConnection(
      Context context, String action, ScheduledExecutorService scheduledExecutorService) {
    this.context = context.getApplicationContext();
    this.connectionIntent = new Intent(action).setPackage(this.context.getPackageName());
    this.scheduledExecutorService = scheduledExecutorService;
  }

  @CanIgnoreReturnValue
  synchronized Task<Void> sendIntent(Intent intent) {
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "new intent queued in the bind-strategy delivery");
    }
    BindRequest req = new BindRequest(intent);
    req.arrangeTimeout(scheduledExecutorService);
    intentQueue.add(req);
    flushQueue();
    return req.getTask();
  }

  private synchronized void flushQueue() {
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "flush queue called");
    }
    while (!intentQueue.isEmpty()) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "found intent to be delivered");
      }
      // if we are connected, process the queue with the service.
      if (binder != null && binder.isBinderAlive()) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
          Log.d(TAG, "binder is alive, sending the intent.");
        }
        BindRequest bindRequest = intentQueue.poll();
        binder.send(bindRequest);
      } else {
        startConnectionIfNeeded();
        return;
      }
    }
  }

  @GuardedBy("this")
  private void startConnectionIfNeeded() {
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "binder is dead. start connection? " + !connectionInProgress);
    }
    if (connectionInProgress) {
      return;
    }
    // If the connection is not in progress, try to start it
    connectionInProgress = true;
    try {
      if (ConnectionTracker.getInstance()
          .bindService(
              context, connectionIntent, this, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT)) {
        // conn. will succeed. return and wait for onServiceConnected()
        return;
      }
      Log.e(TAG, "binding to the service failed");
    } catch (SecurityException e) {
      // fall through
      Log.e(TAG, "Exception while binding the service", e);
    }
    // Connection failed in a bad way: security exception or bindService
    // returned false. We will probably never be able to bind. Clear the queue.
    connectionInProgress = false;
    finishAllInQueue();
  }

  @GuardedBy("this")
  private void finishAllInQueue() {
    while (!intentQueue.isEmpty()) {
      intentQueue.poll().finish();
    }
  }

  @Override
  public synchronized void onServiceConnected(ComponentName componentName, IBinder iBinder) {
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "onServiceConnected: " + componentName);
    }
    connectionInProgress = false;

    if (!(iBinder instanceof WithinAppServiceBinder)) {
      Log.e(TAG, "Invalid service connection: " + iBinder);
      finishAllInQueue();
      return;
    }

    binder = (WithinAppServiceBinder) iBinder;
    flushQueue();
  }

  @Override
  public void onServiceDisconnected(ComponentName componentName) {
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "onServiceDisconnected: " + componentName);
    }
    flushQueue(); // call flushQueue() to force a new reconnect if the queue is not empty.
  }
}
