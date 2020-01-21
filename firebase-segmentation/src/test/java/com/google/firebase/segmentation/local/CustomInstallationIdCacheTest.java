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

package com.google.firebase.segmentation.local;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Instrumented tests for {@link CustomInstallationIdCache} */
@RunWith(RobolectricTestRunner.class)
public class CustomInstallationIdCacheTest {

  private FirebaseApp firebaseApp0;
  private FirebaseApp firebaseApp1;
  private CustomInstallationIdCache cache0;
  private CustomInstallationIdCache cache1;

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
    cache0 = new CustomInstallationIdCache(firebaseApp0);
    cache1 = new CustomInstallationIdCache(firebaseApp1);
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
            CustomInstallationIdCacheEntryValue.create(
                "123456", "cAAAAAAAAAA", CustomInstallationIdCache.CacheStatus.PENDING_UPDATE)));
    CustomInstallationIdCacheEntryValue entryValue = cache0.readCacheEntryValue();
    assertThat(entryValue.getCustomInstallationId()).isEqualTo("123456");
    assertThat(entryValue.getFirebaseInstanceId()).isEqualTo("cAAAAAAAAAA");
    assertThat(entryValue.getCacheStatus())
        .isEqualTo(CustomInstallationIdCache.CacheStatus.PENDING_UPDATE);
    assertNull(cache1.readCacheEntryValue());

    assertTrue(
        cache0.insertOrUpdateCacheEntry(
            CustomInstallationIdCacheEntryValue.create(
                "123456", "cAAAAAAAAAA", CustomInstallationIdCache.CacheStatus.SYNCED)));
    entryValue = cache0.readCacheEntryValue();
    assertThat(entryValue.getCustomInstallationId()).isEqualTo("123456");
    assertThat(entryValue.getFirebaseInstanceId()).isEqualTo("cAAAAAAAAAA");
    assertThat(entryValue.getCacheStatus()).isEqualTo(CustomInstallationIdCache.CacheStatus.SYNCED);
  }
}
