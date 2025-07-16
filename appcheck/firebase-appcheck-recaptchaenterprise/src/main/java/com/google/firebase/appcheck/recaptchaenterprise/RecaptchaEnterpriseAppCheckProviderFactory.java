// Copyright 2025 Google LLC
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

package com.google.firebase.appcheck.recaptchaenterprise;

import android.app.Application;
import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.AppCheckProviderFactory;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.recaptchaenterprise.internal.FirebaseExecutors;
import com.google.firebase.appcheck.recaptchaenterprise.internal.RecaptchaEnterpriseAppCheckProvider;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of an {@link AppCheckProviderFactory} that builds <br>
 * {@link RecaptchaEnterpriseAppCheckProvider}s. This is the default implementation.
 */
public class RecaptchaEnterpriseAppCheckProviderFactory implements AppCheckProviderFactory {

  private static FirebaseExecutors firebaseExecutors;
  private static final Map<String, RecaptchaEnterpriseAppCheckProviderFactory> factoryInstances =
      new ConcurrentHashMap<>();
  private final String siteKey;
  private volatile RecaptchaEnterpriseAppCheckProvider provider;

  private RecaptchaEnterpriseAppCheckProviderFactory(@NonNull String siteKey) {
    this.siteKey = siteKey;
  }

  /** Gets an instance of this class for installation into a {@link FirebaseAppCheck} instance. */
  @NonNull
  public static RecaptchaEnterpriseAppCheckProviderFactory getInstance(@NonNull String siteKey) {
    RecaptchaEnterpriseAppCheckProviderFactory factory = factoryInstances.get(siteKey);
    if (factory == null) {
      synchronized (factoryInstances) {
        factory = factoryInstances.get(siteKey);
        if (factory == null) {
          factory = new RecaptchaEnterpriseAppCheckProviderFactory(siteKey);
          factoryInstances.put(siteKey, factory);
        }
      }
    }
    return factory;
  }

  @NonNull
  @Override
  @SuppressWarnings("FirebaseUseExplicitDependencies")
  public AppCheckProvider create(@NonNull FirebaseApp firebaseApp) {
    if (provider == null) {
      synchronized (this) {
        if (provider == null) {
          if (RecaptchaEnterpriseAppCheckProviderFactory.firebaseExecutors == null) {
            firebaseExecutors = firebaseApp.get(FirebaseExecutors.class);
          }
          Application application = firebaseApp.get(Application.class);

          provider =
              new RecaptchaEnterpriseAppCheckProvider(
                  firebaseApp,
                  application,
                  siteKey,
                  firebaseExecutors.getLiteExecutor(),
                  firebaseExecutors.getBlockingExecutor());
        }
      }
    }
    return provider;
  }
}
