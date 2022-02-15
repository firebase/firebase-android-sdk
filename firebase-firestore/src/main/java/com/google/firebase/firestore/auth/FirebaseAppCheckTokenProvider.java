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
import com.google.firebase.appcheck.interop.AppCheckTokenListener;
import com.google.firebase.appcheck.interop.InternalAppCheckTokenProvider;
import com.google.firebase.firestore.util.Executors;
import com.google.firebase.firestore.util.Listener;
import com.google.firebase.firestore.util.Logger;
import com.google.firebase.inject.Deferred;
import com.google.firebase.inject.Provider;
import java.util.Objects;

/** FirebaseAppCheckTokenProvider uses Firebase AppCheck to get an AppCheck token. */
public final class FirebaseAppCheckTokenProvider extends CredentialsProvider<String> {

  private static final String LOG_TAG = "FirebaseAppCheckTokenProvider";

  /** The listener to be notified of AppCheck token changes. */
  @Nullable
  @GuardedBy("this")
  private Listener<String> changeListener;

  /**
   * The {@link Provider} that gives access to the {@link InternalAppCheckTokenProvider} instance.
   */
  @Nullable
  @GuardedBy("this")
  private InternalAppCheckTokenProvider internalAppCheckTokenProvider;

  @GuardedBy("this")
  private boolean forceRefresh;

  @GuardedBy("this")
  private String latestAppCheckToken;

  /**
   * The listener registered with FirebaseApp; used to start/stop receiving AppCheck token changes.
   */
  private final AppCheckTokenListener tokenListener = result -> onTokenChanged(result);

  /** Creates a new FirebaseAppCheckTokenProvider. */
  @SuppressLint("ProviderAssignment") // TODO: Remove this @SuppressLint once b/181014061 is fixed.
  public FirebaseAppCheckTokenProvider(
      Deferred<InternalAppCheckTokenProvider> deferredAppCheckTokenProvider) {
    deferredAppCheckTokenProvider.whenAvailable(
        provider -> {
          synchronized (this) {
            internalAppCheckTokenProvider = provider.get();
            // Get notified when AppCheck token changes to a new value in the future.
            if (internalAppCheckTokenProvider != null) {
              internalAppCheckTokenProvider.addAppCheckTokenListener(tokenListener);
            }
          }
        });
  }

  /** Invoked when the AppCheck token changes. */
  private synchronized void onTokenChanged(@NonNull AppCheckTokenResult result) {
    if (result.getError() != null) {
      Logger.warn(
          LOG_TAG,
          "Error getting App Check token; using placeholder token instead. Error: "
              + result.getError());
    }

    // We should *not* invoke `changeListener` the very first time an App Check token is retrieved.
    // Otherwise, FirestoreClient upon startup sets up a stream and immediately tears it down and
    // re-establishes a new stream.
    //
    // The reason is as follows:
    //
    // (1) Retrieving App Check tokens can be slow, so (unlike Auth) we do not block the
    // FirestoreClient initialization (main thread) waiting for an App Check token. Instead, we take
    // a lazy approach: `getToken` gets called when a stream is about to be set up.
    //
    // (2) Calling `getToken` for the very first time always results in `onTokenChanged` getting
    // invoked (by App Check) on another thread *before* `getToken` has completed. This is because
    // App Check does not invoke the listener until the first time it retrieves a token.
    //
    // (3) FirestoreClient listens to App Check token changes and re-establishes streams when
    // App Check tokens expire (happens when `changeListener` is invoked below).
    if (changeListener != null
        && latestAppCheckToken != null
        && !Objects.equals(latestAppCheckToken, result.getToken())) {
      latestAppCheckToken = result.getToken();
      changeListener.onValue(result.getToken());
    }
  }

  /**
   * Returns a task which will have the AppCheck token upon success, or an exception upon failure.
   */
  @Override
  public synchronized Task<String> getToken() {
    if (internalAppCheckTokenProvider == null) {
      return Tasks.forException(new FirebaseApiNotAvailableException("AppCheck is not available"));
    }

    Task<AppCheckTokenResult> res = internalAppCheckTokenProvider.getToken(forceRefresh);
    forceRefresh = false;

    return res.continueWithTask(
        Executors.DIRECT_EXECUTOR,
        task -> {
          synchronized (this) {
            if (task.isSuccessful()) {
              latestAppCheckToken = task.getResult().getToken();
              return Tasks.forResult(task.getResult().getToken());
            } else {
              return Tasks.forException(task.getException());
            }
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

  /** Remove the listener for AppCheck token changes. */
  @Override
  public synchronized void removeChangeListener() {
    changeListener = null;

    if (internalAppCheckTokenProvider != null) {
      internalAppCheckTokenProvider.removeAppCheckTokenListener(tokenListener);
    }
  }

  /** Registers a listener that will be notified when AppCheck token changes. */
  @Override
  public synchronized void setChangeListener(@NonNull Listener<String> changeListener) {
    this.changeListener = changeListener;
  }
}
