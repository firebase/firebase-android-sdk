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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.recaptchaenterprise.internal.ProviderMultiResourceComponent;
import com.google.firebase.appcheck.recaptchaenterprise.internal.RecaptchaEnterpriseAppCheckProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link RecaptchaEnterpriseAppCheckProviderFactory}. */
@RunWith(MockitoJUnitRunner.class)
public class RecaptchaEnterpriseAppCheckProviderFactoryTest {
  static final String SITE_KEY_1 = "siteKey1";

  @Mock private FirebaseApp mockFirebaseApp;
  @Mock private ProviderMultiResourceComponent mockComponent;
  @Mock private RecaptchaEnterpriseAppCheckProvider mockProvider;

  @Before
  public void setUp() {
    when(mockFirebaseApp.get(eq(ProviderMultiResourceComponent.class))).thenReturn(mockComponent);
    when(mockComponent.get(anyString())).thenReturn(mockProvider);
  }

  @Test
  public void getInstance_nonNullSiteKey_returnsNonNullInstance() {
    RecaptchaEnterpriseAppCheckProviderFactory factory =
        RecaptchaEnterpriseAppCheckProviderFactory.getInstance(SITE_KEY_1);
    assertNotNull(factory);
  }

  @Test
  public void getInstance_nullSiteKey_expectThrows() {
    assertThrows(
        NullPointerException.class,
        () -> RecaptchaEnterpriseAppCheckProviderFactory.getInstance(null));
  }

  @Test
  public void create_nonNullFirebaseApp_returnsRecaptchaEnterpriseAppCheckProvider() {
    RecaptchaEnterpriseAppCheckProviderFactory factory =
        RecaptchaEnterpriseAppCheckProviderFactory.getInstance(SITE_KEY_1);
    AppCheckProvider provider = factory.create(mockFirebaseApp);
    assertNotNull(provider);
    assertEquals(RecaptchaEnterpriseAppCheckProvider.class, provider.getClass());
  }

  @Test
  public void create_callMultipleTimes_providerIsInitializedOnlyOnce() {
    RecaptchaEnterpriseAppCheckProviderFactory factory =
        RecaptchaEnterpriseAppCheckProviderFactory.getInstance(SITE_KEY_1);

    factory.create(mockFirebaseApp);
    factory.create(mockFirebaseApp);
    factory.create(mockFirebaseApp);
    verify(mockProvider, times(1)).initializeRecaptchaClient();
  }
}
