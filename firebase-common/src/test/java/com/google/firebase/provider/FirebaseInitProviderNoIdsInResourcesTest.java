// Copyright 2018 Google LLC
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

package com.google.firebase.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.ProviderInfo;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.testing.FirebaseAppRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Unit tests for {@link FirebaseInitProvider} without IDs from resources. */
@RunWith(RobolectricTestRunner.class)
public class FirebaseInitProviderNoIdsInResourcesTest {

  private static final String GOOGLE_APP_ID = "1:855246033427:android:6e48bff8253f3f6e6e";
  private static final String GOOGLE_API_KEY = "AIzaSyD3asb-2pEZVqMkmL6M9N6nHZRR_znhrh0";

  private FirebaseInitProvider firebaseInitProvider;
  private Context targetContext;

  @Before
  public void setUp() {
    firebaseInitProvider = new FirebaseInitProvider();
    targetContext = RuntimeEnvironment.application.getApplicationContext();
  }

  @Rule public FirebaseAppRule firebaseAppRule = new FirebaseAppRule();

  @Test
  public void testFirebaseInitProvider() throws Exception {
    ProviderInfo providerInfo = new ProviderInfo();
    providerInfo.authority = "com.google.android.gms.tests.common.firebaseinitprovider";
    firebaseInitProvider.attachInfo(targetContext, providerInfo);
    try {
      FirebaseApp.getInstance();
      fail();
    } catch (Exception expected) {
    }

    // Now we set an app explicitly.
    FirebaseOptions firebaseOptions =
        new FirebaseOptions.Builder()
            .setApiKey(GOOGLE_API_KEY)
            .setApplicationId(GOOGLE_APP_ID)
            .build();
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(targetContext, firebaseOptions);

    assertEquals(firebaseApp, FirebaseApp.getInstance());
  }
}
