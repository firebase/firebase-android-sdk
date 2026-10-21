// Copyright 2026 Google LLC
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

import static java.util.concurrent.TimeUnit.SECONDS;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 * Holds the binding to the developer's {@link FirebaseMessagingService}.
 *
 * <p>The handover is a direct object reference through {@link
 * FirebaseMessagingService.LocalBinder}, so both components must share a process. The binding is
 * established on the first message and reused after that.
 *
 * <p>{@link #getService()} returns a {@link Task} rather than blocking: the calling thread comes
 * from the cloud messaging client library's two thread pool, which carries every message the app
 * receives, and Google Play services holds a wake lock until each message is answered.
 *
 * <p>Thread safe. Messages arrive on those worker threads and {@link ServiceConnection} callbacks
 * on the main thread, so all state below is guarded by {@code lock}.
 */
final class MessagingServiceConnector {

  private static final String TAG = "FcmServiceConnector";

  /** How long a message waits for {@code bindService()} to produce a usable service. */
  private static final long BIND_TIMEOUT_S = 5;

  /**
   * Service that owns this connector. It is also the context used to bind, so that the binding is
   * released when this service goes away rather than living as long as the application.
   */
  private final Service owner;

  /**
   * Schedules give-up timers. Static because a {@link
   * java.util.concurrent.ScheduledThreadPoolExecutor} keeps its worker alive once used, so a
   * per-instance one would leave a thread behind every time the owning service is destroyed.
   */
  private static final ScheduledExecutorService scheduledExecutor =
      FcmExecutors.newServiceConnectionExecutor();

  private final Object lock = new Object();

  @GuardedBy("lock")
  @Nullable
  private ServiceConnection cachedConnection;

  @GuardedBy("lock")
  @Nullable
  private FirebaseMessagingService cachedService;

  /** The bind currently in flight. Null when there is none. */
  @GuardedBy("lock")
  @Nullable
  private TaskCompletionSource<FirebaseMessagingService> pendingConnection;

  /** Whether the developer's service is declared in the owner's process. Null until looked up. */
  @GuardedBy("lock")
  @Nullable
  private Boolean sameProcess;

  /**
   * A connector is constructed before the framework attaches a context to its owner, so nothing is
   * derived from the owner until a message is handled.
   */
  MessagingServiceConnector(Service owner) {
    this.owner = owner;
  }

  /**
   * Returns the developer's {@link FirebaseMessagingService}, binding first if needed. Concurrent
   * callers share a single bind rather than one per message.
   *
   * @return a task that fails when the service cannot be resolved, is declared in another process,
   *     {@code bindService()} fails, or the connection is not ready within {@link #BIND_TIMEOUT_S}
   *     seconds. Callers report an error to Google Play services in all of those cases.
   */
  Task<FirebaseMessagingService> getService() {
    TaskCompletionSource<FirebaseMessagingService> source;
    Intent bindIntent;
    ServiceConnection conn;
    synchronized (lock) {
      Task<FirebaseMessagingService> existing = existingConnection();
      if (existing != null) {
        return existing;
      }
      try {
        bindIntent = createBindIntent();
      } catch (IllegalStateException e) {
        return Tasks.forException(e);
      }

      source = new TaskCompletionSource<>();
      conn = new MessagingServiceConnection();

      // Publish the connection before binding: onServiceConnected() may be delivered
      // synchronously from within bindService(), and it ignores callbacks that do not match the
      // currently active connection.
      pendingConnection = source;
      cachedConnection = conn;
    }

    // bindService() is a call into the system server and is made without the lock, so that the
    // main thread, which delivers every ServiceConnection callback as well as the owner's
    // onUnbind(), is never parked behind it.
    if (!tryBind(bindIntent, conn)) {
      // Even when bindService() fails the connection may have been registered, so it still has to
      // be released. teardown() unbinds, which calls into the system server, and it also fails the
      // task.
      teardown(conn);
      return source.getTask();
    }

    boolean abandoned;
    synchronized (lock) {
      abandoned = cachedConnection != conn;
    }
    if (abandoned) {
      // Binding outside the lock leaves a window where the connection is published but not yet
      // registered. A teardown that runs in that window finds nothing to release and clears the
      // fields, so this connection has to be released here or nothing ever will. Whatever was
      // waiting on it has already been failed by that teardown.
      unbind(conn);
      return source.getTask();
    }

    arrangeTimeout(source);
    return source.getTask();
  }

  /** Returns the connected service or the in-flight bind, or null when the caller has to bind. */
  @GuardedBy("lock")
  @Nullable
  private Task<FirebaseMessagingService> existingConnection() {
    if (cachedService != null) {
      return Tasks.forResult(cachedService);
    }
    if (pendingConnection != null) {
      // A bind is already in flight. Wait for it instead of issuing a duplicate bindService(),
      // which would register a second connection record that we could never release.
      return pendingConnection.getTask();
    }
    return null;
  }

  /**
   * Returns the intent that binds to the developer's service.
   *
   * @throws IllegalStateException when the service cannot be resolved or runs in another process.
   *     The caller hands that exception to the waiting message.
   */
  @GuardedBy("lock")
  private Intent createBindIntent() {
    Intent intent = new Intent(ServiceStarter.ACTION_MESSAGING_EVENT);
    intent.setPackage(owner.getPackageName());
    String className = ServiceStarter.getInstance().resolveServiceClassName(owner, intent);
    if (className == null) {
      throw new IllegalStateException("Could not resolve FirebaseMessagingService class name");
    }
    if (!runsInSameProcess(className)) {
      // runsInSameProcess() explains the mismatch the first time it is evaluated. Every later
      // message reuses that cached answer, and the caller already logs the delivery failure.
      throw new IllegalStateException("FirebaseMessagingService runs in another process");
    }
    Intent bindIntent = new Intent(FirebaseMessagingService.ACTION_BIND_LISTENER);
    bindIntent.setClassName(owner, className);
    return bindIntent;
  }

  /**
   * Binds {@code conn} to the developer's service. Must be called without holding {@code lock}.
   *
   * @return whether the bind was accepted. A rejected bind may still have registered the
   *     connection, so the caller has to release it either way.
   */
  private boolean tryBind(Intent bindIntent, ServiceConnection conn) {
    try {
      boolean bound = owner.bindService(bindIntent, conn, Context.BIND_AUTO_CREATE);
      if (!bound) {
        Log.e(TAG, "bindService failed");
      }
      return bound;
    } catch (SecurityException e) {
      // bindService() throws instead of returning false when this app is not allowed to reach the
      // service, so the failure has to be handled here as well.
      Log.e(TAG, "Not permitted to bind to FirebaseMessagingService", e);
      return false;
    }
  }

  /**
   * Gives up after {@link #BIND_TIMEOUT_S}, so the message is answered rather than left until
   * Google Play services' own deadline expires.
   */
  private void arrangeTimeout(TaskCompletionSource<FirebaseMessagingService> source) {
    ScheduledFuture<?> timeoutFuture =
        scheduledExecutor.schedule(() -> abandon(source), BIND_TIMEOUT_S, SECONDS);
    // Cancelling only saves the timer from waking up, it is not what makes a connection that
    // arrives in time safe: abandon() leaves a bind that has already finished alone. Running the
    // listener inline is safe because it does nothing but cancel.
    source.getTask().addOnCompleteListener(Runnable::run, task -> timeoutFuture.cancel(false));
  }

  /**
   * Drops the bind that {@code source} was waiting on, unless it is no longer the one in flight, so
   * a late timer cannot take down a newer attempt.
   */
  private void abandon(TaskCompletionSource<FirebaseMessagingService> source) {
    ServiceConnection connectionToUnbind;
    synchronized (lock) {
      if (pendingConnection != source) {
        return;
      }
      connectionToUnbind = cachedConnection;
      cachedConnection = null;
      cachedService = null;
      pendingConnection = null;
    }
    unbind(connectionToUnbind);
    Log.e(TAG, "Timeout waiting for service connection");
    source.trySetException(
        new IllegalStateException("Timed out connecting to FirebaseMessagingService"));
  }

  /** Releases the binding. Safe to call more than once and when nothing is bound. */
  void shutdown() {
    teardown(null);
  }

  /**
   * Receives the outcome of the {@code bindService()} call made by {@link #getService()}.
   *
   * <p>Delivered on the main thread, so shared state is only touched under {@code lock}. The
   * platform can still deliver a queued callback after this connection has been torn down, which is
   * why each one checks {@code cachedConnection} first.
   */
  private final class MessagingServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
      TaskCompletionSource<FirebaseMessagingService> toComplete;
      FirebaseMessagingService connected = null;
      synchronized (lock) {
        if (cachedConnection != this) {
          // Stale callback for a connection that has already been torn down.
          return;
        }
        if (binder instanceof FirebaseMessagingService.LocalBinder) {
          connected = ((FirebaseMessagingService.LocalBinder) binder).getService();
          cachedService = connected;
        }
        toComplete = pendingConnection;
        pendingConnection = null;
      }

      if (connected != null) {
        // Completing outside the lock keeps developer code, which the waiting caller runs as soon
        // as this resolves, from starting while the monitor is held.
        if (toComplete != null) {
          toComplete.trySetResult(connected);
        }
        return;
      }

      // The developer's service must run in this process for the local handover to work. Give the
      // binding straight back rather than keeping a connection that can never deliver a message,
      // and report the failure so that Google Play services falls back to the broadcast path.
      Log.e(
          TAG,
          "Unexpected binder from FirebaseMessagingService. It must run in the same process as"
              + " FirebaseMessagingListenerService to receive messages through this path.");
      teardown(this);
      if (toComplete != null) {
        toComplete.trySetException(
            new IllegalStateException("FirebaseMessagingService is not in this process"));
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      // Reachable even for a same-process service: the platform reports a disconnect when it
      // brings the service down, for instance after the component is disabled at runtime. The
      // connection record stays registered, so release it and let the next message bind again.
      teardown(this);
    }

    @Override
    public void onBindingDied(ComponentName name) {
      teardown(this);
    }

    @Override
    public void onNullBinding(ComponentName name) {
      // The platform delivers this instead of onServiceConnected() when the service returns a null
      // binder, which happens when the app's messaging event service is not a
      // FirebaseMessagingService. Nothing else would report the outcome, so the message would wait
      // for the bind timeout before being answered.
      Log.e(TAG, "FirebaseMessagingService returned a null binder");
      teardown(this);
    }
  }

  /**
   * Returns whether the developer's service is declared in the owner's process. Binding across
   * processes would succeed but hand back a proxy that cannot be unwrapped, so it is rejected up
   * front. The merged manifest cannot change at runtime, so the answer is cached.
   */
  @GuardedBy("lock")
  private boolean runsInSameProcess(String className) {
    if (sameProcess == null) {
      sameProcess = resolveSameProcess(className);
    }
    return sameProcess;
  }

  /**
   * Compares the declared processes and logs a mismatch. Only the first message reaches this
   * method, so a misconfigured app reports the problem once.
   *
   * <p>Returns true when the processes cannot be compared, so an unexpected package manager result
   * does not stop delivery. The binder check in {@code onServiceConnected} still rejects a
   * cross-process binder if that happens.
   */
  @GuardedBy("lock")
  private boolean resolveSameProcess(String className) {
    PackageManager packageManager = owner.getPackageManager();
    try {
      String targetProcess =
          declaredProcessName(
              packageManager.getServiceInfo(new ComponentName(owner, className), 0));
      String ownProcess =
          declaredProcessName(
              packageManager.getServiceInfo(new ComponentName(owner, owner.getClass()), 0));
      if (!targetProcess.equals(ownProcess)) {
        String ownName = owner.getClass().getName();
        Log.e(
            TAG,
            "Not binding to "
                + className
                + " because it is declared to run in process "
                + targetProcess
                + " while "
                + ownName
                + " runs in "
                + ownProcess
                + ". Declare "
                + ownName
                + " with the same android:process value to receive messages.");
        return false;
      }
      return true;
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, "Couldn't read the declared process names", e);
      return true;
    }
  }

  /**
   * Returns the process {@code serviceInfo} is declared to run in, applying the same fallbacks the
   * package manager uses so that a missing value resolves to the process the component would
   * actually run in rather than being treated as unknown.
   */
  private String declaredProcessName(ServiceInfo serviceInfo) {
    if (serviceInfo.processName != null) {
      return serviceInfo.processName;
    }
    String applicationProcess = owner.getApplicationInfo().processName;
    return applicationProcess != null ? applicationProcess : owner.getPackageName();
  }

  /**
   * Releases the current binding, clears the cached state and fails anything waiting on it.
   *
   * @param expected when non-null, the teardown is skipped unless this is still the active
   *     connection. Pass null to tear down unconditionally.
   */
  private void teardown(@Nullable ServiceConnection expected) {
    ServiceConnection connectionToUnbind;
    TaskCompletionSource<FirebaseMessagingService> pendingToFail;
    synchronized (lock) {
      if (expected != null && cachedConnection != expected) {
        return;
      }
      connectionToUnbind = cachedConnection;
      pendingToFail = pendingConnection;
      cachedConnection = null;
      cachedService = null;
      pendingConnection = null;
    }
    unbind(connectionToUnbind);
    // Fail any message waiting on this bind immediately rather than letting it sit until the
    // timeout fires.
    if (pendingToFail != null) {
      pendingToFail.trySetException(
          new IllegalStateException("Connection to FirebaseMessagingService was released"));
    }
  }

  /** Releases a binding already dropped from the fields above. Must not hold {@code lock}. */
  private void unbind(@Nullable ServiceConnection connectionToUnbind) {
    // unbindService() is a synchronous call into the system server, so it is made outside the
    // lock to avoid holding the monitor across an IPC.
    if (connectionToUnbind != null) {
      try {
        owner.unbindService(connectionToUnbind);
      } catch (IllegalArgumentException e) {
        Log.w(TAG, "Service connection was not registered", e);
      }
    }
  }

  /** Drops the binding and the cached process comparison so that a test can start over. */
  @VisibleForTesting
  void resetForTesting() {
    teardown(null);
    synchronized (lock) {
      sameProcess = null;
    }
  }
}
