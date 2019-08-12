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

/** Instrumented tests for {@link FiidCache} */
@RunWith(AndroidJUnit4.class)
public class FiidCacheTest {

  private FirebaseApp firebaseApp0;
  private FirebaseApp firebaseApp1;
  private FiidCache cache0;
  private FiidCache cache1;
  private final String AUTH_TOKEN = "auth_token";
  private final String REFRESH_TOKEN = "refresh_token";

  private final long TIMESTAMP_IN_SECONDS = 100L;

  @Before
  public void setUp() {
    FirebaseApp.clearInstancesForTest();
    firebaseApp0 =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder().setApplicationId("1:123456789:android:abcdef").build());
    firebaseApp1 =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder().setApplicationId("1:987654321:android:abcdef").build(),
            "firebase_app_1");
    cache0 = new FiidCache(firebaseApp0);
    cache1 = new FiidCache(firebaseApp1);
  }

  @After
  public void cleanUp() throws Exception {
    cache0.clear();
    cache1.clear();
  }

  @Test
  public void testReadCacheEntry_Null() {
    assertNull(cache0.readCacheEntryValue());
    assertNull(cache1.readCacheEntryValue());
  }

  @Test
  public void testUpdateAndReadCacheEntry() throws Exception {
    assertTrue(
        cache0.insertOrUpdateCacheEntry(
            FiidCacheEntryValue.create(
                "123456",
                FiidCache.CacheStatus.UNREGISTERED,
                AUTH_TOKEN,
                REFRESH_TOKEN,
                TIMESTAMP_IN_SECONDS,
                TIMESTAMP_IN_SECONDS)));
    FiidCacheEntryValue entryValue = cache0.readCacheEntryValue();
    assertThat(entryValue.getFirebaseInstallationId()).isEqualTo("123456");
    assertThat(entryValue.getAuthToken()).isEqualTo(AUTH_TOKEN);
    assertThat(entryValue.getRefreshToken()).isEqualTo(REFRESH_TOKEN);
    assertThat(entryValue.getCacheStatus()).isEqualTo(FiidCache.CacheStatus.UNREGISTERED);
    assertThat(entryValue.getExpiresInSecs()).isEqualTo(TIMESTAMP_IN_SECONDS);
    assertThat(entryValue.getTokenCreationEpochInSecs()).isEqualTo(TIMESTAMP_IN_SECONDS);
    assertNull(cache1.readCacheEntryValue());

    assertTrue(
        cache0.insertOrUpdateCacheEntry(
            FiidCacheEntryValue.create(
                "123456",
                FiidCache.CacheStatus.REGISTERED,
                AUTH_TOKEN,
                REFRESH_TOKEN,
                200L,
                TIMESTAMP_IN_SECONDS)));
    entryValue = cache0.readCacheEntryValue();
    assertThat(entryValue.getFirebaseInstallationId()).isEqualTo("123456");
    assertThat(entryValue.getAuthToken()).isEqualTo(AUTH_TOKEN);
    assertThat(entryValue.getRefreshToken()).isEqualTo(REFRESH_TOKEN);
    assertThat(entryValue.getCacheStatus()).isEqualTo(FiidCache.CacheStatus.REGISTERED);
    assertThat(entryValue.getExpiresInSecs()).isEqualTo(TIMESTAMP_IN_SECONDS);
    assertThat(entryValue.getTokenCreationEpochInSecs()).isEqualTo(200L);
  }
}
