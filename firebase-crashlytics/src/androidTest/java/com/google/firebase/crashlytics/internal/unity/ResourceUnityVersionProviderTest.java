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

package com.google.firebase.crashlytics.internal.unity;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;

public class ResourceUnityVersionProviderTest extends CrashlyticsTestCase {

  private static final String PACKAGE_NAME = "package.name";
  private static final String RESOURCE_UNITY_VERSION = "2.0.0";

  private static final String MISSING_PACKAGE_NAME = "missing.package.name";

  public void testGetUnityVersion_returnsVersionString() throws Exception {
    final Context context = createMockContext(true);
    final ResourceUnityVersionProvider provider = new ResourceUnityVersionProvider(context);
    assertEquals(RESOURCE_UNITY_VERSION, provider.getUnityVersion());
  }

  public void testGetUnityVersion_returnsNullWhenValueDoesNotExist() throws Exception {
    final Context context = createMockContext(false);
    final ResourceUnityVersionProvider provider = new ResourceUnityVersionProvider(context);
    assertNull(provider.getUnityVersion());
  }

  private Context createMockContext(boolean mockResource)
      throws PackageManager.NameNotFoundException {
    // Mock the ApplicationInfo.
    final ApplicationInfo info = new ApplicationInfo();
    info.icon = 0;
    info.metaData = new Bundle();

    // Mock the PackageManager.
    final PackageManager mockManager = mock(PackageManager.class);
    doReturn(info)
        .when(mockManager)
        .getApplicationInfo(eq(PACKAGE_NAME), eq(PackageManager.GET_META_DATA));
    doThrow(new PackageManager.NameNotFoundException())
        .when(mockManager)
        .getApplicationInfo(eq(MISSING_PACKAGE_NAME), eq(PackageManager.GET_META_DATA));

    // Mock the Resources.
    final int resourceId = mockResource ? 1000 : 0;
    final Resources resources = mock(Resources.class);
    doReturn(resourceId)
        .when(resources)
        .getIdentifier(
            eq("com.google.firebase.crashlytics.unity_version"), eq("string"), eq(PACKAGE_NAME));
    if (mockResource) {
      doReturn(RESOURCE_UNITY_VERSION).when(resources).getString(eq(resourceId));
    }

    // Mock the application Context.
    final Context applicationContext = mock(Context.class);
    doReturn(info).when(applicationContext).getApplicationInfo();

    // Mock the Context.
    final Context mockContext = mock(Context.class);
    doReturn(mockManager).when(mockContext).getPackageManager();
    doReturn(PACKAGE_NAME).when(mockContext).getPackageName();
    doReturn(resources).when(mockContext).getResources();
    doReturn(applicationContext).when(mockContext).getApplicationContext();

    return mockContext;
  }
}
