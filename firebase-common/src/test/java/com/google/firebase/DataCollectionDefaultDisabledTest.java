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
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = "NoAutoDataCollectionAndroidManifest.xml")
public class DataCollectionDefaultDisabledTest {

  @Test
  public void isDataCollectionDefaultEnabled_whenMetadataFalse_shouldReturnFalse() {
    withApp(app -> assertThat(app.isDataCollectionDefaultEnabled()).isFalse());
  }

  @Test
  public void isDataCollectionDefaultEnabled_whenMetadataFalseAndPrefsFalse_shouldReturnFalse() {
    setSharedPreferencesTo(false);
    withApp(app -> assertThat(app.isDataCollectionDefaultEnabled()).isFalse());
  }

  @Test
  public void isDataCollectionDefaultEnabled_whenMetadataFalseAndPrefsTrue_shouldReturnTrue() {
    setSharedPreferencesTo(true);
    withApp(app -> assertThat(app.isDataCollectionDefaultEnabled()).isTrue());
  }

  @Test
  public void setDataCollectionDefaultEnabledTrue_shouldUpdateSharedPrefs() {
    withApp(
        app -> {
          app.setDataCollectionDefaultEnabled(true);
          SharedPreferences prefs = getSharedPreferences();
          assertThat(prefs.contains(DataCollectionConfigStorage.DATA_COLLECTION_DEFAULT_ENABLED))
              .isTrue();
          assertThat(
                  prefs.getBoolean(
                      DataCollectionConfigStorage.DATA_COLLECTION_DEFAULT_ENABLED, false))
              .isTrue();
          assertThat(app.isDataCollectionDefaultEnabled()).isTrue();
        });
  }

  @Test
  public void setDataCollectionDefaultEnabledTrue_shouldEmitEvents() {
    withApp(
        app -> {
          DataCollectionDefaultChangeRegistrar.ChangeListener changeListener =
              app.get(DataCollectionDefaultChangeRegistrar.ChangeListener.class);
          assertThat(changeListener.changes).isEmpty();

          app.setDataCollectionDefaultEnabled(false);
          assertThat(changeListener.changes).isEmpty();

          app.setDataCollectionDefaultEnabled(true);
          assertThat(changeListener.changes).containsExactly(true);

          app.setDataCollectionDefaultEnabled(false);
          assertThat(changeListener.changes).containsExactly(true, false).inOrder();
        });
  }
}
