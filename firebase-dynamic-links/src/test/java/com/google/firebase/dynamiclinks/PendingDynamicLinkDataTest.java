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

package com.google.firebase.dynamiclinks;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import com.google.firebase.dynamiclinks.internal.DynamicLinkData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PendingDynamicLinkDataTest {

  private static final String DEEP_LINK = "http://deeplink";
  private static final String DYNAMIC_LINK = "http://test.com/dynamic?link=" + DEEP_LINK;
  private static final int MINIMUM_VERSION = 1234;
  private static final long CLICK_TIMESTAMP = 54321L;
  private static final String PACKAGE_NAME = "com.google.test.package.name";
  private static final String MARKET_URL =
      "market://details?id=com.google.android.gm&min_version=10";
  private static final String QUERY_VALUE = "http://example.google.com";
  private static final String QUERY_KEY = "url";

  // Bundle keys to test extensions.
  private static final String KEY_TEST_STRING = "com.google.test.value.STRING";
  private static final String KEY_TEST_INT = "com.google.test.value.INT";
  private static final String TEST_STRING_VALUE = "testString";
  private static final int TEST_INT_VALUE = 321;

  private final PackageInfo packageInfo = new PackageInfo();
  private Uri updateAppUri;

  @Mock private Context mockContext;
  @Mock private PackageManager mockPackageManager;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    packageInfo.versionCode = 0; // default to zero, allow each test to set as needed.
    when(mockContext.getPackageManager()).thenReturn(mockPackageManager);
    when(mockContext.getApplicationContext()).thenReturn(mockContext);
    when(mockContext.getPackageName()).thenReturn(PACKAGE_NAME);
    when(mockPackageManager.getPackageInfo(any(String.class), anyInt())).thenReturn(packageInfo);
    // Create uri with properly escaped query params.
    updateAppUri =
        Uri.parse(MARKET_URL).buildUpon().appendQueryParameter(QUERY_KEY, QUERY_VALUE).build();
  }

  @Test
  public void testCreateDynamicLink() {
    PendingDynamicLinkData pendingDynamicLinkData =
        new PendingDynamicLinkData(createDynamicLinkData());
    assertEquals(pendingDynamicLinkData.getLink(), Uri.parse(DEEP_LINK));
    assertEquals(MINIMUM_VERSION, pendingDynamicLinkData.getMinimumAppVersion());
    assertEquals(CLICK_TIMESTAMP, pendingDynamicLinkData.getClickTimestamp());
  }

  @Test
  public void testCreateDynamicLink_NullData() {
    PendingDynamicLinkData pendingDynamicLinkData = new PendingDynamicLinkData(null);
    assertNull(pendingDynamicLinkData.getLink());
    assertEquals(0L, pendingDynamicLinkData.getClickTimestamp());
    assertEquals(0, pendingDynamicLinkData.getMinimumAppVersion());
    assertNull(pendingDynamicLinkData.getRedirectUrl());
    assertEquals(0, pendingDynamicLinkData.getExtensions().size());
  }

  @Test
  public void testCreateDynamicLink_ZeroValues() {
    PendingDynamicLinkData pendingDynamicLinkData =
        new PendingDynamicLinkData(createDataLinkDataZeroValues());
    assertNotNull(pendingDynamicLinkData);
    assertNull(pendingDynamicLinkData.getLink());
    assertNull(pendingDynamicLinkData.getRedirectUrl());
    assertEquals(0, pendingDynamicLinkData.getMinimumAppVersion());
    // Click timestamp with value 0 is reset to the current time.
    assertFalse(0L == pendingDynamicLinkData.getClickTimestamp());
    assertEquals(0, pendingDynamicLinkData.getExtensions().size());
  }

  @Test
  public void testCreateDynamicLink_DirectValues() {
    PendingDynamicLinkData pendingDynamicLinkData =
        new PendingDynamicLinkData(DEEP_LINK, MINIMUM_VERSION, CLICK_TIMESTAMP, updateAppUri);
    assertEquals(pendingDynamicLinkData.getLink(), Uri.parse(DEEP_LINK));
    assertEquals(MINIMUM_VERSION, pendingDynamicLinkData.getMinimumAppVersion());
    assertEquals(CLICK_TIMESTAMP, pendingDynamicLinkData.getClickTimestamp());
    assertEquals(updateAppUri, pendingDynamicLinkData.getRedirectUrl());
    assertEquals(0, pendingDynamicLinkData.getExtensions().size());
  }

  @Test
  public void testCreateDynamicLink_ExtensionValues() {
    PendingDynamicLinkData pendingDynamicLinkData =
        new PendingDynamicLinkData(createDynamicLinkDataExtensions());
    assertEquals(pendingDynamicLinkData.getLink(), Uri.parse(DEEP_LINK));
    assertEquals(MINIMUM_VERSION, pendingDynamicLinkData.getMinimumAppVersion());
    assertEquals(CLICK_TIMESTAMP, pendingDynamicLinkData.getClickTimestamp());
    Bundle bundle = pendingDynamicLinkData.getExtensions();
    assertNotNull(bundle);
    assertEquals(TEST_STRING_VALUE, bundle.getString(KEY_TEST_STRING));
    assertEquals(TEST_INT_VALUE, bundle.getInt(KEY_TEST_INT));
  }

  @Test
  public void testGetUpdateAppIntent_UpdateRequired_CurrentVersionZero() {
    PendingDynamicLinkData pendingDynamicLinkData =
        new PendingDynamicLinkData(createDynamicLinkDataWithMinVersion(MINIMUM_VERSION));
    packageInfo.versionCode = 0;
    Intent intent = pendingDynamicLinkData.getUpdateAppIntent(mockContext);
    assertNotNull(intent);
    assertEquals(intent.getData(), updateAppUri);
  }

  @Test
  public void testGetUpdateAppIntent_UpdateRequired_MinVersionHigher() {
    PendingDynamicLinkData pendingDynamicLinkData =
        new PendingDynamicLinkData(createDynamicLinkDataWithMinVersion(MINIMUM_VERSION));
    packageInfo.versionCode = MINIMUM_VERSION - 1;
    Intent intent = pendingDynamicLinkData.getUpdateAppIntent(mockContext);
    assertNotNull(intent);
    assertEquals(intent.getData(), updateAppUri);
  }

  @Test
  public void testGetUpdateAppIntent_NoUpdate_ZeroMinVersion() {
    PendingDynamicLinkData pendingDynamicLinkData =
        new PendingDynamicLinkData(createDynamicLinkDataWithMinVersion(0));
    packageInfo.versionCode = 0;
    Intent intent = pendingDynamicLinkData.getUpdateAppIntent(mockContext);
    assertNull(intent);
  }

  @Test
  public void testGetUpdateAppIntent_NoUpdate_SameVersion() {
    PendingDynamicLinkData pendingDynamicLinkData =
        new PendingDynamicLinkData(createDynamicLinkDataWithMinVersion(MINIMUM_VERSION));
    packageInfo.versionCode = MINIMUM_VERSION;
    Intent intent = pendingDynamicLinkData.getUpdateAppIntent(mockContext);
    assertNull(intent);
  }

  @Test
  public void testGetUpdateAppIntent_NoUpdate_MinVersionLower() {
    PendingDynamicLinkData pendingDynamicLinkData =
        new PendingDynamicLinkData(createDynamicLinkDataWithMinVersion(MINIMUM_VERSION));
    packageInfo.versionCode = MINIMUM_VERSION + 1;
    Intent intent = pendingDynamicLinkData.getUpdateAppIntent(mockContext);
    assertNull(intent);
  }

  @Test
  public void testGetUpdateAppIntent_NameNotFoundException() throws Exception {
    PendingDynamicLinkData pendingDynamicLinkData =
        new PendingDynamicLinkData(createDynamicLinkDataWithMinVersion(MINIMUM_VERSION));
    packageInfo.versionCode = 0;
    when(mockPackageManager.getPackageInfo(eq(PACKAGE_NAME), anyInt()))
        .thenThrow(new PackageManager.NameNotFoundException());
    Intent intent = pendingDynamicLinkData.getUpdateAppIntent(mockContext);
    assertNull(intent);
  }

  private DynamicLinkData createDynamicLinkData() {
    return new DynamicLinkData(
        DYNAMIC_LINK, DEEP_LINK, MINIMUM_VERSION, CLICK_TIMESTAMP, null, updateAppUri);
  }

  private DynamicLinkData createDynamicLinkDataWithMinVersion(int minVersion) {
    return new DynamicLinkData(
        DYNAMIC_LINK, DEEP_LINK, minVersion, CLICK_TIMESTAMP, null, updateAppUri);
  }

  private DynamicLinkData createDynamicLinkDataExtensions() {
    Bundle bundle = new Bundle();
    bundle.putString(KEY_TEST_STRING, TEST_STRING_VALUE);
    bundle.putInt(KEY_TEST_INT, TEST_INT_VALUE);
    return new DynamicLinkData(
        DYNAMIC_LINK, DEEP_LINK, MINIMUM_VERSION, CLICK_TIMESTAMP, bundle, updateAppUri);
  }

  private DynamicLinkData createDataLinkDataZeroValues() {
    return new DynamicLinkData(null, null, 0, 0L, null, null);
  }
}
