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

import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.AppCheckProviderFactory;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.recaptchaenterprise.internal.ProviderMultiResourceComponent;
import com.google.firebase.appcheck.recaptchaenterprise.internal.RecaptchaEnterpriseAppCheckProvider;
import java.util.Objects;

/**
 * Implementation of an {@link AppCheckProviderFactory} that builds <br>
 * {@link RecaptchaEnterpriseAppCheckProvider}s. This is the default implementation.
 */
public class RecaptchaEnterpriseAppCheckProviderFactory implements AppCheckProviderFactory {

  private final String siteKey;
  private volatile RecaptchaEnterpriseAppCheckProvider provider;

  private RecaptchaEnterpriseAppCheckProviderFactory(@NonNull String siteKey) {
    this.siteKey = siteKey;
  }

  /** Gets an instance of this class for installation into a {@link FirebaseAppCheck} instance. */
  @NonNull
  public static RecaptchaEnterpriseAppCheckProviderFactory getInstance(@NonNull String siteKey) {
    Objects.requireNonNull(siteKey, "siteKey cannot be null");
    return new RecaptchaEnterpriseAppCheckProviderFactory(siteKey);
  }

  @NonNull
  @Override
  @SuppressWarnings("FirebaseUseExplicitDependencies")
  public AppCheckProvider create(@NonNull FirebaseApp firebaseApp) {
    if (provider == null) {
      synchronized (this) {
        if (provider == null) {
          ProviderMultiResourceComponent component =
              firebaseApp.get(ProviderMultiResourceComponent.class);
          provider = component.get(siteKey);
          provider.initializeRecaptchaClient();
        }
      }
    }
    return provider;
  }
}
