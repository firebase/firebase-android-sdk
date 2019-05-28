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

import android.content.Context;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.util.Logger;

/**
 * Android implementation of the SSL Provider. Attempts to fetch GMSCore's SSL Ciphers if available.
 *
 * Note: `initializeSsl()` starts a separate thread to load GMSCore. This allows the rest of the
 * client initialization to proceed without blocking on the SSL stack.
 */
public class AndroidSslProvider implements SslProvider {
  private static final String LOG_TAG = "AndroidSslProvider";

  private final Context context;

  public AndroidSslProvider(Context context) {
    this.context = context;
  }

  @Override
  public Task<Void> initializeSsl() {
    TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();
    new Thread() {
      public void run() {
        try {
          ProviderInstaller.installIfNeeded(context);
        } catch (GooglePlayServicesNotAvailableException
            | GooglePlayServicesRepairableException e) {
          Logger.warn(LOG_TAG, "Failed to update ssl context: %s", e);
        } finally {
          // Mark the SSL initialization as done, even though we may be using outdated SSL ciphers.
          // GRPC-Java recommends obtaining updated ciphers from GMSCore, but we allow the device
          // to fall back to other SSL ciphers if GMSCore is not available.
         taskCompletionSource.setResult(null);
        }
      }
    }.start();
    return taskCompletionSource.getTask();
  }
}
