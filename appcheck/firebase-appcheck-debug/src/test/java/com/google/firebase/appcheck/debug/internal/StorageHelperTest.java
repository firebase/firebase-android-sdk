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

package com.google.firebase.appcheck.debug.internal;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link StorageHelper} */
@RunWith(RobolectricTestRunner.class)
public class StorageHelperTest {

  private static final String PERSISTENCE_KEY = "persistenceKey";
  private static final String DEBUG_SECRET = "debugSecret";

  private StorageHelper storageHelper;
  private SharedPreferences sharedPreferences;

  @Before
  public void setUp() {
    storageHelper = new StorageHelper(ApplicationProvider.getApplicationContext(), PERSISTENCE_KEY);
    sharedPreferences =
        ApplicationProvider.getApplicationContext()
            .getSharedPreferences(
                String.format(StorageHelper.PREFS_TEMPLATE, PERSISTENCE_KEY), Context.MODE_PRIVATE);
  }

  @After
  public void tearDown() {
    sharedPreferences.edit().clear().commit();
  }

  @Test
  public void testSaveDebugSecret_sharedPrefsWritten() {
    storageHelper.saveDebugSecret(DEBUG_SECRET);
    String debugSecret = sharedPreferences.getString(StorageHelper.DEBUG_SECRET_KEY, null);
    assertThat(debugSecret).isNotEmpty();
    assertThat(debugSecret).isEqualTo(DEBUG_SECRET);
  }

  @Test
  public void testSaveDebugSecret_expectEquivalentToken() {
    storageHelper.saveDebugSecret(DEBUG_SECRET);
    String debugSecret = storageHelper.retrieveDebugSecret();
    assertThat(debugSecret).isNotEmpty();
    assertThat(debugSecret).isEqualTo(DEBUG_SECRET);
  }

  @Test
  public void testRetrieveDebugSecret_defaultNull() {
    String debugSecret = storageHelper.retrieveDebugSecret();
    assertThat(debugSecret).isNull();
  }
}
