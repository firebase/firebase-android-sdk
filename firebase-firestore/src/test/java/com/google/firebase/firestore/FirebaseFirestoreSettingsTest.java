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

package com.google.firebase.firestore;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FirebaseFirestoreSettingsTest {

  @Test
  public void builderWithNoModificationsShouldProduceDefaultSettings() {
    FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder().build();
    assertEquals(settings.getHost(), "firestore.googleapis.com");
    assertEquals(settings.isSslEnabled(), true);
    assertEquals(settings.isPersistenceEnabled(), true);
    assertEquals(settings.getCacheSizeBytes(), 104857600L);
  }

  @Test
  public void builderWithAllValuesCustomizedShouldProduceSettingsWithThoseCustomValues() {
    FirebaseFirestoreSettings settings =
        new FirebaseFirestoreSettings.Builder()
            .setHost("a.b.c")
            .setSslEnabled(false)
            .setPersistenceEnabled(false)
            .setCacheSizeBytes(2000000L)
            .build();
    assertEquals(settings.getHost(), "a.b.c");
    assertEquals(settings.isSslEnabled(), false);
    assertEquals(settings.isPersistenceEnabled(), false);
    assertEquals(settings.getCacheSizeBytes(), 2000000L);
  }

  @Test
  public void builderConstructorShouldCopyAllValuesFromTheGivenSettings() {
    FirebaseFirestoreSettings settings1 =
        new FirebaseFirestoreSettings.Builder()
            .setHost("a.b.c")
            .setSslEnabled(false)
            .setPersistenceEnabled(false)
            .setCacheSizeBytes(2000000L)
            .build();
    FirebaseFirestoreSettings settings2 = new FirebaseFirestoreSettings.Builder(settings1).build();
    assertEquals(settings2.getHost(), "a.b.c");
    assertEquals(settings2.isSslEnabled(), false);
    assertEquals(settings2.isPersistenceEnabled(), false);
    assertEquals(settings2.getCacheSizeBytes(), 2000000L);
  }
}
