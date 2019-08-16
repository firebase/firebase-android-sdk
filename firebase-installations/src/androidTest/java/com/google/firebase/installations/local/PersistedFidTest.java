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

package com.google.firebase.installations.local;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_APP_ID_1;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_APP_ID_2;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_AUTH_TOKEN;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_CREATION_TIMESTAMP_1;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_CREATION_TIMESTAMP_2;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_FID_1;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_REFRESH_TOKEN;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_TOKEN_EXPIRATION_TIMESTAMP;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Instrumented tests for {@link PersistedFid} */
@RunWith(AndroidJUnit4.class)
public class PersistedFidTest {

  private FirebaseApp firebaseApp0;
  private FirebaseApp firebaseApp1;
  private PersistedFid cache0;
  private PersistedFid cache1;

  @Before
  public void setUp() {
    FirebaseApp.clearInstancesForTest();
    firebaseApp0 =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder().setApplicationId(TEST_APP_ID_1).build());
    firebaseApp1 =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder().setApplicationId(TEST_APP_ID_2).build(),
            "firebase_app_1");
    cache0 = new PersistedFid(firebaseApp0);
    cache1 = new PersistedFid(firebaseApp1);
  }

  @After
  public void cleanUp() throws Exception {
    cache0.clear();
    cache1.clear();
  }

  @Test
  public void testReadCacheEntry_Null() {
    assertNull(cache0.readPersistedFidEntryValue());
    assertNull(cache1.readPersistedFidEntryValue());
  }

  @Test
  public void testUpdateAndReadCacheEntry() throws Exception {
    assertTrue(
        cache0.insertOrUpdatePersistedFidEntry(
            PersistedFidEntry.builder()
                .setFirebaseInstallationId(TEST_FID_1)
                .setAuthToken(TEST_AUTH_TOKEN)
                .setRefreshToken(TEST_REFRESH_TOKEN)
                .setPersistedStatus(PersistedFid.PersistedStatus.UNREGISTERED)
                .setTokenCreationEpochInSecs(TEST_CREATION_TIMESTAMP_1)
                .setExpiresInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                .build()));
    PersistedFidEntry entryValue = cache0.readPersistedFidEntryValue();
    assertThat(entryValue.getFirebaseInstallationId()).isEqualTo(TEST_FID_1);
    assertThat(entryValue.getAuthToken()).isEqualTo(TEST_AUTH_TOKEN);
    assertThat(entryValue.getRefreshToken()).isEqualTo(TEST_REFRESH_TOKEN);
    assertThat(entryValue.getPersistedStatus())
        .isEqualTo(PersistedFid.PersistedStatus.UNREGISTERED);
    assertThat(entryValue.getExpiresInSecs()).isEqualTo(TEST_TOKEN_EXPIRATION_TIMESTAMP);
    assertThat(entryValue.getTokenCreationEpochInSecs()).isEqualTo(TEST_CREATION_TIMESTAMP_1);
    assertNull(cache1.readPersistedFidEntryValue());

    assertTrue(
        cache0.insertOrUpdatePersistedFidEntry(
            PersistedFidEntry.builder()
                .setFirebaseInstallationId(TEST_FID_1)
                .setAuthToken(TEST_AUTH_TOKEN)
                .setRefreshToken(TEST_REFRESH_TOKEN)
                .setPersistedStatus(PersistedFid.PersistedStatus.REGISTERED)
                .setTokenCreationEpochInSecs(TEST_CREATION_TIMESTAMP_2)
                .setExpiresInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                .build()));
    entryValue = cache0.readPersistedFidEntryValue();
    assertThat(entryValue.getFirebaseInstallationId()).isEqualTo(TEST_FID_1);
    assertThat(entryValue.getAuthToken()).isEqualTo(TEST_AUTH_TOKEN);
    assertThat(entryValue.getRefreshToken()).isEqualTo(TEST_REFRESH_TOKEN);
    assertThat(entryValue.getPersistedStatus()).isEqualTo(PersistedFid.PersistedStatus.REGISTERED);
    assertThat(entryValue.getExpiresInSecs()).isEqualTo(TEST_TOKEN_EXPIRATION_TIMESTAMP);
    assertThat(entryValue.getTokenCreationEpochInSecs()).isEqualTo(TEST_CREATION_TIMESTAMP_2);
  }
}
