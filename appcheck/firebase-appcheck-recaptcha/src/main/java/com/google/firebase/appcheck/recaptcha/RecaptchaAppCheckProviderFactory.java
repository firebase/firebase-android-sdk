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

package com.google.firebase.appcheck.recaptcha;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.AppCheckProviderFactory;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.recaptcha.internal.ProviderMultiResourceComponent;
import com.google.firebase.appcheck.recaptcha.internal.RecaptchaAppCheckProvider;
import java.util.Objects;

/**
 * Implementation of {@link AppCheckProviderFactory} for the reCAPTCHA attestation provider
 */
public class RecaptchaAppCheckProviderFactory implements AppCheckProviderFactory {

  private final String siteKey;
  private volatile RecaptchaAppCheckProvider provider;

  private RecaptchaAppCheckProviderFactory(@Nullable String siteKey) {
    this.siteKey = siteKey;
  }

  /**
   * Gets an instance of this class for installation into a {@link FirebaseAppCheck} instance.
   *
   * @deprecated Use {@link #getInstance(String)} instead.
   */
  @Deprecated
  @NonNull
  public static RecaptchaAppCheckProviderFactory getInstance() {
    return new RecaptchaAppCheckProviderFactory(null);
  }

  /** Gets an instance of this class for installation into a {@link FirebaseAppCheck} instance. */
  @NonNull
  public static RecaptchaAppCheckProviderFactory getInstance(@NonNull String siteKey) {
    Objects.requireNonNull(siteKey, "siteKey cannot be null");
    return new RecaptchaAppCheckProviderFactory(siteKey);
  }

  @NonNull
  @Override
  @SuppressWarnings({"FirebaseUseExplicitDependencies", "deprecation"})
  public AppCheckProvider create(@NonNull FirebaseApp firebaseApp) {
    String siteKey = this.siteKey;
    if (siteKey == null) {
      siteKey = firebaseApp.getOptions().getRecaptchaSiteKey();
      Preconditions.checkNotEmpty(
          siteKey,
          "Missing site key from configuration. Verify your google-services.json file is updated.");
      ProviderMultiResourceComponent component =
          firebaseApp.get(ProviderMultiResourceComponent.class);
      RecaptchaAppCheckProvider provider = component.get(siteKey);
      provider.initializeRecaptchaClient();
      return provider;
    }
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
