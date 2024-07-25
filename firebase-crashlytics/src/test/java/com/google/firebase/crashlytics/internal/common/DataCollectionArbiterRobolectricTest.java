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

package com.google.firebase.crashlytics.internal.common;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Bundle;
import com.google.firebase.FirebaseApp;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DataCollectionArbiterRobolectricTest {

  private Context testContext;
  private FirebaseApp firebaseApp;

  private static final String FIREBASE_CRASHLYTICS_COLLECTION_ENABLED =
      "firebase_crashlytics_collection_enabled";

  @Before
  public void setUp() {
    testContext = getApplicationContext();
    firebaseApp = mock(FirebaseApp.class);
    when(firebaseApp.getApplicationContext()).thenReturn(testContext);
  }

  private DataCollectionArbiter getDataCollectionArbiter(FirebaseApp app) {
    return new DataCollectionArbiter(app);
  }

  @Test
  public void testSetCrashlyticsDataCollectionEnabled_overridesOtherSettings() {
    // Ensure that Manifest metadata is set to false.
    editManifestApplicationMetadata(testContext)
        .putBoolean(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED, false);

    // Mock FirebaseApp to return default data collection as false.
    when(firebaseApp.isDataCollectionDefaultEnabled()).thenReturn(false);

    DataCollectionArbiter arbiter = getDataCollectionArbiter(firebaseApp);

    // Setting explicitly to true should override both manifest and default settings.
    arbiter.setCrashlyticsDataCollectionEnabled(true);
    assertThat(arbiter.isAutomaticDataCollectionEnabled()).isTrue();

    // Setting explicitly to false should also override the previous value
    arbiter.setCrashlyticsDataCollectionEnabled(false);
    assertThat(arbiter.isAutomaticDataCollectionEnabled()).isFalse();

    arbiter.setCrashlyticsDataCollectionEnabled(null);
    // Expecting `false` result since manifest metadata value is `false`
    assertThat(arbiter.isAutomaticDataCollectionEnabled()).isFalse();
  }

  @Test
  public void testManifestMetadata_respectedWhenNoOverride() {
    editManifestApplicationMetadata(testContext)
        .putBoolean(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED, true);

    DataCollectionArbiter arbiter = getDataCollectionArbiter(firebaseApp);

    assertThat(arbiter.isAutomaticDataCollectionEnabled()).isTrue();

    editManifestApplicationMetadata(testContext)
        .putBoolean(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED, false);

    arbiter = getDataCollectionArbiter(firebaseApp);

    assertThat(arbiter.isAutomaticDataCollectionEnabled()).isFalse();
  }

  @Test
  public void testDefaultDataCollection_usedWhenNoOverrideOrManifestSetting() {
    editManifestApplicationMetadata(testContext).remove(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED);

    DataCollectionArbiter arbiter = getDataCollectionArbiter(firebaseApp);

    when(firebaseApp.isDataCollectionDefaultEnabled()).thenReturn(true);
    assertThat(arbiter.isAutomaticDataCollectionEnabled()).isTrue();

    when(firebaseApp.isDataCollectionDefaultEnabled()).thenReturn(false);
    assertThat(arbiter.isAutomaticDataCollectionEnabled()).isFalse();

    // No Test of `null` return for firebaseApp.isDataCollectionDefaultEnabled(), since it will
    // never return `null` value
  }

  private Bundle editManifestApplicationMetadata(Context context) {
    return shadowOf(context.getPackageManager())
        .getInternalMutablePackageInfo(context.getPackageName())
        .applicationInfo
        .metaData;
  }
}
