// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.auth;

import android.annotation.SuppressLint;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApiNotAvailableException;
import com.google.firebase.appcheck.AppCheckTokenResult;
import com.google.firebase.appcheck.interop.InternalAppCheckTokenProvider;
import com.google.firebase.firestore.util.Executors;
import com.google.firebase.firestore.util.Listener;
import com.google.firebase.firestore.util.Logger;
import com.google.firebase.inject.Provider;

/** FirebaseAppCheckTokenProvider uses Firebase AppCheck to get an AppCheck token. */
public final class FirebaseAppCheckTokenProvider extends CredentialsProvider<String> {

  private static final String LOG_TAG = "FirebaseAppCheckTokenProvider";

  /**
   * The {@link Provider} that gives access to the {@link InternalAppCheckTokenProvider} instance.
   */
  @Nullable
  @GuardedBy("this")
  private InternalAppCheckTokenProvider internalAppCheckTokenProvider;

  @GuardedBy("this")
  private boolean forceRefresh;

  /** Creates a new FirebaseAppCheckTokenProvider. */
  @SuppressLint("ProviderAssignment") // TODO: Remove this @SuppressLint once b/181014061 is fixed.
  public FirebaseAppCheckTokenProvider(
      Provider<InternalAppCheckTokenProvider> appCheckTokenProvider) {
    internalAppCheckTokenProvider = appCheckTokenProvider.get();
  }

  /**
   * Returns a task which will have the AppCheck token upon success, or an exception upon failure.
   */
  @Override
  public synchronized Task<String> getToken() {
    if (internalAppCheckTokenProvider == null) {
      Logger.debug(LOG_TAG, "Firebase AppCheck API not available.");
      return Tasks.forException(new FirebaseApiNotAvailableException("AppCheck is not available"));
    }

    Task<AppCheckTokenResult> res = internalAppCheckTokenProvider.getToken(forceRefresh);
    forceRefresh = false;

    return res.continueWithTask(
        Executors.DIRECT_EXECUTOR,
        task -> {
          if (task.isSuccessful()) {
            Logger.debug(LOG_TAG, "Successfully fetched AppCheck token.");
            return Tasks.forResult(task.getResult().getToken());
          } else {
            Logger.warn(LOG_TAG, "Failed to get AppCheck token: %s.", task.getException());
            return Tasks.forException(task.getException());
          }
        });
  }

  /**
   * Informs FirebaseAppCheckTokenProvider that the current AppCheck token has been invalidated. As
   * a result, the next call to `getToken` will use a `forceRefresh` flag.
   */
  @Override
  public synchronized void invalidateToken() {
    forceRefresh = true;
  }

  @Override
  public synchronized void removeChangeListener() {
    throw new RuntimeException("API Not Supported");
  }

  @Override
  public synchronized void setChangeListener(@NonNull Listener<String> changeListener) {
    throw new RuntimeException("API Not Supported");
  }
}
