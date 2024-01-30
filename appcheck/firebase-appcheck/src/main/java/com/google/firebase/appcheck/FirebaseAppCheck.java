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

package com.google.firebase.appcheck;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.interop.AppCheckTokenListener;
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider;

public abstract class FirebaseAppCheck implements InteropAppCheckTokenProvider {

  /** Gets the default instance of {@code FirebaseAppCheck}. */
  @NonNull
  public static FirebaseAppCheck getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  /**
   * Gets the instance of {@code FirebaseAppCheck} associated with the given {@link FirebaseApp}
   * instance.
   */
  @NonNull
  public static FirebaseAppCheck getInstance(@NonNull FirebaseApp firebaseApp) {
    return firebaseApp.get(FirebaseAppCheck.class);
  }

  /**
   * Installs the given {@link AppCheckProviderFactory}, overwriting any that were previously
   * associated with this {@code FirebaseAppCheck} instance. Any {@link AppCheckTokenListener}s
   * attached to this {@code FirebaseAppCheck} instance will be transferred from existing factories
   * to the newly installed one.
   *
   * <p>Automatic token refreshing will only occur if the global {@code
   * isDataCollectionDefaultEnabled} flag is set to true. To allow automatic token refreshing for
   * Firebase App Check without changing the {@code isDataCollectionDefaultEnabled} flag for other
   * Firebase SDKs, use {@link #installAppCheckProviderFactory(AppCheckProviderFactory, boolean)}
   * instead or call {@link #setTokenAutoRefreshEnabled(boolean)} after installing the {@code
   * factory}.
   */
  public abstract void installAppCheckProviderFactory(@NonNull AppCheckProviderFactory factory);

  /**
   * Installs the given {@link AppCheckProviderFactory}, overwriting any that were previously
   * associated with this {@code FirebaseAppCheck} instance. Any {@link AppCheckTokenListener}s
   * attached to this {@code FirebaseAppCheck} instance will be transferred from existing factories
   * to the newly installed one.
   *
   * <p>Automatic token refreshing will only occur if the {@code isTokenAutoRefreshEnabled} field is
   * set to true. To use the global {@code isDataCollectionDefaultEnabled} flag for determining
   * automatic token refreshing, call {@link
   * #installAppCheckProviderFactory(AppCheckProviderFactory)} instead.
   */
  @SuppressLint("FirebaseLambdaLast")
  public abstract void installAppCheckProviderFactory(
      @NonNull AppCheckProviderFactory factory, boolean isTokenAutoRefreshEnabled);

  /** Sets the {@code isTokenAutoRefreshEnabled} flag. */
  public abstract void setTokenAutoRefreshEnabled(boolean isTokenAutoRefreshEnabled);

  /**
   * Requests a Firebase App Check token. This method should be used ONLY if you need to authorize
   * requests to a non-Firebase backend. Requests to Firebase backends are authorized automatically
   * if configured.
   *
   * <p>If your non-Firebase backend exposes a sensitive or expensive endpoint that has low traffic
   * volume, consider protecting it with <a
   * href=https://firebase.google.com/docs/app-check/custom-resource-backend#replay-protection>Replay
   * Protection</a>. In this case, use {@link #getLimitedUseAppCheckToken()} instead to obtain a
   * limited-use token.
   */
  @NonNull
  public abstract Task<AppCheckToken> getAppCheckToken(boolean forceRefresh);

  /**
   * Requests a Firebase App Check token. This method should be used ONLY if you need to authorize
   * requests to a non-Firebase backend.
   *
   * <p>Returns limited-use tokens that are intended for use with your non-Firebase backend
   * endpoints that are protected with <a
   * href=https://firebase.google.com/docs/app-check/custom-resource-backend#replay-protection>Replay
   * Protection</a>. This method does not affect the token generation behavior of the {@link
   * #getAppCheckToken(boolean forceRefresh)} method.
   */
  @NonNull
  public abstract Task<AppCheckToken> getLimitedUseAppCheckToken();

  /**
   * Registers an {@link AppCheckListener} to changes in the token state. This method should be used
   * ONLY if you need to authorize requests to a non-Firebase backend. Requests to Firebase backends
   * are authorized automatically if configured.
   */
  public abstract void addAppCheckListener(@NonNull AppCheckListener listener);

  /** Unregisters an {@link AppCheckListener} to changes in the token state. */
  public abstract void removeAppCheckListener(@NonNull AppCheckListener listener);

  public interface AppCheckListener {
    /**
     * This method gets invoked on the UI thread on changes to the token state. Does not trigger on
     * token expiry.
     */
    void onAppCheckTokenChanged(@NonNull AppCheckToken token);
  }
}
