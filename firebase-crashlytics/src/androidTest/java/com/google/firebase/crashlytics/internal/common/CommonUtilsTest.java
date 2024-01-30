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

package com.google.firebase.crashlytics.internal.common;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.Log;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.Logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommonUtilsTest extends CrashlyticsTestCase {
  private static final String LEGACY_ID_VALUE = "legacy_id";
  private static final String CRASHLYTICS_ID_VALUE = "crashlytics_id";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  private static final String ABC_EXPECTED_HASH = "a9993e364706816aba3e25717850c26c9cd0d89d";

  public void testCreateInstanceIdFromNullInput() {
    assertNull(CommonUtils.createInstanceIdFrom(((String[]) null)));
  }

  public void testCreateInstanceIdFromEmptyInput() {
    assertNull(CommonUtils.createInstanceIdFrom((new String[] {})));
  }

  public void testCreateInstanceIdFromArrayWithNull() {
    assertNull(CommonUtils.createInstanceIdFrom((new String[] {null})));
  }

  public void testCreateInstanceIdFromSingleId() {
    assertEquals(
        "86f7e437faa5a7fce15d1ddcb9eaeaea377667b8",
        CommonUtils.createInstanceIdFrom((new String[] {"A"})));
  }

  public void testCreateInstanceIdFromMultipleIds() {
    assertEquals(
        ABC_EXPECTED_HASH, CommonUtils.createInstanceIdFrom((new String[] {"B", "C", "A"})));
  }

  public void testCreateInstanceIdFromMultipleIdsSorted() {
    assertEquals(
        ABC_EXPECTED_HASH, CommonUtils.createInstanceIdFrom((new String[] {"A", "B", "C"})));
  }

  public void testCreateInstanceIdFromMultipleIdsWithNull() {
    // We expect the null value to be skipped
    assertEquals(
        ABC_EXPECTED_HASH, CommonUtils.createInstanceIdFrom((new String[] {"B", null, "C", "A"})));
  }

  public void testCreateInstanceIdFromIdsWithDashes() {
    // We expect dashes to be stripped
    assertEquals(
        "39ccc6558fde373e0501040b7753dca474572fcc",
        CommonUtils.createInstanceIdFrom((new String[] {"B-3-4", "C-5-6", "A-1-2"})));
  }

  public void testPadWithZerosToMaxIntWidthNegative() {
    try {
      CommonUtils.padWithZerosToMaxIntWidth(-1);
    } catch (IllegalArgumentException e) {
      return;
    }
    fail();
  }

  public void testPadWithZerosToMaxIntWidth() {
    assertEquals("0000000000", CommonUtils.padWithZerosToMaxIntWidth(0));
    assertEquals("0000000001", CommonUtils.padWithZerosToMaxIntWidth(1));
    assertEquals("0000000100", CommonUtils.padWithZerosToMaxIntWidth(100));
    assertEquals("2147483647", CommonUtils.padWithZerosToMaxIntWidth(Integer.MAX_VALUE));
  }

  public void testSha1() {
    String source = "sha1 test";
    String result = CommonUtils.sha1(source);

    System.out.println(source + " -> " + result);
    // results taken from: http://www.movable-type.co.uk/scripts/sha1.html
    // assertEquals("42aae338eae1f985c9a2188977e11a190002afe3", result);

    source = "f1e88c49bc0509d9";
    result = CommonUtils.sha1(source);
    System.out.println(source + " -> " + result);
    assertEquals("0470a17620f98ce61700faa3d4e21e29c49ad0e1", result);
  }

  public void testGetCpuArchitectureInt() {
    final int archInt = CommonUtils.getCpuArchitectureInt();
    assertTrue(archInt < CommonUtils.Architecture.values().length);
    assertTrue(archInt >= 0);
    Log.d(Logger.TAG, "testGetArchitecture: archInt=" + archInt);
  }

  public void testGetCpuArchitecture() {
    assertNotNull(CommonUtils.Architecture.getValue());
    assertFalse(CommonUtils.Architecture.UNKNOWN.equals(CommonUtils.Architecture.getValue()));
  }

  public void testGetTotalRamInBytes() {
    final long bytes = CommonUtils.calculateTotalRamInBytes(getContext());
    // can't check complete string because emulators & devices may be different.
    assertTrue(bytes > 0);
    Log.d(Logger.TAG, "testGetTotalRam: " + bytes);
  }

  public void testIsRooted() {
    // No good way to test the alternate case,
    // just want to ensure we can complete the call without an exception here.
    final boolean isRooted = CommonUtils.isRooted();
    Log.d(Logger.TAG, "isRooted: " + isRooted + " isEmulator: " + CommonUtils.isEmulator());

    // We don't care about the actual result of isRooted, just that we didn't cause an exception
    assertTrue(true);
  }

  public void testIsDebuggerAttached() {
    // No good way to test the alternate case,
    // just want to ensure we can complete the call without an exception here.
    final boolean isDebugging = CommonUtils.isDebuggerAttached();
    Log.d(Logger.TAG, "isDebugging: " + isDebugging);
    assertFalse(isDebugging);
  }

  private boolean isBitSet(int data, int mask) {
    return (data & mask) == mask;
  }

  public void testGetDeviceState() {

    final int state = CommonUtils.getDeviceState();
    Log.d(Logger.TAG, "testGetDeviceState: state=" + state);

    if (CommonUtils.isEmulator()) {
      assertTrue(isBitSet(state, CommonUtils.DEVICE_STATE_ISSIMULATOR));
    } else {
      assertFalse(isBitSet(state, CommonUtils.DEVICE_STATE_ISSIMULATOR));
    }

    if (CommonUtils.isDebuggerAttached()) {
      assertTrue(isBitSet(state, CommonUtils.DEVICE_STATE_DEBUGGERATTACHED));
    } else {
      assertFalse(isBitSet(state, CommonUtils.DEVICE_STATE_DEBUGGERATTACHED));
    }

    if (CommonUtils.isRooted()) {
      assertTrue(isBitSet(state, CommonUtils.DEVICE_STATE_JAILBROKEN));
    } else {
      assertFalse(isBitSet(state, CommonUtils.DEVICE_STATE_JAILBROKEN));
    }

    // The following are not currently implemented in Util class.

    final boolean isBetaOs = false;
    if (isBetaOs) {
      assertTrue(isBitSet(state, CommonUtils.DEVICE_STATE_BETAOS));
    } else {
      assertFalse(isBitSet(state, CommonUtils.DEVICE_STATE_BETAOS));
    }

    final boolean hasCompromisedLibraries = false;
    if (hasCompromisedLibraries) {
      assertTrue(isBitSet(state, CommonUtils.DEVICE_STATE_COMPROMISEDLIBRARIES));
    } else {
      assertFalse(isBitSet(state, CommonUtils.DEVICE_STATE_COMPROMISEDLIBRARIES));
    }

    final boolean isVendorInternal = false;
    if (isVendorInternal) {
      assertTrue(isBitSet(state, CommonUtils.DEVICE_STATE_VENDORINTERNAL));
    } else {
      assertFalse(isBitSet(state, CommonUtils.DEVICE_STATE_VENDORINTERNAL));
    }
  }

  public void testCheckPermission_hasPermission() {
    assertTrue(CommonUtils.checkPermission(mockPermissionContext(true), "random.perm"));
  }

  public void testCheckPermission_noPermission() {
    assertFalse(CommonUtils.checkPermission(mockPermissionContext(false), "random.perm"));
  }

  public void testCanTryConnection_withoutPermission() {
    assertTrue(CommonUtils.canTryConnection(mockPermissionContext(false)));
  }

  public void testResolveMappingFileId_legacyId() throws Exception {
    assertBuildId(
        LEGACY_ID_VALUE,
        new HashMap<String, String>() {
          {
            put(CommonUtils.LEGACY_MAPPING_FILE_ID_RESOURCE_NAME, LEGACY_ID_VALUE);
          }
        });
  }

  public void testResolveBuildId_crashlyticsId() throws Exception {
    assertBuildId(
        CRASHLYTICS_ID_VALUE,
        new HashMap<String, String>() {
          {
            put(CommonUtils.MAPPING_FILE_ID_RESOURCE_NAME, CRASHLYTICS_ID_VALUE);
          }
        });
  }

  public void testResolveBuildId_bothIds() throws Exception {
    assertBuildId(
        CRASHLYTICS_ID_VALUE,
        new HashMap<String, String>() {
          {
            put(CommonUtils.LEGACY_MAPPING_FILE_ID_RESOURCE_NAME, LEGACY_ID_VALUE);
            put(CommonUtils.MAPPING_FILE_ID_RESOURCE_NAME, CRASHLYTICS_ID_VALUE);
          }
        });
  }

  public void testResolveNativeBuildIds_missing_returnsEmptyList() {
    assertNativeBuildIds(new ArrayList<BuildIdInfo>(), new HashMap<String, List<String>>());
  }

  public void testResolveNativeBuildIds_missingResourceArray_returnsEmptyList() {
    assertNativeBuildIds(
        new ArrayList<BuildIdInfo>(),
        new HashMap<String, List<String>>() {
          {
            put(
                CommonUtils.BUILD_IDS_LIB_NAMES_RESOURCE_NAME,
                new ArrayList<String>(Arrays.asList("lib.so")));
            put(
                CommonUtils.BUILD_IDS_BUILD_ID_RESOURCE_NAME,
                new ArrayList<String>(Arrays.asList("aabb")));
          }
        });
  }

  public void testResolveNativeBuildIds_returnsSingleLibrary() {
    assertNativeBuildIds(
        new ArrayList<BuildIdInfo>(Arrays.asList(new BuildIdInfo("lib.so", "x86", "aabb"))),
        new HashMap<String, List<String>>() {
          {
            put(
                CommonUtils.BUILD_IDS_LIB_NAMES_RESOURCE_NAME,
                new ArrayList<String>(Arrays.asList("lib.so")));
            put(
                CommonUtils.BUILD_IDS_ARCH_RESOURCE_NAME,
                new ArrayList<String>(Arrays.asList("x86")));
            put(
                CommonUtils.BUILD_IDS_BUILD_ID_RESOURCE_NAME,
                new ArrayList<String>(Arrays.asList("aabb")));
          }
        });
  }

  public void testResolveNativeBuildIds_returnsMultipleLibrary() {
    assertNativeBuildIds(
        new ArrayList<BuildIdInfo>(
            Arrays.asList(
                new BuildIdInfo("lib.so", "x86", "aabb"),
                new BuildIdInfo("lib.so", "x86_64", "bbaa"))),
        new HashMap<String, List<String>>() {
          {
            put(
                CommonUtils.BUILD_IDS_LIB_NAMES_RESOURCE_NAME,
                new ArrayList<String>(Arrays.asList("lib.so", "lib.so")));
            put(
                CommonUtils.BUILD_IDS_ARCH_RESOURCE_NAME,
                new ArrayList<String>(Arrays.asList("x86", "x86_64")));
            put(
                CommonUtils.BUILD_IDS_BUILD_ID_RESOURCE_NAME,
                new ArrayList<String>(Arrays.asList("aabb", "bbaa")));
          }
        });
  }

  public void testResolveNativeBuildIds_mismatchedArray_returnsEmptyList() {
    assertNativeBuildIds(
        new ArrayList<BuildIdInfo>(),
        new HashMap<String, List<String>>() {
          {
            put(
                CommonUtils.BUILD_IDS_LIB_NAMES_RESOURCE_NAME,
                new ArrayList<String>(Arrays.asList("lib.so", "lib.so")));
            put(
                CommonUtils.BUILD_IDS_ARCH_RESOURCE_NAME,
                new ArrayList<String>(Arrays.asList("x86")));
            put(
                CommonUtils.BUILD_IDS_BUILD_ID_RESOURCE_NAME,
                new ArrayList<String>(Arrays.asList("aabb", "baab")));
          }
        });
  }

  private void assertBuildId(String expectedValue, Map<String, String> buildIds) {
    final Context mockContext = mock(Context.class);
    final Context mockAppContext = mock(Context.class);
    final Resources mockResources = mock(Resources.class);

    final String packageName = "package.name";
    final ApplicationInfo info = new ApplicationInfo();
    info.icon = 0;

    when(mockContext.getResources()).thenReturn(mockResources);
    when(mockContext.getApplicationContext()).thenReturn(mockAppContext);

    when(mockAppContext.getApplicationInfo()).thenReturn(info);
    when(mockContext.getPackageName()).thenReturn(packageName);

    int id = -1;
    when(mockResources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(++id);
    for (String buildIdKey : buildIds.keySet()) {
      when(mockResources.getIdentifier(buildIdKey, "string", packageName)).thenReturn(++id);
      when(mockResources.getString(eq(id))).thenReturn(buildIds.get(buildIdKey));
    }

    assertEquals(expectedValue, CommonUtils.getMappingFileId(mockContext));
  }

  private void assertNativeBuildIds(
      ArrayList<BuildIdInfo> expected, Map<String, List<String>> nativeBuildIds) {
    final Context mockContext = mock(Context.class);
    final Context mockAppContext = mock(Context.class);
    final Resources mockResources = mock(Resources.class);

    final String packageName = "package.name";
    final ApplicationInfo info = new ApplicationInfo();
    info.icon = 0;

    when(mockContext.getResources()).thenReturn(mockResources);
    when(mockContext.getApplicationContext()).thenReturn(mockAppContext);

    when(mockAppContext.getApplicationInfo()).thenReturn(info);
    when(mockContext.getPackageName()).thenReturn(packageName);

    int id = -1;
    when(mockResources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(++id);
    for (String buildIdKey : nativeBuildIds.keySet()) {
      when(mockResources.getIdentifier(buildIdKey, "array", packageName)).thenReturn(++id);
      when(mockResources.getStringArray(eq(id)))
          .thenReturn(nativeBuildIds.get(buildIdKey).toArray(new String[] {}));
    }
    List<BuildIdInfo> output = CommonUtils.getBuildIdInfo(mockContext);
    assertEquals(output.size(), expected.size());
    for (int i = 0; i < expected.size(); i++) {
      BuildIdInfo expectedBuildIdInfo = expected.get(i);
      BuildIdInfo outputBuildIdInfo = output.get(i);
      assertEquals(expectedBuildIdInfo.getLibraryName(), outputBuildIdInfo.getLibraryName());
      assertEquals(expectedBuildIdInfo.getArch(), outputBuildIdInfo.getArch());
      assertEquals(expectedBuildIdInfo.getBuildId(), outputBuildIdInfo.getBuildId());
    }
  }

  private Context mockPermissionContext(boolean isGranted) {
    Context mockContext = mock(Context.class);
    when(mockContext.checkCallingOrSelfPermission(anyString()))
        .thenReturn(
            isGranted ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED);
    return mockContext;
  }
}
