// Copyright 2019 Google LLC
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

package com.google.firebase.firestore.remote;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.util.Consumer;
import com.google.firebase.firestore.util.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Android implementation of ConnectivityMonitor. Parallel implementations exist for N+ and pre-N.
 *
 * <p>Implementation note: Most of the code here was shamelessly stolen from
 * https://github.com/grpc/grpc-java/blob/master/android/src/main/java/io/grpc/android/AndroidChannelBuilder.java
 */
public final class AndroidConnectivityMonitor implements ConnectivityMonitor {

  private static final String LOG_TAG = "AndroidConnectivityMonitor";

  private final Context context;
  @Nullable private final ConnectivityManager connectivityManager;
  @Nullable private Runnable unregisterRunnable;
  private final List<Consumer<NetworkStatus>> callbacks = new ArrayList<>();

  public AndroidConnectivityMonitor(Context context) {
    // This notnull restriction could be eliminated... the pre-N method doesn't
    // require a Context, and we could use that even on N+ if necessary.
    hardAssert(context != null, "Context must be non-null");
    this.context = context;

    connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    configureBackgroundStateListener();
    configureNetworkMonitoring();
  }

  @Override
  public void addCallback(Consumer<NetworkStatus> callback) {
    synchronized (callbacks) {
      callbacks.add(callback);
    }
  }

  @Override
  public void shutdown() {
    if (unregisterRunnable != null) {
      unregisterRunnable.run();
      unregisterRunnable = null;
    }
  }

  private void configureNetworkMonitoring() {
    // Android N added the registerDefaultNetworkCallback API to listen to changes in the device's
    // default network. For earlier Android API levels, use the BroadcastReceiver API.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && connectivityManager != null) {
      final DefaultNetworkCallback defaultNetworkCallback = new DefaultNetworkCallback();
      connectivityManager.registerDefaultNetworkCallback(defaultNetworkCallback);
      unregisterRunnable =
          () -> connectivityManager.unregisterNetworkCallback(defaultNetworkCallback);
    } else {
      NetworkReceiver networkReceiver = new NetworkReceiver();
      @SuppressWarnings("deprecation")
      IntentFilter networkIntentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
      context.registerReceiver(networkReceiver, networkIntentFilter);
      unregisterRunnable = () -> context.unregisterReceiver(networkReceiver);
    }
  }

  private void configureBackgroundStateListener() {
    Application application = (Application) context.getApplicationContext();
    final AtomicBoolean inBackground = new AtomicBoolean();

    // Manually register an ActivityLifecycleCallback. Android's BackgroundDetector only notifies
    // when it is certain that the app transitioned from background to foreground. Instead, we
    // want to be notified whenever there is a slight chance that this transition happened.
    application.registerActivityLifecycleCallbacks(
        new Application.ActivityLifecycleCallbacks() {
          @Override
          public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
            if (inBackground.compareAndSet(true, false)) {
              raiseForegroundNotification();
            }
          }

          @Override
          public void onActivityStarted(@NonNull Activity activity) {
            if (inBackground.compareAndSet(true, false)) {
              raiseForegroundNotification();
            }
          }

          @Override
          public void onActivityResumed(@NonNull Activity activity) {
            if (inBackground.compareAndSet(true, false)) {
              raiseForegroundNotification();
            }
          }

          @Override
          public void onActivityPaused(@NonNull Activity activity) {}

          @Override
          public void onActivityStopped(@NonNull Activity activity) {}

          @Override
          public void onActivitySaveInstanceState(
              @NonNull Activity activity, @NonNull Bundle outState) {}

          @Override
          public void onActivityDestroyed(@NonNull Activity activity) {}
        });

    application.registerComponentCallbacks(
        new ComponentCallbacks2() {
          @Override
          public void onTrimMemory(int level) {
            if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
              inBackground.set(true);
            }
          }

          @Override
          public void onConfigurationChanged(@NonNull Configuration newConfig) {}

          @Override
          public void onLowMemory() {}
        });
  }

  public void raiseForegroundNotification() {
    Logger.debug(LOG_TAG, "App has entered the foreground.");
    if (isConnected()) {
      raiseCallbacks(/* connected= */ true);
    }
  }

  /** Respond to changes in the default network. Only used on API levels 24+. */
  @TargetApi(Build.VERSION_CODES.N)
  private class DefaultNetworkCallback extends ConnectivityManager.NetworkCallback {
    @Override
    public void onAvailable(Network network) {
      raiseCallbacks(/* connected= */ true);
    }

    @Override
    public void onLost(Network network) {
      raiseCallbacks(/* connected= */ false);
    }
  }

  /** Respond to network changes. Only used on API levels < 24. */
  private class NetworkReceiver extends BroadcastReceiver {
    private boolean wasConnected = false;

    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context context, Intent intent) {
      boolean isConnected = isConnected();
      if (isConnected() && !wasConnected) {
        raiseCallbacks(/* connected= */ true);
      } else if (!isConnected && wasConnected) {
        raiseCallbacks(/* connected= */ false);
      }
      wasConnected = isConnected;
    }
  }

  private void raiseCallbacks(boolean connected) {
    synchronized (callbacks) {
      for (Consumer<NetworkStatus> callback : callbacks) {
        callback.accept(connected ? NetworkStatus.REACHABLE : NetworkStatus.UNREACHABLE);
      }
    }
  }

  private boolean isConnected() {
    ConnectivityManager conn =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    android.net.NetworkInfo networkInfo = conn.getActiveNetworkInfo();
    return networkInfo != null && networkInfo.isConnected();
  }
}
