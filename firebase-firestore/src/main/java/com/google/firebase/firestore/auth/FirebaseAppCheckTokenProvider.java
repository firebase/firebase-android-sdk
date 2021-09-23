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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.appcheck.AppCheckTokenResult;
import com.google.firebase.appcheck.interop.InternalAppCheckTokenProvider;
import com.google.firebase.firestore.util.Listener;
import com.google.firebase.firestore.util.Logger;
import com.google.firebase.inject.Provider;

/** FirebaseAppCheckTokenProvider uses Firebase AppCheck to get an AppCheck token. */
public final class FirebaseAppCheckTokenProvider extends AppCheckTokenProvider {

  private static final String LOG_TAG = "FirebaseAppCheckTokenProvider";

  /**
   * The latest token retrieved from AppCheck.
   *
   * <p>Its value is an empty string if AppCheck is not available. Its value is null if AppCheck is
   * available, but the task of retrieving the token has not finished yet.
   */
  @Nullable
  @GuardedBy("this")
  private String appCheckToken;

  /** The listener to be notified of AppCheck token changes. */
  @Nullable
  @GuardedBy("this")
  private Listener<String> changeListener;

  /** Creates a new FirebaseAppCheckTokenProvider. */
  public FirebaseAppCheckTokenProvider(
      Provider<InternalAppCheckTokenProvider> appCheckTokenProvider) {
    InternalAppCheckTokenProvider internalAppCheckTokenProvider = appCheckTokenProvider.get();

    // AppCheck is not available.
    if (internalAppCheckTokenProvider == null) {
      appCheckToken = "";
      // At this point it is guaranteed that changeListener is null, therefore we don't need to
      // notify it.
      return;
    }

    // Get the first AppCheck token asynchronously.
    internalAppCheckTokenProvider
        .getToken(/* forceRefresh */ false)
        .addOnSuccessListener(token -> onTokenChanged(token))
        .addOnFailureListener(e -> onTokenError(e));

    // Get notified when AppCheck token changes to a new value in the future.
    internalAppCheckTokenProvider.addAppCheckTokenListener(result -> onTokenChanged(result));
  }

  /** Invoked when an exception occurs while retrieving the AppCheck token. */
  private void onTokenError(@NonNull Exception exception) {
    Logger.warn(LOG_TAG, "Unexpected error getting App Check token: " + exception);
    synchronized (this) {
      appCheckToken = null;
      if (changeListener != null) {
        changeListener.onValue(null);
      }
    }
  }

  /** Invoked when the AppCheck token changes. */
  private void onTokenChanged(@NonNull AppCheckTokenResult result) {
    if (result.getError() != null) {
      Logger.warn(
          LOG_TAG,
          "Error getting App Check token; using placeholder token instead. Error: "
              + result.getError());
    }
    synchronized (this) {
      appCheckToken = result.getToken();
      if (changeListener != null) {
        changeListener.onValue(appCheckToken);
      }
    }
  }

  /**
   * Returns the latest AppCheck token. This can be null (if the task of retrieving the token has
   * not completed yet), and can be an empty string (if AppCheck is not available).
   */
  @Nullable
  public synchronized String getCurrentAppCheckToken() {
    return appCheckToken;
  }

  /** Registers a listener that will be notified when AppCheck token changes. */
  @Override
  public synchronized void setChangeListener(@NonNull Listener<String> changeListener) {
    this.changeListener = changeListener;
  }
}
