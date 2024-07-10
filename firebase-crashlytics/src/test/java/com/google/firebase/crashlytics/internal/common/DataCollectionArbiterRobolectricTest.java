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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.SharedPreferences;
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

  private SharedPreferences sharedPreferences;

  private static final String FIREBASE_CRASHLYTICS_COLLECTION_ENABLED =
          "firebase_crashlytics_collection_enabled";

  private DataCollectionArbiter arbiter;

  @Before
  public void setUp() {
    testContext = getApplicationContext();
    firebaseApp = mock(FirebaseApp.class);
    when(firebaseApp.getApplicationContext()).thenReturn(testContext);

    arbiter = new DataCollectionArbiter(firebaseApp);

    sharedPreferences = CommonUtils.getSharedPrefs(testContext);
  }

  @Test
  public void testIsCrashlyticsCollectionEnabled_withSharedPreferenceValue() {
    arbiter.setCrashlyticsDataCollectionEnabled(true);
    assertTrue(arbiter.isCrashlyticsCollectionEnabled());

    arbiter.setCrashlyticsDataCollectionEnabled(false);
    assertFalse(arbiter.isCrashlyticsCollectionEnabled());
  }

  @Test
  public void testIsCrashlyticsCollectionEnabled_withManifestValue() {
    //Disable preference
    removeSharedPreferenceData();

    editManifestApplicationMetadata(testContext)
            .putBoolean(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED,true);
    assertTrue(arbiter.isCrashlyticsCollectionEnabled());

    editManifestApplicationMetadata(testContext)
            .putBoolean(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED,false);
    assertFalse(arbiter.isCrashlyticsCollectionEnabled());
  }

  @Test
  public void testIsCrashlyticsCollectionEnabled_withFirebaseDefaultValue() {
    //Disable preference
    removeSharedPreferenceData();
    //Disable manifest
    editManifestApplicationMetadata(testContext)
            .remove(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED);

    when(firebaseApp.isDataCollectionDefaultEnabled()).thenReturn(true);
    assertTrue(arbiter.isCrashlyticsCollectionEnabled());

    when(firebaseApp.isDataCollectionDefaultEnabled()).thenReturn(false);
    assertFalse(arbiter.isCrashlyticsCollectionEnabled());
  }

  private void removeSharedPreferenceData() {
    SharedPreferences.Editor editor = sharedPreferences.edit();
    if(sharedPreferences.contains(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED)) {
      editor.remove(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED);
      editor.apply();
    }
  }

  private Bundle editManifestApplicationMetadata(Context context) {
    return shadowOf(context.getPackageManager())
            .getInternalMutablePackageInfo(context.getPackageName())
            .applicationInfo
            .metaData;
  }
}
