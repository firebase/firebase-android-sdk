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

package com.google.firebase.appcheck.debug.testing;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.internal.DefaultFirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DebugAppCheckTestHelperTest {
  private static final String API_KEY = "apiKey";
  private static final String PROJECT_ID = "projectId";
  private static final String APP_ID = "appId";
  private static final String OTHER_FIREBASE_APP_NAME = "otherFirebaseAppName";

  private final DebugAppCheckTestHelper debugAppCheckTestHelper =
      DebugAppCheckTestHelper.fromInstrumentationArgs();

  @Before
  public void setUp() {
    FirebaseApp.clearInstancesForTest();
    initializeFirebaseApp(
        ApplicationProvider.getApplicationContext(), FirebaseApp.DEFAULT_APP_NAME);
    initializeFirebaseApp(ApplicationProvider.getApplicationContext(), OTHER_FIREBASE_APP_NAME);
  }

  @Test
  public void testDebugAppCheckTestHelper_withDebugProviderDefaultApp_installsDebugProvider() {
    DefaultFirebaseAppCheck firebaseAppCheck =
        (DefaultFirebaseAppCheck) FirebaseAppCheck.getInstance();
    firebaseAppCheck.installAppCheckProviderFactory(
        PlayIntegrityAppCheckProviderFactory.getInstance());

    // Sanity check
    assertThat(firebaseAppCheck.getInstalledAppCheckProviderFactory())
        .isEqualTo(PlayIntegrityAppCheckProviderFactory.getInstance());

    debugAppCheckTestHelper.withDebugProvider(
        () -> {
          assertThat(firebaseAppCheck.getInstalledAppCheckProviderFactory())
              .isInstanceOf(DebugAppCheckProviderFactory.class);
        });

    // Make sure the factory is reset.
    assertThat(firebaseAppCheck.getInstalledAppCheckProviderFactory())
        .isEqualTo(PlayIntegrityAppCheckProviderFactory.getInstance());
  }

  @Test
  public void testDebugAppCheckTestHelper_withDebugProviderNamedApp_installsDebugProvider() {
    FirebaseApp firebaseApp = FirebaseApp.getInstance(OTHER_FIREBASE_APP_NAME);
    DefaultFirebaseAppCheck firebaseAppCheck =
        (DefaultFirebaseAppCheck) FirebaseAppCheck.getInstance(firebaseApp);
    firebaseAppCheck.installAppCheckProviderFactory(
        PlayIntegrityAppCheckProviderFactory.getInstance());

    // Sanity check
    assertThat(firebaseAppCheck.getInstalledAppCheckProviderFactory())
        .isEqualTo(PlayIntegrityAppCheckProviderFactory.getInstance());

    debugAppCheckTestHelper.withDebugProvider(
        firebaseApp,
        () -> {
          assertThat(firebaseAppCheck.getInstalledAppCheckProviderFactory())
              .isInstanceOf(DebugAppCheckProviderFactory.class);
        });

    // Make sure the factory is reset.
    assertThat(firebaseAppCheck.getInstalledAppCheckProviderFactory())
        .isEqualTo(PlayIntegrityAppCheckProviderFactory.getInstance());
  }

  @Test
  public void
      testDebugAppCheckTestHelper_withDebugProvider_noPreviousProvider_installsDebugProvider() {
    DefaultFirebaseAppCheck firebaseAppCheck =
        (DefaultFirebaseAppCheck) FirebaseAppCheck.getInstance();

    // Sanity check
    assertThat(firebaseAppCheck.getInstalledAppCheckProviderFactory()).isNull();

    debugAppCheckTestHelper.withDebugProvider(
        () ->
            assertThat(firebaseAppCheck.getInstalledAppCheckProviderFactory())
                .isInstanceOf(DebugAppCheckProviderFactory.class));

    // Make sure the factory is reset.
    assertThat(firebaseAppCheck.getInstalledAppCheckProviderFactory()).isNull();
  }

  private static void initializeFirebaseApp(Context context, String name) {
    FirebaseApp.initializeApp(
        context,
        new FirebaseOptions.Builder()
            .setApiKey(API_KEY)
            .setProjectId(PROJECT_ID)
            .setApplicationId(APP_ID)
            .build(),
        name);
  }
}
