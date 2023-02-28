// Copyright 2022 Google LLC
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

package com.google.firebase.appcheck.playintegrity;

import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.AppCheckProviderFactory;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.internal.PlayIntegrityAppCheckProvider;

/**
 * Implementation of an {@link AppCheckProviderFactory} that builds <br>
 * {@link PlayIntegrityAppCheckProvider}s. This is the default implementation.
 */
public class PlayIntegrityAppCheckProviderFactory implements AppCheckProviderFactory {

  private static final PlayIntegrityAppCheckProviderFactory instance =
      new PlayIntegrityAppCheckProviderFactory();

  /** Gets an instance of this class for installation into a {@link FirebaseAppCheck} instance. */
  @NonNull
  public static PlayIntegrityAppCheckProviderFactory getInstance() {
    return instance;
  }

  @NonNull
  @Override
  @SuppressWarnings("FirebaseUseExplicitDependencies")
  public AppCheckProvider create(@NonNull FirebaseApp firebaseApp) {
    return firebaseApp.get(PlayIntegrityAppCheckProvider.class);
  }
}
