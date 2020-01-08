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

import static org.mockito.Mockito.*;

import android.content.Context;
import android.content.pm.PackageManager;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;

public class InstallerPackageNameProviderTest extends CrashlyticsTestCase {

  private InstallerPackageNameProvider testProvider;
  private PackageManager mockPackageManager;
  private Context mockContext;

  public void setUp() {
    testProvider = new InstallerPackageNameProvider();

    mockPackageManager = mock(PackageManager.class);

    mockContext = mock(Context.class);
    when(mockContext.getPackageName()).thenReturn("some non-null string");
    when(mockContext.getPackageManager()).thenReturn(mockPackageManager);
  }

  public void testGetInstallerPackageName_null() {
    when(mockPackageManager.getInstallerPackageName(anyString())).thenReturn(null);

    assertNull(testProvider.getInstallerPackageName(mockContext));
  }

  public void testGetInstallerPackageName_present() {
    final String expected = "expected_package_name";
    when(mockPackageManager.getInstallerPackageName(anyString())).thenReturn(expected);

    assertEquals(expected, testProvider.getInstallerPackageName(mockContext));
  }
}
