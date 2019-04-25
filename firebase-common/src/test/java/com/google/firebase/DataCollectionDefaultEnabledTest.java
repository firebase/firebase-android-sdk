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
import static com.google.firebase.DataCollectionTestUtil.getSharedPreferences;
import static com.google.firebase.DataCollectionTestUtil.setSharedPreferencesTo;
import static com.google.firebase.DataCollectionTestUtil.withApp;

import android.content.SharedPreferences;
import com.google.firebase.internal.DataCollectionConfigStorage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DataCollectionDefaultEnabledTest {

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
          assertThat(prefs.contains(DataCollectionConfigStorage.DATA_COLLECTION_DEFAULT_ENABLED))
              .isTrue();
          assertThat(
                  prefs.getBoolean(
                      DataCollectionConfigStorage.DATA_COLLECTION_DEFAULT_ENABLED, true))
              .isFalse();
          assertThat(app.isDataCollectionDefaultEnabled()).isFalse();
        });
  }

  @Test
  public void setDataCollectionDefaultEnabled_shouldNotAffectOtherFirebaseAppInstances() {
    withApp(
        "app1",
        app1 -> {
          withApp(
              "app2",
              app2 -> {
                assertThat(app1.isDataCollectionDefaultEnabled()).isTrue();
                assertThat(app2.isDataCollectionDefaultEnabled()).isTrue();
              });

          app1.setDataCollectionDefaultEnabled(false);
          withApp(
              "app2",
              app2 -> {
                assertThat(app1.isDataCollectionDefaultEnabled()).isFalse();
                assertThat(app2.isDataCollectionDefaultEnabled()).isTrue();
              });
        });
  }
}
