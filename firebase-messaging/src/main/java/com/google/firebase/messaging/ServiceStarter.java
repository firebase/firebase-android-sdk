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

import android.Manifest;
import android.Manifest.permission;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.firebase.iid.FirebaseInstanceIdReceiver;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Manages securely starting the app's FirebaseMessagingService implementation even though they are
 * exported without permissions.
 *
 * <p>The general flow is:
 *
 * <ul>
 *   <li>1. The real intent is added to a queue (one per service).
 *   <li>2. An empty intent is sent to the service.
 *   <li>3. The service polls the real intent off the queue or simply stops itself if the queue is
 *       empty as it cannot trust any intents that it is started with.
 * </ul>
 *
 * <p>There are 3 cases we handle:
 *
 * <ul>
 *   <li>1. Incoming message, comes through FirebaseInstanceIdReceiver which is exported but
 *       protected by a permission. It calls {@link #startMessagingService}, which places the intent
 *       on a queue and starts the service.
 *   <li>2. PendingIntents (e.g. for Notifications click or dismissal). Same flow as 1 via
 *       FirebaseInstanceIdReceiver.
 *   <li>3. Starting the service through some internal event. Here we don't need to go through the
 *       receiver, as the app will be running the whole time.
 * </ul>
 *
 * <p>Note that there is potential flakiness to this approach if the app is killed between receiving
 * the broadcast and the service starting, but there is little chance of this as the app holds a
 * wake lock between the two.
 *
 * <p>This class also caches the service class name and whether the app has permission to take wake
 * locks and holds this small amount of state forever to save doing the somewhat expensive lookups
 * repeatedly.
 *
 * @hide
 */
@KeepForSdk
public class ServiceStarter {

  public static final int SUCCESS = Activity.RESULT_OK;

  @KeepForSdk public static final int ERROR_UNKNOWN = 500;

  static final int ERROR_NOT_FOUND = 404;
  static final int ERROR_SECURITY_EXCEPTION = 401;
  static final int ERROR_ILLEGAL_STATE_EXCEPTION = 402;
  static final int ERROR_ILLEGAL_STATE_EXCEPTION_FALLBACK_TO_BIND = 403;

  // Internal actions for routing intents from this receiver to services defined by API clients
  static final String ACTION_MESSAGING_EVENT = "com.google.firebase.MESSAGING_EVENT";

  private static final String EXTRA_WRAPPED_INTENT = "wrapped_intent";
  private static final String PERMISSIONS_MISSING_HINT =
      "this should normally be included by the manifest merger, "
          + "but may needed to be manually added to your manifest";

  private static ServiceStarter instance;

  /**
   * Cache of FirebaseMessagingService implementation class name so that the expensive name
   * resolution only needs to be done once.
   */
  @GuardedBy("this")
  @Nullable
  private String firebaseMessagingServiceClassName = null;

  /**
   * Cache of whether this app has the wake lock permission. This saves checking the permission
   * every time which is a moderately expensive call.
   *
   * <p>Initially null, set to true or false on first use.
   */
  private Boolean hasWakeLockPermission = null;

  private Boolean hasAccessNetworkStatePermission = null;

  // Not synchronized as only accessed on the main thread
  private final Queue<Intent> messagingEvents = new ArrayDeque<>();

  static synchronized ServiceStarter getInstance() {
    if (instance == null) {
      instance = new ServiceStarter();
    }
    return instance;
  }

  private ServiceStarter() {}

  /** Get the next start Intent for the app's FirebaseMessagingService. */
  @MainThread
  Intent getMessagingEvent() {
    return messagingEvents.poll();
  }

  /** Securely start the service identified by {@param action} with the provided {@param intent}. */
  @MainThread
  public int startMessagingService(Context context, Intent intent) {
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "Starting service");
    }
    // As the services use to be exported and not protected by a permission, place the intent on a
    // queue, then start the service with an empty intent telling it to check the queue.
    messagingEvents.offer(intent);

    Intent serviceIntent = new Intent(ACTION_MESSAGING_EVENT);
    serviceIntent.setPackage(context.getPackageName());
    return doStartService(context, serviceIntent);
  }

  private int doStartService(Context context, Intent intent) {
    // It seems that startService with intent without ClassName can fail due to a framework bug
    // See: http://b/19873307 and http://b/24065801 for GCM
    String className = resolveServiceClassName(context, intent);
    if (className != null) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "Restricting intent to a specific service: " + className);
      }
      intent.setClassName(context.getPackageName(), className);
    }

    try {
      ComponentName service;
      if (hasWakeLockPermission(context)) {
        service = WakeLockHolder.startWakefulService(context, intent);
      } else {
        service = context.startService(intent);
        Log.d(TAG, "Missing wake lock permission, service start may be delayed");
      }
      if (service == null) {
        Log.e(TAG, "Error while delivering the message: ServiceIntent not found.");
        return ERROR_NOT_FOUND;
      }
      return SUCCESS;
    } catch (SecurityException ex) {
      // It seems that startService with intent without ClassName can fail with
      // SecurityException due to a framework bug. See: http://b/19873307 http://b/24065801
      Log.e(TAG, "Error while delivering the message to the serviceIntent", ex);
      return ERROR_SECURITY_EXCEPTION;
    } catch (IllegalStateException e) {
      // We tried to start a service while in the background, avoid crashing the app. This should
      // never happen any more, but keep this catch here just in case.
      Log.e(TAG, "Failed to start service while in background: " + e);
      return ERROR_ILLEGAL_STATE_EXCEPTION;
    }
  }

  /**
   * Try to resolve the classname of the target service and apply it to the intent.
   *
   * <p>We have reports from Chrome of a possible framework bug, where {@link
   * #startMessagingService} is confused, tries to deliver the intent to the wrong app (!) and fails
   * with a SecurityException. In that case the receiver (and possibly the whole app) crashes.
   *
   * <p>For now we don't want to try/catch the SecurityException because it would result in FCM
   * messages silently dropped and difficult to investigate.
   *
   * <p>If you want to avoid the crash and log the error in other ways you can override onReceive()
   * or onStartWakefulService() in your own {@link FirebaseInstanceIdReceiver}.
   *
   * <p>It has been reported that the bug doesn't occur when the service Classname is set. This is a
   * tentative workaround for this (difficult to reproduce) framework bug.
   *
   * <p>See: http://b/19873307 and http://b/24065801 for FCM
   */
  @Nullable
  private synchronized String resolveServiceClassName(Context context, Intent intent) {
    if (firebaseMessagingServiceClassName != null) {
      return firebaseMessagingServiceClassName;
    }
    ResolveInfo resolveInfo = context.getPackageManager().resolveService(intent, 0);
    if (resolveInfo == null || resolveInfo.serviceInfo == null) {
      Log.e(TAG, "Failed to resolve target intent service, skipping classname enforcement");
      return null;
    }

    ServiceInfo serviceInfo = resolveInfo.serviceInfo;
    if (!context.getPackageName().equals(serviceInfo.packageName) || serviceInfo.name == null) {
      Log.e(
          TAG,
          "Error resolving target intent service, skipping classname enforcement. "
              + "Resolved service was: "
              + serviceInfo.packageName
              + "/"
              + serviceInfo.name);
      return null;
    }

    // If service name is "relative" (starting with ".") prepend the package name.
    if (serviceInfo.name.startsWith(".")) {
      firebaseMessagingServiceClassName = context.getPackageName() + serviceInfo.name;
    } else {
      firebaseMessagingServiceClassName = serviceInfo.name;
    }
    return firebaseMessagingServiceClassName;
  }

  boolean hasWakeLockPermission(Context context) {
    if (hasWakeLockPermission == null) {
      hasWakeLockPermission =
          context.checkCallingOrSelfPermission(Manifest.permission.WAKE_LOCK)
              == PackageManager.PERMISSION_GRANTED;
    }

    if (!hasWakeLockPermission) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(
            TAG,
            "Missing Permission: "
                + Manifest.permission.WAKE_LOCK
                + " "
                + PERMISSIONS_MISSING_HINT);
      }
    }
    return hasWakeLockPermission;
  }

  boolean hasAccessNetworkStatePermission(Context context) {
    if (hasAccessNetworkStatePermission == null) {
      hasAccessNetworkStatePermission =
          context.checkCallingOrSelfPermission(permission.ACCESS_NETWORK_STATE)
              == PackageManager.PERMISSION_GRANTED;
    }

    if (!hasWakeLockPermission) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(
            TAG,
            "Missing Permission: "
                + Manifest.permission.ACCESS_NETWORK_STATE
                + " "
                + PERMISSIONS_MISSING_HINT);
      }
    }
    return hasAccessNetworkStatePermission;
  }

  @VisibleForTesting
  public static void setForTesting(ServiceStarter serviceStarter) {
    instance = serviceStarter;
  }
}
