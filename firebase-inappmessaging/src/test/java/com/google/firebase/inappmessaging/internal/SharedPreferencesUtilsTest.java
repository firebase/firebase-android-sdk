// Copyright 2018 Google LLC
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

package com.google.firebase.inappmessaging.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import com.google.firebase.FirebaseApp;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SharedPreferencesUtilsTest {

  private final String packageName = "package.name";
  private final String TEST_PREFERENCE = "TEST_PREF";
  private final String TEST_MANIFEST_PREFERENCE = "TEST_MANIFEST_PREF";

  @Mock private SharedPreferences sharedPreferences;
  @Mock private Application application;
  @Mock private FirebaseApp firebaseApp;
  @Mock private PackageManager packageManager;
  @Mock private SharedPreferences.Editor editor;

  private SharedPreferencesUtils sharedPreferencesUtils;

  // This is needed to avoid the 'Stub!' runtime exception that happens when you try to instantiate
  // a new Bundle, because roboelectric tests run with mocks
  private static Bundle createNewBundle() {
    return new Bundle();
  }

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(firebaseApp.getApplicationContext()).thenReturn(application);
    when(application.getPackageName()).thenReturn(packageName);
    when(application.getSharedPreferences(
            SharedPreferencesUtils.PREFERENCES_PACKAGE_NAME, Context.MODE_PRIVATE))
        .thenReturn(sharedPreferences);
    when(application.getPackageManager()).thenReturn(packageManager);
    when(sharedPreferences.edit()).thenReturn(editor);

    sharedPreferencesUtils = new SharedPreferencesUtils(firebaseApp);
  }

  @Test
  public void getAndSetBooleanPreference_correctlySetsPreference() throws Exception {
    sharedPreferencesUtils.getAndSetBooleanPreference(TEST_PREFERENCE, false);
    verify(editor).putBoolean(TEST_PREFERENCE, false);
    verify(editor).apply();
  }

  @Test
  public void setBooleanPreference_correctlySetsPreference() throws Exception {
    sharedPreferencesUtils.setBooleanPreference(TEST_PREFERENCE, false);
    verify(editor).putBoolean(TEST_PREFERENCE, false);
    verify(editor).apply();
  }

  @Test
  public void getAndSetBooleanPreference_correctlyGetsPreference() throws Exception {
    when(sharedPreferences.contains(TEST_PREFERENCE)).thenReturn(true);
    when(sharedPreferences.getBoolean(TEST_PREFERENCE, true)).thenReturn(false);

    // Here we default to true but we know that the mock will return false.
    assertThat(sharedPreferencesUtils.getAndSetBooleanPreference(TEST_PREFERENCE, true)).isFalse();

    verify(sharedPreferences).contains(TEST_PREFERENCE);
    verify(sharedPreferences).getBoolean(TEST_PREFERENCE, true);
  }

  @Test
  public void getAndSetBooleanPreference_correctlyGetsPreferenceFromDefault() throws Exception {
    when(sharedPreferences.contains(TEST_PREFERENCE)).thenReturn(false);

    // Here we expect the default to be returned.
    assertThat(sharedPreferencesUtils.getAndSetBooleanPreference(TEST_PREFERENCE, true)).isTrue();

    verify(sharedPreferences).contains(TEST_PREFERENCE);
    verify(editor).putBoolean(TEST_PREFERENCE, true);
    verify(editor).apply();
  }

  @Test
  public void getAndSetBooleanPreference_correctlyUsesDefaultWhenAllElseFails() throws Exception {
    Bundle metaData = createNewBundle();
    ApplicationInfo appInfo = new ApplicationInfo();
    appInfo.metaData = metaData;

    when(packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA))
        .thenReturn(appInfo);
    when(sharedPreferences.contains(any())).thenReturn(false);

    assertThat(sharedPreferencesUtils.getAndSetBooleanPreference(TEST_PREFERENCE, false)).isFalse();

    verify(editor).putBoolean(TEST_PREFERENCE, false);
    verify(editor).apply();
  }

  @Test
  public void getBooleanPreference_correctlyGetsPreference() throws Exception {
    when(sharedPreferences.contains(TEST_PREFERENCE)).thenReturn(true);
    when(sharedPreferences.getBoolean(TEST_PREFERENCE, true)).thenReturn(false);

    // Here we default to true but we know that the mock will return false.
    assertThat(sharedPreferencesUtils.getAndSetBooleanPreference(TEST_PREFERENCE, true)).isFalse();

    verify(sharedPreferences).contains(TEST_PREFERENCE);
    verify(sharedPreferences).getBoolean(TEST_PREFERENCE, true);
  }

  @Test
  public void getBooleanPreference_correctlyGetsPreferenceFromDefault() throws Exception {
    when(sharedPreferences.contains(TEST_PREFERENCE)).thenReturn(false);

    // Here we expect the default to be returned.
    assertThat(sharedPreferencesUtils.getAndSetBooleanPreference(TEST_PREFERENCE, true)).isTrue();
    // Shouldnt have motif

    verify(sharedPreferences).contains(TEST_PREFERENCE);
  }

  @Test
  public void getBooleanPreference_correctlyUsesDefaultWhenAllElseFails() throws Exception {
    Bundle metaData = createNewBundle();
    ApplicationInfo appInfo = new ApplicationInfo();
    appInfo.metaData = metaData;

    when(packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA))
        .thenReturn(appInfo);
    when(sharedPreferences.contains(any())).thenReturn(false);

    assertThat(sharedPreferencesUtils.getAndSetBooleanPreference(TEST_PREFERENCE, false)).isFalse();

    verify(editor).putBoolean(TEST_PREFERENCE, false);
    verify(editor).apply();
  }

  @Test
  public void getBooleanPreference_correctlyRemovesPreference() throws Exception {
    sharedPreferencesUtils.setBooleanPreference(TEST_PREFERENCE, false);
    verify(editor).putBoolean(TEST_PREFERENCE, false);
    sharedPreferencesUtils.clearPreference(TEST_PREFERENCE);
    verify(editor).remove(TEST_PREFERENCE);
    verify(editor, times(2)).apply();
  }
}
