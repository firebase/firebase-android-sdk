// Copyright 2020 Google LLC
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.FirebaseApp;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import java.lang.reflect.Method;

public class DataCollectionArbiterTest extends CrashlyticsTestCase {

  final String PREFS_NAME = CommonUtils.SHARED_PREFS_NAME;

  public void testSetCrashlyticsDataCollectionEnabled() throws Exception {
    Context mockContext = mock(Context.class);
    FirebaseApp app = mock(FirebaseApp.class);
    when(app.getApplicationContext()).thenReturn(mockContext);
    when(app.isDataCollectionDefaultEnabled()).thenReturn(true);
    final String PREFS_NAME = CommonUtils.SHARED_PREFS_NAME;
    final String PREFS_KEY = "firebase_crashlytics_collection_enabled";
    SharedPreferences.Editor mockEditor = mock(SharedPreferences.Editor.class);
    when(mockEditor.putBoolean(PREFS_KEY, true)).thenReturn(mockEditor);
    when(mockEditor.commit()).thenReturn(true);
    SharedPreferences mockPrefs = mock(SharedPreferences.class);
    when(mockPrefs.edit()).thenReturn(mockEditor);
    when(mockContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(mockPrefs);

    DataCollectionArbiter arbiter = new DataCollectionArbiter(app);

    assertTrue(arbiter.isAutomaticDataCollectionEnabled());
    arbiter.setCrashlyticsDataCollectionEnabled(Boolean.FALSE);
    assertFalse(arbiter.isAutomaticDataCollectionEnabled());
    arbiter.setCrashlyticsDataCollectionEnabled(Boolean.TRUE);
    assertTrue(arbiter.isAutomaticDataCollectionEnabled());
    arbiter.setCrashlyticsDataCollectionEnabled(null);
    assertTrue(arbiter.isAutomaticDataCollectionEnabled());
  }

  public void testSetCrashlyticsDataCollectionEnabled_reflection() throws Exception {
    // This test exists because the Crashlytics Unity Plugin uses reflection to access
    // DataCollectionArbiter#setCrashlyticsDataCollection(Boolean), which is not part of the public
    // API. If this test throws a NoSuchMethodException, the method signature has changed. Before
    // updating this test, update crashlytics_android.cc in the Unity repo to match the new method
    // signature.
    Method m =
        DataCollectionArbiter.class.getMethod("setCrashlyticsDataCollectionEnabled", Boolean.class);
    assertNotNull(m);
  }
}
