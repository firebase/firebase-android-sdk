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

import static com.google.firebase.installations.FisAndroidTestConstants.DEFAULT_PERSISTED_FID_ENTRY;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_APP_ID_1;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_APP_ID_2;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_AUTH_TOKEN;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_CREATION_TIMESTAMP_1;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_CREATION_TIMESTAMP_2;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_FID_1;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_REFRESH_TOKEN;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_TOKEN_EXPIRATION_TIMESTAMP;
import static com.google.firebase.installations.local.PersistedFidEntrySubject.assertThat;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.local.PersistedFid.RegistrationStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Instrumented tests for {@link PersistedFid} */
@RunWith(AndroidJUnit4.class)
public class PersistedFidTest {

  private FirebaseApp firebaseApp0;
  private FirebaseApp firebaseApp1;
  private PersistedFid persistedFid0;
  private PersistedFid persistedFid1;

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
    persistedFid0 = new PersistedFid(firebaseApp0);
    persistedFid1 = new PersistedFid(firebaseApp1);
  }

  @After
  public void cleanUp() throws Exception {
    persistedFid0.clear();
    persistedFid1.clear();
  }

  @Test
  public void testReadPersistedFidEntry_Null() {
    assertThat(persistedFid0.readPersistedFidEntryValue()).isEqualTo(DEFAULT_PERSISTED_FID_ENTRY);
    assertThat(persistedFid1.readPersistedFidEntryValue()).isEqualTo(DEFAULT_PERSISTED_FID_ENTRY);
  }

  @Test
  public void testUpdateAndReadPersistedFidEntry_successful() throws Exception {
    // Insert Persisted Fid Entry with Unregistered status in Shared Prefs
    assertTrue(
        persistedFid0.insertOrUpdatePersistedFidEntry(
            PersistedFidEntry.builder()
                .setFirebaseInstallationId(TEST_FID_1)
                .setAuthToken(TEST_AUTH_TOKEN)
                .setRefreshToken(TEST_REFRESH_TOKEN)
                .setRegistrationStatus(PersistedFid.RegistrationStatus.UNREGISTERED)
                .setTokenCreationEpochInSecs(TEST_CREATION_TIMESTAMP_1)
                .setExpiresInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                .build()));
    PersistedFidEntry entryValue = persistedFid0.readPersistedFidEntryValue();

    // Validate insertion was successful
    assertThat(entryValue).hasFid(TEST_FID_1);
    assertThat(entryValue).hasAuthToken(TEST_AUTH_TOKEN);
    assertThat(entryValue).hasRefreshToken(TEST_REFRESH_TOKEN);
    assertThat(entryValue).hasRegistrationStatus(RegistrationStatus.UNREGISTERED);
    assertThat(entryValue).hasTokenExpirationTimestamp(TEST_TOKEN_EXPIRATION_TIMESTAMP);
    assertThat(entryValue).hasCreationTimestamp(TEST_CREATION_TIMESTAMP_1);

    // Update Persisted Fid Entry with Registered status in Shared Prefs
    assertTrue(
        persistedFid0.insertOrUpdatePersistedFidEntry(
            PersistedFidEntry.builder()
                .setFirebaseInstallationId(TEST_FID_1)
                .setAuthToken(TEST_AUTH_TOKEN)
                .setRefreshToken(TEST_REFRESH_TOKEN)
                .setRegistrationStatus(PersistedFid.RegistrationStatus.REGISTERED)
                .setTokenCreationEpochInSecs(TEST_CREATION_TIMESTAMP_2)
                .setExpiresInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                .build()));
    entryValue = persistedFid0.readPersistedFidEntryValue();

    // Validate update was successful
    assertThat(entryValue).hasFid(TEST_FID_1);
    assertThat(entryValue).hasAuthToken(TEST_AUTH_TOKEN);
    assertThat(entryValue).hasRefreshToken(TEST_REFRESH_TOKEN);
    assertThat(entryValue).hasRegistrationStatus(RegistrationStatus.REGISTERED);
    assertThat(entryValue).hasTokenExpirationTimestamp(TEST_TOKEN_EXPIRATION_TIMESTAMP);
    assertThat(entryValue).hasCreationTimestamp(TEST_CREATION_TIMESTAMP_2);
  }
}
