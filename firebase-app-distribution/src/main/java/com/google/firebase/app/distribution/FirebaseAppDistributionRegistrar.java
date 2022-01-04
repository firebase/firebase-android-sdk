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

package com.google.firebase.app.distribution;

import android.app.Application;
import android.content.Context;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import com.google.firebase.app.distribution.internal.LogWrapper;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentContainer;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;

/**
 * Registers FirebaseAppDistribution
 *
 * @hide
 */
@Keep
public class FirebaseAppDistributionRegistrar implements ComponentRegistrar {

  private static String TAG = "Registrar:";

  @Override
  public @NonNull List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(FirebaseAppDistribution.class)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.requiredProvider(FirebaseInstallationsApi.class))
            .factory(this::buildFirebaseAppDistribution)
            .build(),
        LibraryVersionComponent.create("fire-app-distribution", BuildConfig.VERSION_NAME));
  }

  private FirebaseAppDistribution buildFirebaseAppDistribution(ComponentContainer container) {
    FirebaseApp firebaseApp = container.get(FirebaseApp.class);
    FirebaseAppDistribution appDistribution =
        new FirebaseAppDistribution(
            firebaseApp, container.getProvider(FirebaseInstallationsApi.class));
    FirebaseAppDistributionLifecycleNotifier lifecycleNotifier =
        FirebaseAppDistributionLifecycleNotifier.getInstance();

    Context context = firebaseApp.getApplicationContext();
    if (context instanceof Application) {
      Application firebaseApplication = (Application) context;
      firebaseApplication.registerActivityLifecycleCallbacks(lifecycleNotifier);
    } else {
      LogWrapper.getInstance()
          .e(
              TAG
                  + "Context "
                  + context
                  + " was not an Application, can't register for lifecycle callbacks. SDK might not"
                  + " function correctly.");
    }

    return appDistribution;
  }
}
