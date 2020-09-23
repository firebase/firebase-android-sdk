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

package com.google.firebase.firestore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FirebaseFirestoreTest {

  @Test
  public void getInstance_withEmulator() {
    FirebaseApp app = getApp("getInstance_withEmulator");

    FirebaseFirestore firestore = FirebaseFirestore.getInstance(app);
    firestore.useEmulator("10.0.2.2", 8080);
    FirebaseFirestoreSettings settings = firestore.getFirestoreSettings();

    assertEquals(settings.getHost(), "10.0.2.2:8080");
    assertFalse(settings.isSslEnabled());
  }

  @Test
  public void getInstance_withEmulator_mergeSettingsSuccess() {
    FirebaseApp app = getApp("getInstance_withEmulator_mergeSettingsSuccess");

    FirebaseFirestore firestore = FirebaseFirestore.getInstance(app);
    firestore.useEmulator("10.0.2.2", 8080);
    firestore.setFirestoreSettings(
        new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());

    FirebaseFirestoreSettings settings = firestore.getFirestoreSettings();

    assertEquals(settings.getHost(), "10.0.2.2:8080");
    assertFalse(settings.isSslEnabled());
    assertFalse(settings.isPersistenceEnabled());
  }

  @Test
  public void getInstance_withEmulator_lateFailure() {
    FirebaseApp app = getApp("getInstance_withEmulator_lateFailure");

    try {
      FirebaseFirestore firestore = FirebaseFirestore.getInstance(app);
      CollectionReference ref = firestore.collection("foo");

      firestore.useEmulator("10.0.2.2", 8080);
      fail("Exception should be thrown");
    } catch (Exception e) {
      assertTrue(e instanceof IllegalStateException);
      assertEquals(
          e.getMessage(), "Cannot call useEmulator() after instance has already been initialized.");
    }
  }

  @Test
  public void setSettings_repeatedSuccess() {
    FirebaseApp app = getApp("setSettings_repeatedSuccess");
    FirebaseFirestore firestore = FirebaseFirestore.getInstance(app);

    FirebaseFirestoreSettings settings =
        new FirebaseFirestoreSettings.Builder().setHost("myhost.com").setSslEnabled(false).build();
    firestore.setFirestoreSettings(settings);

    // This should 'start' Firestore
    DocumentReference reference = firestore.document("foo/bar");

    // Second settings set should pass because the settings are equal
    firestore.setFirestoreSettings(settings);
  }

  @Test
  public void setSettings_repeatedSuccess_withEmulator() {
    FirebaseApp app = getApp("setSettings_repeatedSuccess_withEmulator");

    FirebaseFirestore firestore = FirebaseFirestore.getInstance(app);
    firestore.useEmulator("10.0.2.2", 8080);

    FirebaseFirestoreSettings settings =
        new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build();
    firestore.setFirestoreSettings(settings);

    // This should 'start' Firestore
    DocumentReference reference = firestore.document("foo/bar");

    // Second settings set should pass because the settings are equal
    firestore.setFirestoreSettings(settings);
  }

  @Test
  public void setSettings_repeatedFailure() {
    FirebaseApp app = getApp("setSettings_repeatedFailure");
    FirebaseFirestore firestore = FirebaseFirestore.getInstance(app);

    FirebaseFirestoreSettings settings =
        new FirebaseFirestoreSettings.Builder().setHost("myhost.com").setSslEnabled(false).build();

    FirebaseFirestoreSettings otherSettings =
        new FirebaseFirestoreSettings.Builder()
            .setHost("otherhost.com")
            .setSslEnabled(false)
            .build();

    firestore.setFirestoreSettings(settings);

    // This should 'start' Firestore
    DocumentReference reference = firestore.document("foo/bar");

    try {
      firestore.setFirestoreSettings(otherSettings);
      fail("Exception should be thrown");
    } catch (Exception e) {
      assertTrue(e instanceof IllegalStateException);
      assertTrue(
          e.getMessage()
              .startsWith(
                  "FirebaseFirestore has already been started and its settings can no longer be changed."));
    }
  }

  @NonNull
  private FirebaseApp getApp(@NonNull String name) {
    return FirebaseApp.initializeApp(
        InstrumentationRegistry.getInstrumentation().getContext(),
        new FirebaseOptions.Builder()
            .setApplicationId("appid")
            .setApiKey("apikey")
            .setProjectId("projectid")
            .build(),
        name);
  }
}
