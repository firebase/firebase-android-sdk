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

package com.google.firebase.dynamiclinks.internal;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DynamicLinkDataTest {
  private static final String DEEP_LINK = "http://deeplink";
  private static final String DYNAMIC_LINK = "http://test.com/dynamic?link=" + DEEP_LINK;
  private static final int MINIMUM_VERSION = 1234;
  private static final long CLICK_TIMESTAMP = 54321L;
  private static final String KEY_TEST_STRING = "com.google.test.value.STRING";
  private static final String KEY_TEST_INT = "com.google.test.value.INT";
  private static final String KEY_TEST_EXTRA = "com.google.test.value.EXTRA";
  private static final String TEST_STRING_VALUE = "testString";
  private static final int TEST_INT_VALUE = 321;
  private Uri updateAppUri;

  @Before
  public void setUp() {
    // Create uri with properly escaped query params.
    updateAppUri =
        Uri.parse("market://details?id=com.google.android.gm&min_version=10")
            .buildUpon()
            .appendQueryParameter("url", "http://example.google.com")
            .build();
  }

  @Test
  public void testConstructor() {
    DynamicLinkData data = genDynamicLinkData();

    assertEquals(DYNAMIC_LINK, data.getDynamicLink());
    assertEquals(DEEP_LINK, data.getDeepLink());
    assertEquals(MINIMUM_VERSION, data.getMinVersion());
    assertEquals(CLICK_TIMESTAMP, data.getClickTimestamp());
    assertEquals(updateAppUri, data.getRedirectUrl());
  }

  @Test
  public void testConstructor_EmptyValues() {
    DynamicLinkData data = genEmptyDynamicLinkData();

    assertNull(data.getDynamicLink());
    assertNull(data.getDeepLink());
    assertEquals(0, data.getMinVersion());
    assertEquals(0L, data.getClickTimestamp());
    assertNull(data.getRedirectUrl());
  }

  @Test
  public void testSetMinVersion() {
    DynamicLinkData data = genEmptyDynamicLinkData();
    assertEquals(0, data.getMinVersion());
    data.setMinVersion(MINIMUM_VERSION);
    assertEquals(MINIMUM_VERSION, data.getMinVersion());
  }

  @Test
  public void testSetClickTimestamp() {
    DynamicLinkData data = genEmptyDynamicLinkData();
    assertEquals(0L, data.getClickTimestamp());
    data.setClickTimestamp(CLICK_TIMESTAMP);
    assertEquals(CLICK_TIMESTAMP, data.getClickTimestamp());
  }

  @Test
  public void testSetDynamicLink() {
    DynamicLinkData data = genEmptyDynamicLinkData();
    assertNull(data.getDynamicLink());
    data.setDynamicLink(DYNAMIC_LINK);
    assertEquals(DYNAMIC_LINK, data.getDynamicLink());
  }

  @Test
  public void testSetDeepLink() {
    DynamicLinkData data = genEmptyDynamicLinkData();
    assertNull(data.getDeepLink());
    data.setDeepLink(DEEP_LINK);
    assertEquals(DEEP_LINK, data.getDeepLink());
  }

  @Test
  public void testSetRedirectUrl() {
    DynamicLinkData data = genEmptyDynamicLinkData();
    assertNull(data.getRedirectUrl());
    data.setRedirectUrl(updateAppUri);
    assertEquals(updateAppUri, data.getRedirectUrl());
  }

  @Test
  public void testSetExtension() {
    DynamicLinkData data = genDynamicLinkData();
    Bundle bundle = data.getExtensionBundle();
    // Make sure data isn't present beforehand.
    assertFalse(bundle.containsKey(KEY_TEST_STRING));
    assertFalse(bundle.containsKey(KEY_TEST_INT));
    // Add extension data.
    addExtension(data);
    // Get extensions and validate.
    Bundle updatedBundle = data.getExtensionBundle();
    assertNotNull(updatedBundle);
    assertEquals(TEST_STRING_VALUE, updatedBundle.getString(KEY_TEST_STRING, null));
    assertEquals(TEST_INT_VALUE, updatedBundle.getInt(KEY_TEST_INT));
  }

  @Test
  public void testWriteToParcel() {
    DynamicLinkData writtenLinkData = addExtension(genDynamicLinkData());
    Parcel out = Parcel.obtain();
    writtenLinkData.writeToParcel(out, 0);
    out.setDataPosition(0);

    DynamicLinkData createdDynamicLink = DynamicLinkData.CREATOR.createFromParcel(out);
    assertEquals(writtenLinkData.getDynamicLink(), createdDynamicLink.getDynamicLink());
    assertEquals(writtenLinkData.getDeepLink(), createdDynamicLink.getDeepLink());
    assertEquals(writtenLinkData.getClickTimestamp(), createdDynamicLink.getClickTimestamp());
    assertEquals(writtenLinkData.getMinVersion(), createdDynamicLink.getMinVersion());
    Bundle writtenBundle = writtenLinkData.getExtensionBundle();
    Bundle createdBundle = createdDynamicLink.getExtensionBundle();
    assertEquals(writtenBundle.size(), createdBundle.size());
    Set<String> createdKeys = createdBundle.keySet();
    Set<String> writtenKeys = writtenBundle.keySet();
    assertTrue(createdKeys.containsAll(writtenKeys));
  }

  @Test
  public void testExtension_DifferentBundle() {
    DynamicLinkData originalDynamicLink = addExtension(genDynamicLinkData());
    DynamicLinkData extraDynamicLink = addExtension(genDynamicLinkData());
    // Add one more bundle key.
    Bundle bundle = extraDynamicLink.getExtensionBundle();
    bundle.putString(KEY_TEST_EXTRA, TEST_STRING_VALUE);
    extraDynamicLink.setExtensionData(bundle);
    Bundle originalBundle = originalDynamicLink.getExtensionBundle();
    Bundle extraBundle = extraDynamicLink.getExtensionBundle();
    Set<String> extraKeys = extraBundle.keySet();
    Set<String> originalKeys = originalBundle.keySet();
    assertTrue(extraKeys.containsAll(originalKeys));
    // Identify that the bundles are different, missing keys.
    assertFalse(originalKeys.containsAll(extraKeys));
  }

  private DynamicLinkData genDynamicLinkData() {
    return new DynamicLinkData(
        DYNAMIC_LINK, DEEP_LINK, MINIMUM_VERSION, CLICK_TIMESTAMP, null, updateAppUri);
  }

  private DynamicLinkData genEmptyDynamicLinkData() {
    return new DynamicLinkData(null, null, 0, 0L, null, null);
  }

  private DynamicLinkData addExtension(DynamicLinkData dynamicLinkData) {
    Bundle bundle = dynamicLinkData.getExtensionBundle();
    bundle.putString(KEY_TEST_STRING, TEST_STRING_VALUE);
    bundle.putInt(KEY_TEST_INT, TEST_INT_VALUE);
    dynamicLinkData.setExtensionData(bundle);
    return dynamicLinkData;
  }
}
