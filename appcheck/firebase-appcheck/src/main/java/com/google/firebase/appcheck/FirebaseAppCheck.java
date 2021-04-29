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
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.interop.AppCheckTokenListener;
import com.google.firebase.appcheck.interop.InternalAppCheckTokenProvider;

public abstract class FirebaseAppCheck implements InternalAppCheckTokenProvider {

  /** Get the default instance of FirebaseAppCheck. */
  @NonNull
  public static FirebaseAppCheck getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  /**
   * Get the instance of FirebaseAppCheck associated with the given {@link FirebaseApp} instance.
   */
  @NonNull
  public static FirebaseAppCheck getInstance(@NonNull FirebaseApp firebaseApp) {
    return firebaseApp.get(FirebaseAppCheck.class);
  }

  /**
   * Installs the given {@link AppCheckProviderFactory}, overwriting any that were previously
   * associated with this FirebaseAppCheck instance. Any {@link AppCheckTokenListener}s attached to
   * this FirebaseAppCheck instance will be transferred from existing factories to the newly
   * installed one.
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
   * associated with this FirebaseAppCheck instance. Any {@link AppCheckTokenListener}s attached to
   * this FirebaseAppCheck instance will be transferred from existing factories to the newly
   * installed one.
   *
   * <p>Automatic token refreshing will only occur if the {@code isTokenAutoRefreshEnabled} field is
   * set to true. To use the global {@code isDataCollectionDefaultEnabled} flag for determining
   * automatic token refreshing, {@link #installAppCheckProviderFactory(AppCheckProviderFactory)}
   * should be called instead.
   */
  @SuppressLint("FirebaseLambdaLast")
  public abstract void installAppCheckProviderFactory(
      @NonNull AppCheckProviderFactory factory, boolean isTokenAutoRefreshEnabled);

  /** Sets the {@code isTokenAutoRefreshEnabled} flag. */
  public abstract void setTokenAutoRefreshEnabled(boolean isTokenAutoRefreshEnabled);
}
