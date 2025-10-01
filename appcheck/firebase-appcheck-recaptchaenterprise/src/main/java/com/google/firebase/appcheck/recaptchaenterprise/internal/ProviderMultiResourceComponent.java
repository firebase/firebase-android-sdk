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

package com.google.firebase.appcheck.recaptchaenterprise.internal;

import androidx.annotation.NonNull;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Multi-resource container for RecaptchaEnterpriseAppCheckProvider */
@Singleton
public final class ProviderMultiResourceComponent {
  private final RecaptchaEnterpriseAppCheckProviderFactory providerFactory;

  private final Map<String, RecaptchaEnterpriseAppCheckProvider> instances =
      new ConcurrentHashMap<>();

  @Inject
  ProviderMultiResourceComponent(RecaptchaEnterpriseAppCheckProviderFactory providerFactory) {
    this.providerFactory = providerFactory;
  }

  @NonNull
  public RecaptchaEnterpriseAppCheckProvider get(@NonNull String siteKey) {
    RecaptchaEnterpriseAppCheckProvider provider = instances.get(siteKey);
    if (provider == null) {
      synchronized (instances) {
        provider = instances.get(siteKey);
        if (provider == null) {
          provider = providerFactory.create(siteKey);
          instances.put(siteKey, provider);
        }
      }
    }
    return provider;
  }

  @AssistedFactory
  interface RecaptchaEnterpriseAppCheckProviderFactory {
    RecaptchaEnterpriseAppCheckProvider create(@Assisted String siteKey);
  }
}
