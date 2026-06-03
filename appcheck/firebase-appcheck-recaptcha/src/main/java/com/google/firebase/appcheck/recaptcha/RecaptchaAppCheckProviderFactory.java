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
import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.AppCheckProviderFactory;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.recaptcha.internal.ProviderMultiResourceComponent;
import com.google.firebase.appcheck.recaptcha.internal.RecaptchaAppCheckProvider;

/**
 * Implementation of an {@link AppCheckProviderFactory} that builds <br>
 * {@link RecaptchaAppCheckProvider}s. This is the default implementation.
 */
public class RecaptchaAppCheckProviderFactory implements AppCheckProviderFactory {

  private RecaptchaAppCheckProviderFactory() {}

  /** Gets an instance of this class for installation into a {@link FirebaseAppCheck} instance. */
  @NonNull
  public static RecaptchaAppCheckProviderFactory getInstance() {
    return new RecaptchaAppCheckProviderFactory();
  }

  @NonNull
  @Override
  @SuppressWarnings("FirebaseUseExplicitDependencies")
  public AppCheckProvider create(@NonNull FirebaseApp firebaseApp) {
    String siteKey = firebaseApp.getOptions().getRecaptchaSiteKey();
    Preconditions.checkNotEmpty(
        siteKey,
        "Missing site key from configuration. Verify your google-services.json file is updated.");
    ProviderMultiResourceComponent component =
        firebaseApp.get(ProviderMultiResourceComponent.class);
    RecaptchaAppCheckProvider provider = component.get(siteKey);
    provider.initializeRecaptchaClient();
    return provider;
  }
}
