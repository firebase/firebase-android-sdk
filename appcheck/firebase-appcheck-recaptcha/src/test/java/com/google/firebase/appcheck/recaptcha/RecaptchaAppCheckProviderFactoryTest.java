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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.recaptcha.internal.ProviderMultiResourceComponent;
import com.google.firebase.appcheck.recaptcha.internal.RecaptchaAppCheckProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link RecaptchaAppCheckProviderFactory}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RecaptchaAppCheckProviderFactoryTest {
  static final String SITE_KEY_1 = "siteKey1";

  @Mock private FirebaseApp mockFirebaseApp;
  @Mock private FirebaseOptions mockFirebaseOptions;
  @Mock private ProviderMultiResourceComponent mockComponent;
  @Mock private RecaptchaAppCheckProvider mockProvider;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    when(mockFirebaseApp.get(eq(ProviderMultiResourceComponent.class))).thenReturn(mockComponent);
    when(mockComponent.get(anyString())).thenReturn(mockProvider);
    when(mockFirebaseApp.getOptions()).thenReturn(mockFirebaseOptions);
  }

  @Test
  public void getInstance_returnsNonNullInstance() {
    RecaptchaAppCheckProviderFactory factory = RecaptchaAppCheckProviderFactory.getInstance();
    assertNotNull(factory);
  }

  @Test
  public void create_siteKeyInOptions_returnsRecaptchaAppCheckProvider() {
    when(mockFirebaseOptions.getRecaptchaSiteKey()).thenReturn(SITE_KEY_1);
    RecaptchaAppCheckProviderFactory factory = RecaptchaAppCheckProviderFactory.getInstance();
    AppCheckProvider provider = factory.create(mockFirebaseApp);
    assertNotNull(provider);
    assertEquals(RecaptchaAppCheckProvider.class, provider.getClass());
    verify(mockComponent).get(SITE_KEY_1);
  }

  @Test
  public void create_noSiteKeyInOptionsOrFactory_expectThrows() {
    when(mockFirebaseOptions.getRecaptchaSiteKey()).thenReturn(null);
    RecaptchaAppCheckProviderFactory factory = RecaptchaAppCheckProviderFactory.getInstance();
    assertThrows(IllegalArgumentException.class, () -> factory.create(mockFirebaseApp));
  }

  @Test
  public void create_callMultipleTimes_initializesProviderEveryTime() {
    when(mockFirebaseOptions.getRecaptchaSiteKey()).thenReturn(SITE_KEY_1);
    RecaptchaAppCheckProviderFactory factory = RecaptchaAppCheckProviderFactory.getInstance();

    factory.create(mockFirebaseApp);
    factory.create(mockFirebaseApp);
    factory.create(mockFirebaseApp);

    verify(mockComponent, times(3)).get(SITE_KEY_1);
    verify(mockProvider, times(3)).initializeRecaptchaClient();
  }
}
