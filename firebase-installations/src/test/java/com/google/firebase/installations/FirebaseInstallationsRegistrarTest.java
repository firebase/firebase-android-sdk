// Copyright 2019 Google LLC
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

package com.google.firebase.installations;

import static com.google.firebase.installations.FirebaseInstallationsTest.TEST_API_KEY;
import static com.google.firebase.installations.FirebaseInstallationsTest.TEST_PROJECT_ID;
import static org.junit.Assert.assertNotNull;

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link FirebaseInstallationsRegistrar}. */
@RunWith(RobolectricTestRunner.class)
public class FirebaseInstallationsRegistrarTest {
  @Before
  public void setUp() {
    FirebaseApp.clearInstancesForTest();
  }

  @Test
  public void getFirebaseInstallationsInstance() {
    FirebaseApp defaultApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId("1:123456789:android:abcdef")
                .setApiKey(TEST_API_KEY)
                .setProjectId(TEST_PROJECT_ID)
                .build());

    FirebaseApp anotherApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId("1:987654321:android:abcdef")
                .setApiKey(TEST_API_KEY)
                .setProjectId(TEST_PROJECT_ID)
                .build(),
            "firebase_app_1");

    FirebaseInstallations defaultFirebaseInstallation = FirebaseInstallations.getInstance();
    assertNotNull(defaultFirebaseInstallation);

    FirebaseInstallations anotherFirebaseInstallation =
        FirebaseInstallations.getInstance(anotherApp);
    assertNotNull(anotherFirebaseInstallation);
  }
}
