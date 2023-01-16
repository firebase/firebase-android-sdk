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

package com.google.firebase.appdistribution;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import com.google.firebase.appdistribution.internal.FirebaseAppDistributionProxy;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentContainer;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;

/**
 * Registers {@link FirebaseAppDistributionProxy}.
 *
 * @hide
 */
@Keep
public class FirebaseAppDistributionApiRegistrar implements ComponentRegistrar {
  private static final String LIBRARY_NAME = "fire-appdistribution-api";

  @Override
  public @NonNull List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(FirebaseAppDistributionProxy.class)
            .name(LIBRARY_NAME)
            .add(Dependency.optionalProvider(FirebaseAppDistribution.class))
            .factory(this::buildFirebaseAppDistributionProxy)
            // construct FirebaseAppDistribution instance on startup so we can register for
            // activity lifecycle callbacks before the API is called
            .alwaysEager()
            .build(),
        LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME));
  }

  private FirebaseAppDistributionProxy buildFirebaseAppDistributionProxy(
      ComponentContainer container) {
    return new FirebaseAppDistributionProxy(container.getProvider(FirebaseAppDistribution.class));
  }
}
