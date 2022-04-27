// Copyright 2020 Google LLC
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

package com.google.firebase.appcheck;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link FirebaseAppCheck}. */
@RunWith(RobolectricTestRunner.class)
public class FirebaseAppCheckTest {

  private static final String API_KEY = "apiKey";
  private static final String APP_ID = "appId";
  private static final String OTHER_FIREBASE_APP_NAME = "otherFirebaseAppName";

  @Before
  public void setup() {
    FirebaseApp.clearInstancesForTest();
    initializeFirebaseApp(
        ApplicationProvider.getApplicationContext(), FirebaseApp.DEFAULT_APP_NAME);
    initializeFirebaseApp(ApplicationProvider.getApplicationContext(), OTHER_FIREBASE_APP_NAME);
  }

  @Test
  public void testGetInstance_defaultFirebaseAppName_matchesDefaultGetter() {
    FirebaseAppCheck defaultGetter = FirebaseAppCheck.getInstance();
    FirebaseAppCheck namedGetter =
        FirebaseAppCheck.getInstance(FirebaseApp.getInstance(FirebaseApp.DEFAULT_APP_NAME));
    assertThat(defaultGetter).isEqualTo(namedGetter);
  }

  @Test
  public void testGetInstance_otherFirebaseAppName_doesNotMatch() {
    FirebaseAppCheck defaultGetter = FirebaseAppCheck.getInstance();
    FirebaseAppCheck namedGetter =
        FirebaseAppCheck.getInstance(FirebaseApp.getInstance(OTHER_FIREBASE_APP_NAME));
    assertThat(defaultGetter).isNotEqualTo(namedGetter);
  }

  private static void initializeFirebaseApp(Context context, String name) {
    FirebaseApp.initializeApp(
        context,
        new FirebaseOptions.Builder().setApiKey(API_KEY).setApplicationId(APP_ID).build(),
        name);
  }
}
