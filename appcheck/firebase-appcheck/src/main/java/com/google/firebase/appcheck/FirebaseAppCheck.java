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
   * Installs the given {@link AppCheckProviderFactory}, overwriting any that previously was
   * associated with this FirebaseAppCheck instance. Any {@link AppCheckTokenListener}s attached to
   * this FirebaseAppCheck instance will be transferred from any existing factory to the newly
   * installed one.
   */
  public abstract void installAppCheckProviderFactory(@NonNull AppCheckProviderFactory factory);
}
