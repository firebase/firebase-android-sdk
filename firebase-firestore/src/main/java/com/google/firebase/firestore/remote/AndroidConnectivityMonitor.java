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
import android.net.NetworkInfo;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Android implementation of NetworkReachabilityMonitor. Parallel implementations exist for N+ and
 * pre-N.
 *
 * <p>Implementation note: Most of the code here was shamelessly stolen from
 * https://github.com/grpc/grpc-java/blob/master/android/src/main/java/io/grpc/android/AndroidChannelBuilder.java
 */
public final class AndroidNetworkReachabilityMonitor implements NetworkReachabilityMonitor {

  private final Context context;
  @Nullable private final ConnectivityManager connectivityManager;
  private final List<NetworkReachabilityCallback> callbacks = new ArrayList<>();

  public AndroidNetworkReachabilityMonitor(Context context) {
    // This notnull restriction could be eliminated... the pre-N method doesn't
    // require a Context, and we could use that even on N+ if necessary.
    hardAssert(context != null, "Context must be non-null");
    this.context = context;

    connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    configureNetworkMonitoring();
  }

  @Override
  public void onNetworkReachabilityChange(NetworkReachabilityCallback callback) {
    callbacks.add(callback);
  }

  private void configureNetworkMonitoring() {
    // Android N added the registerDefaultNetworkCallback API to listen to changes in the device's
    // default network. For earlier Android API levels, use the BroadcastReceiver API.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && connectivityManager != null) {
      DefaultNetworkCallback defaultNetworkCallback = new DefaultNetworkCallback();
      connectivityManager.registerDefaultNetworkCallback(defaultNetworkCallback);
    } else {
      NetworkReceiver networkReceiver = new NetworkReceiver();
      IntentFilter networkIntentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
      context.registerReceiver(networkReceiver, networkIntentFilter);
    }

    // TODO(rsgowman): We should handle unregistering the listener (via
    // ConnectivityManager.unregisterNetworkCallback() or Context.unregisterReciver()). But we
    // don't support tearing down firestore itself, so it would never be called.
  }

  /** Respond to changes in the default network. Only used on API levels 24+. */
  @TargetApi(Build.VERSION_CODES.N)
  private class DefaultNetworkCallback extends ConnectivityManager.NetworkCallback {
    @Override
    public void onAvailable(Network network) {
      for (NetworkReachabilityCallback callback : callbacks) {
        callback.onChange(Reachability.REACHABLE);
      }
    }

    @Override
    public void onLost(Network network) {
      for (NetworkReachabilityCallback callback : callbacks) {
        callback.onChange(Reachability.UNREACHABLE);
      }
    }
  }

  /** Respond to network changes. Only used on API levels < 24. */
  private class NetworkReceiver extends BroadcastReceiver {
    private boolean isConnected = false;

    @Override
    public void onReceive(Context context, Intent intent) {
      ConnectivityManager conn =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo networkInfo = conn.getActiveNetworkInfo();
      boolean wasConnected = isConnected;
      isConnected = networkInfo != null && networkInfo.isConnected();
      if (isConnected && !wasConnected) {
        for (NetworkReachabilityCallback callback : callbacks) {
          callback.onChange(Reachability.REACHABLE);
        }
      } else if (!isConnected && wasConnected) {
        for (NetworkReachabilityCallback callback : callbacks) {
          callback.onChange(Reachability.UNREACHABLE);
        }
      }
    }
  }
}
