package com.google.firebase.crashlytics.internal;

import static com.google.firebase.crashlytics.internal.DevelopmentPlatformProvider.UNITY_PLATFORM;
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
  private static final String UNITY_VERSION = "2.0.0";

  public void testDevelopmentPlatformInfo_withUnity_returnsPlatformAndVersion() throws Exception {
    Context context = createMockContext(/*withUnityResource=*/ true);

    DevelopmentPlatformProvider provider = new DevelopmentPlatformProvider(context);

    assertEquals(UNITY_PLATFORM, provider.getDevelopmentPlatform());
    assertEquals(UNITY_VERSION, provider.getDevelopmentPlatformVersion());
  }

  public void testDevelopmentPlatformInfo_unknownPlatform_returnsNull() throws Exception {
    Context context = createMockContext(/*withUnityResource=*/ false);

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
