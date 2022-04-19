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

package com.google.firebase.crashlytics.internal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;

public class DevelopmentPlatformProviderTest extends CrashlyticsTestCase {
  private static final String PACKAGE_NAME = "package.name";
  private static final String UNITY_PLATFORM = "Unity";
  private static final String UNITY_VERSION = "2.0.0";
  private static final String FLUTTER_PLATFORM = "Flutter";

  public void testDevelopmentPlatformInfo_withUnity_returnsPlatformAndVersion() throws Exception {
    Context context = createMockContext(/*withUnityResource=*/ true);

    assertTrue(DevelopmentPlatformProvider.isUnity(context));

    DevelopmentPlatformProvider provider = new DevelopmentPlatformProvider(context);

    assertEquals(UNITY_PLATFORM, provider.getDevelopmentPlatform());
    assertEquals(UNITY_VERSION, provider.getDevelopmentPlatformVersion());
  }

  public void testDevelopmentPlatformInfo_withFlutter_returnsPlatformAndNoVersion() {
    Context context = getContext(); // has asset DevelopmentPlatformProvider.FLUTTER_ASSET_FILE

    DevelopmentPlatformProvider provider = new DevelopmentPlatformProvider(context);

    assertEquals(FLUTTER_PLATFORM, provider.getDevelopmentPlatform());
    assertNull(provider.getDevelopmentPlatformVersion());
    assertFalse(DevelopmentPlatformProvider.isUnity(context));
  }

  public void testDevelopmentPlatformInfo_unknownPlatform_returnsNull() throws Exception {
    Context context = createMockContext(/*withUnityResource=*/ false);

    assertFalse(DevelopmentPlatformProvider.isUnity(context));

    DevelopmentPlatformProvider provider = new DevelopmentPlatformProvider(context);

    assertNull(provider.getDevelopmentPlatform());
    assertNull(provider.getDevelopmentPlatformVersion());
  }

  private Context createMockContext(boolean withUnityResource) throws Exception {
    // Mock the ApplicationInfo.
    ApplicationInfo info = new ApplicationInfo();
    info.icon = 0;
    info.metaData = new Bundle();

    // Mock the PackageManager.
    PackageManager mockManager = mock(PackageManager.class);
    doReturn(info)
        .when(mockManager)
        .getApplicationInfo(eq(PACKAGE_NAME), eq(PackageManager.GET_META_DATA));

    // Mock the Resources.
    int resourceId = withUnityResource ? 1000 : 0;
    Resources resources = mock(Resources.class);
    doReturn(resourceId)
        .when(resources)
        .getIdentifier(
            eq("com.google.firebase.crashlytics.unity_version"), eq("string"), eq(PACKAGE_NAME));
    if (withUnityResource) {
      doReturn(UNITY_VERSION).when(resources).getString(eq(resourceId));
    }

    // Mock the application Context.
    Context applicationContext = mock(Context.class);
    doReturn(info).when(applicationContext).getApplicationInfo();

    // Mock the Context.
    Context mockContext = mock(Context.class);
    doReturn(mockManager).when(mockContext).getPackageManager();
    doReturn(PACKAGE_NAME).when(mockContext).getPackageName();
    doReturn(resources).when(mockContext).getResources();
    doReturn(applicationContext).when(mockContext).getApplicationContext();

    return mockContext;
  }
}
