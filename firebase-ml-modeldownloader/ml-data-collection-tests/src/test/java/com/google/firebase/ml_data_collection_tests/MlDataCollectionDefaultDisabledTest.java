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

package com.google.firebase.ml_data_collection_tests;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.ml_data_collection_tests.MlDataCollectionTestUtil.getSharedPreferencesUtil;
import static com.google.firebase.ml_data_collection_tests.MlDataCollectionTestUtil.setSharedPreferencesTo;
import static com.google.firebase.ml_data_collection_tests.MlDataCollectionTestUtil.withApp;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MlDataCollectionDefaultDisabledTest {

  @Test
  public void isMlDataCollectionDefaultEnabled_whenMetadataFalse_shouldReturnFalse() {
    withApp(
        app ->
            assertThat(getSharedPreferencesUtil(app).getCustomModelStatsCollectionFlag())
                .isFalse());
  }

  @Test
  public void isMlDataCollectionDefaultEnabled_whenMetadataFalseAndPrefsFalse_shouldReturnFalse() {
    withApp(
        app -> {
          setSharedPreferencesTo(app, false);
          assertThat(getSharedPreferencesUtil(app).getCustomModelStatsCollectionFlag()).isFalse();
        });
  }

  @Test
  public void isMlDataCollectionDefaultEnabled_whenMetadataFalseAndPrefsTrue_shouldReturnTrue() {
    withApp(
        app -> {
          setSharedPreferencesTo(app, true);
          assertThat(getSharedPreferencesUtil(app).getCustomModelStatsCollectionFlag()).isTrue();
        });
  }

  @Test
  public void isMlDataCollectionDefaultEnabled_whenMetadataFalseAndPrefsNull_shouldReturnFalse() {
    withApp(
        app -> {
          setSharedPreferencesTo(app, null);
          assertThat(getSharedPreferencesUtil(app).getCustomModelStatsCollectionFlag()).isFalse();
        });
  }
}
