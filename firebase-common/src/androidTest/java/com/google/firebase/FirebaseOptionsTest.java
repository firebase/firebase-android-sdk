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

import androidx.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link FirebaseOptions}. */
@RunWith(AndroidJUnit4.class)
public class FirebaseOptionsTest {

  private static final String GOOGLE_APP_ID = "1:855246033427:android:6e48bff8253f3f6e6e";
  private static final String GOOGLE_API_KEY = "AIzaSyD3asb-2pEZVqMkmL6M9N6nHZRR_znhrh0";
  private static final String FIREBASE_DB_URL = "https://ghconfigtest-644f2.firebaseio.com";
  private static final String GA_TRACKING_ID = "UA-123456-2";
  private static final String GCM_SENDER_ID = "309678045053";
  private static final String STORAGE_BUCKET = "ghconfigtest-644f2";
  private static final String GCP_PROJECT_ID = "test-product-id";

  private static final FirebaseOptions ALL_VALUES_OPTIONS =
      new FirebaseOptions.Builder()
          .setApplicationId(GOOGLE_APP_ID)
          .setApiKey(GOOGLE_API_KEY)
          .setDatabaseUrl(FIREBASE_DB_URL)
          .setGaTrackingId(GA_TRACKING_ID)
          .setGcmSenderId(GCM_SENDER_ID)
          .setStorageBucket(STORAGE_BUCKET)
          .setProjectId(GCP_PROJECT_ID)
          .build();

  @Test
  public void createOptionsWithAllValuesSet() {
    FirebaseOptions firebaseOptions =
        new FirebaseOptions.Builder()
            .setApplicationId(GOOGLE_APP_ID)
            .setApiKey(GOOGLE_API_KEY)
            .setDatabaseUrl(FIREBASE_DB_URL)
            .setGaTrackingId(GA_TRACKING_ID)
            .setGcmSenderId(GCM_SENDER_ID)
            .setStorageBucket(STORAGE_BUCKET)
            .setProjectId(GCP_PROJECT_ID)
            .build();
    assertThat(firebaseOptions.getApplicationId()).isEqualTo(GOOGLE_APP_ID);
    assertThat(firebaseOptions.getApiKey()).isEqualTo(GOOGLE_API_KEY);
    assertThat(firebaseOptions.getDatabaseUrl()).isEqualTo(FIREBASE_DB_URL);
    assertThat(firebaseOptions.getGaTrackingId()).isEqualTo(GA_TRACKING_ID);
    assertThat(firebaseOptions.getGcmSenderId()).isEqualTo(GCM_SENDER_ID);
    assertThat(firebaseOptions.getStorageBucket()).isEqualTo(STORAGE_BUCKET);
    assertThat(firebaseOptions.getProjectId()).isEqualTo(GCP_PROJECT_ID);
  }

  @Test
  public void createOptionsWithOnlyMandatoryValuesSet() {
    FirebaseOptions firebaseOptions =
        new FirebaseOptions.Builder().setApplicationId(GOOGLE_APP_ID).build();
    assertThat(firebaseOptions.getApplicationId()).isEqualTo(GOOGLE_APP_ID);
  }

  @Test(expected = IllegalStateException.class)
  public void createOptionsWithAppIdMissing() {
    new FirebaseOptions.Builder().setApiKey(GOOGLE_API_KEY).build();
  }

  @Test
  public void checkToBuilderCreatesNewEquivalentInstance() {
    FirebaseOptions allValuesOptionsCopy = new FirebaseOptions.Builder(ALL_VALUES_OPTIONS).build();
    assertThat(allValuesOptionsCopy).isNotSameInstanceAs(ALL_VALUES_OPTIONS);
    assertThat(allValuesOptionsCopy).isEqualTo(ALL_VALUES_OPTIONS);
  }
}
