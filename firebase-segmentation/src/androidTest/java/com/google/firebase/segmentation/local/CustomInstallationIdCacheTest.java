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
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for {@link CustomInstallationIdCache}
 */
@RunWith(AndroidJUnit4.class)
public class CustomInstallationIdCacheTest {

  private FirebaseApp firebaseApp0;
  private FirebaseApp firebaseApp1;
  private CustomInstallationIdCache cache;

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
    cache = CustomInstallationIdCache.getInstance();
  }

  @After
  public void cleanUp() throws Exception {
    Tasks.await(cache.clearAll());
  }

  @Test
  public void testReadCacheEntry_Null() {
    assertNull(cache.readCacheEntryValue(firebaseApp0));
    assertNull(cache.readCacheEntryValue(firebaseApp1));
  }

  @Test
  public void testUpdateAndReadCacheEntry() throws Exception {
    assertTrue(
        Tasks.await(
            cache.insertOrUpdateCacheEntry(
                firebaseApp0,
                CustomInstallationIdCacheEntryValue.create(
                    "123456", "cAAAAAAAAAA", CustomInstallationIdCache.CacheStatus.PENDING))));
    CustomInstallationIdCacheEntryValue entryValue = cache.readCacheEntryValue(firebaseApp0);
    assertThat(entryValue.getCustomInstallationId()).isEqualTo("123456");
    assertThat(entryValue.getFirebaseInstanceId()).isEqualTo("cAAAAAAAAAA");
    assertThat(entryValue.getCacheStatus())
        .isEqualTo(CustomInstallationIdCache.CacheStatus.PENDING);
    assertNull(cache.readCacheEntryValue(firebaseApp1));

    assertTrue(
        Tasks.await(
            cache.insertOrUpdateCacheEntry(
                firebaseApp0,
                CustomInstallationIdCacheEntryValue.create(
                    "123456", "cAAAAAAAAAA", CustomInstallationIdCache.CacheStatus.SYNCED))));
    entryValue = cache.readCacheEntryValue(firebaseApp0);
    assertThat(entryValue.getCustomInstallationId()).isEqualTo("123456");
    assertThat(entryValue.getFirebaseInstanceId()).isEqualTo("cAAAAAAAAAA");
    assertThat(entryValue.getCacheStatus()).isEqualTo(CustomInstallationIdCache.CacheStatus.SYNCED);
  }
}
