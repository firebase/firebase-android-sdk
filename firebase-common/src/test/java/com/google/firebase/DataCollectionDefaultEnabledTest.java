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

package com.google.firebase;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class DataCollectionDefaultEnabledTest {

  private static final String NO_AUTO_DATA_COLLECTION_MANIFEST =
      "NoAutoDataCollectionAndroidManifest.xml";

  @Test
  public void isDataCollectionDefaultEnabled_shouldDefaultToTrue() {
    withApp(app -> assertThat(app.isDataCollectionDefaultEnabled()).isTrue());
  }

  @Test
  public void isDataCollectionDefaultEnabled_whenPrefsFalse_shouldReturnFalse() {
    setSharedPreferencesTo(false);

    withApp(app -> assertThat(app.isDataCollectionDefaultEnabled()).isFalse());
  }

  @Test
  public void isDataCollectionDefaultEnabled_whenPrefsTrue_shouldReturnTrue() {
    setSharedPreferencesTo(true);

    withApp(app -> assertThat(app.isDataCollectionDefaultEnabled()).isTrue());
  }

  @Test
  public void setDataCollectionDefaultEnabledFalse_shouldUpdateSharedPrefs() {
    withApp(
        app -> {
          app.setDataCollectionDefaultEnabled(false);
          SharedPreferences prefs = getSharedPreferences();
          assertThat(prefs.contains(FirebaseApp.DATA_COLLECTION_DEFAULT_ENABLED)).isTrue();
          assertThat(prefs.getBoolean(FirebaseApp.DATA_COLLECTION_DEFAULT_ENABLED, true)).isFalse();
          assertThat(app.isDataCollectionDefaultEnabled()).isFalse();
        });
  }

  private static void withApp(Consumer<FirebaseApp> callable) {
    FirebaseApp app =
        FirebaseApp.initializeApp(
            RuntimeEnvironment.application.getApplicationContext(),
            new FirebaseOptions.Builder().setApplicationId("appId").build(),
            "someApp");
    try {
      callable.accept(app);
    } finally {
      app.delete();
    }
  }

  private static SharedPreferences getSharedPreferences() {
    return RuntimeEnvironment.application.getSharedPreferences(
        FirebaseApp.FIREBASE_APP_PREFS, Context.MODE_PRIVATE);
  }

  private static void setSharedPreferencesTo(boolean enabled) {
    getSharedPreferences()
        .edit()
        .putBoolean(FirebaseApp.DATA_COLLECTION_DEFAULT_ENABLED, enabled)
        .commit();
  }
}
