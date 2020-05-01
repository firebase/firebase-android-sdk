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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.util.Consumer;
import java.util.ArrayList;
import java.util.List;

/**
 * Android implementation of ConnectivityMonitor. Parallel implementations exist for N+ and pre-N.
 *
 * <p>Implementation note: Most of the code here was shamelessly stolen from
 * https://github.com/grpc/grpc-java/blob/master/android/src/main/java/io/grpc/android/AndroidChannelBuilder.java
 */
public final class AndroidConnectivityMonitor implements ConnectivityMonitor {

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
          new Runnable() {
            @Override
            public void run() {
              connectivityManager.unregisterNetworkCallback(defaultNetworkCallback);
            }
          };
    } else {
      NetworkReceiver networkReceiver = new NetworkReceiver();
      @SuppressWarnings("deprecation")
      IntentFilter networkIntentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
      context.registerReceiver(networkReceiver, networkIntentFilter);
      unregisterRunnable =
          new Runnable() {
            @Override
            public void run() {
              context.unregisterReceiver(networkReceiver);
            }
          };
    }
  }

  /** Respond to changes in the default network. Only used on API levels 24+. */
  @TargetApi(Build.VERSION_CODES.N)
  private class DefaultNetworkCallback extends ConnectivityManager.NetworkCallback {
    @Override
    public void onAvailable(Network network) {
      synchronized (callbacks) {
        for (Consumer<NetworkStatus> callback : callbacks) {
          callback.accept(NetworkStatus.REACHABLE);
        }
      }
    }

    @Override
    public void onLost(Network network) {
      synchronized (callbacks) {
        for (Consumer<NetworkStatus> callback : callbacks) {
          callback.accept(NetworkStatus.UNREACHABLE);
        }
      }
    }
  }

  /** Respond to network changes. Only used on API levels < 24. */
  private class NetworkReceiver extends BroadcastReceiver {
    private boolean isConnected = false;

    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context context, Intent intent) {
      ConnectivityManager conn =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      android.net.NetworkInfo networkInfo = conn.getActiveNetworkInfo();
      boolean wasConnected = isConnected;
      isConnected = networkInfo != null && networkInfo.isConnected();
      if (isConnected && !wasConnected) {
        synchronized (callbacks) {
          for (Consumer<NetworkStatus> callback : callbacks) {
            callback.accept(NetworkStatus.REACHABLE);
          }
        }
      } else if (!isConnected && wasConnected) {
        synchronized (callbacks) {
          for (Consumer<NetworkStatus> callback : callbacks) {
            callback.accept(NetworkStatus.UNREACHABLE);
          }
        }
      }
    }
  }
}
