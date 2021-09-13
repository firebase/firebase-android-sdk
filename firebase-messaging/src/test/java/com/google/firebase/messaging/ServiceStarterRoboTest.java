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
package com.google.firebase.messaging;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.messaging.testing.IntentSubject.assertThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowPowerManager;

/** Robolectric test for the ServiceStarter. */
@RunWith(RobolectricTestRunner.class)
public class ServiceStarterRoboTest {

  // Copied as this is part of our API as clients define them in their manifest, so if our code
  // tries to use a different string, the tests should fail.
  private static final String ACTION_MESSAGING_EVENT = "com.google.firebase.MESSAGING_EVENT";

  private static final String EXTRA_WRAPPED_INTENT = "wrapped_intent";

  private Application context;
  private ServiceStarter serviceStarter;

  @Before
  public void setUp() {
    ServiceStarter.setForTesting(null); // reset any cached state
    context = spy(ApplicationProvider.getApplicationContext());
    serviceStarter = ServiceStarter.getInstance();
  }

  @Test
  public void testStartMessagingService() {
    Intent intent = new Intent("intent123");
    int result = serviceStarter.startMessagingService(context, intent);
    assertThat(result).isEqualTo(ServiceStarter.SUCCESS);

    Intent serviceIntent = shadowOf(context).getNextStartedService();
    assertThat(serviceIntent.getAction()).isEqualTo(ACTION_MESSAGING_EVENT);
    assertThat(serviceIntent.getPackage()).isEqualTo(context.getPackageName());

    Intent polledIntent = serviceStarter.getMessagingEvent();
    assertThat(polledIntent).isSameInstanceAs(intent);

    // Shouldn't be any other intents
    assertThat(serviceStarter.getMessagingEvent()).isNull();
  }

  @Test
  public void testStartService_notFound() {
    doReturn(null).when(context).startService(nullable(Intent.class));

    int result = serviceStarter.startMessagingService(context, new Intent());
    assertThat(result).isEqualTo(ServiceStarter.ERROR_NOT_FOUND);
  }

  @Test
  public void testStartService_securityException() {
    doThrow(new SecurityException()).when(context).startService(nullable(Intent.class));

    int result = serviceStarter.startMessagingService(context, new Intent());
    assertThat(result).isEqualTo(ServiceStarter.ERROR_SECURITY_EXCEPTION);
  }

  @Test
  public void testStartService_illegalStateException() {
    // Will be thrown on Android-O if app is in background
    doThrow(new IllegalStateException()).when(context).startService(nullable(Intent.class));

    int result = serviceStarter.startMessagingService(context, new Intent());
    assertThat(result).isEqualTo(ServiceStarter.ERROR_ILLEGAL_STATE_EXCEPTION);
  }

  @Test
  public void testWakeLockPermissionGranted() {
    shadowOf(context).grantPermissions(Manifest.permission.WAKE_LOCK);
    doTestWakeLockPermission(true);

    // Now flip permission to check value is cached. This would never happen for a real app.
    shadowOf(context).denyPermissions(Manifest.permission.WAKE_LOCK);
    doTestWakeLockPermission(true);
  }

  @Test
  public void testWakeLockPermissionDenied() {
    shadowOf(context).denyPermissions(Manifest.permission.WAKE_LOCK);
    doTestWakeLockPermission(false);

    // Now flip permission to check value is cached. This would never happen for a real app.
    shadowOf(context).grantPermissions(Manifest.permission.WAKE_LOCK);
    doTestWakeLockPermission(false);
  }

  private void doTestWakeLockPermission(boolean expectGranted) {
    Intent intent = new Intent("intent123");
    ShadowPowerManager.reset();
    WakeLockHolder.reset();

    serviceStarter.startMessagingService(context, intent);

    if (expectGranted) {
      assertThat(ShadowPowerManager.getLatestWakeLock()).isNotNull();
    } else {
      assertThat(ShadowPowerManager.getLatestWakeLock()).isNull();
    }
  }

  @Test
  public void testResolvingServiceClassName_relative() {
    addResolveInfo(ACTION_MESSAGING_EVENT, ".MessagingService");

    String packageName = context.getPackageName();
    verifyServiceClassSet(ACTION_MESSAGING_EVENT, packageName + ".MessagingService");

    // Now change the resolved services to check they are cached. This would never happen for
    // a real app.
    removeResolveInfos(ACTION_MESSAGING_EVENT);
    addResolveInfo(ACTION_MESSAGING_EVENT, ".MessagingService2");

    verifyServiceClassSet(ACTION_MESSAGING_EVENT, packageName + ".MessagingService");
  }

  @Test
  public void testResolvingServiceClassName_absolute() {
    addResolveInfo(ACTION_MESSAGING_EVENT, "com.doodad.MessagingService");

    verifyServiceClassSet(ACTION_MESSAGING_EVENT, "com.doodad.MessagingService");
  }

  private void addResolveInfo(String action, String service) {
    Intent intent = new Intent(action);
    intent.setPackage(context.getPackageName());

    ResolveInfo ri = new ResolveInfo();
    ri.serviceInfo = new ServiceInfo();
    ri.serviceInfo.name = service;
    ri.serviceInfo.packageName = context.getPackageName();

    shadowOf(context.getPackageManager()).addResolveInfoForIntent(intent, ri);
  }

  private void removeResolveInfos(String action) {
    Intent intent = new Intent(action);
    intent.setPackage(context.getPackageName());

    shadowOf(context.getPackageManager())
        .removeResolveInfosForIntent(intent, context.getPackageName());
  }

  private void verifyServiceClassSet(String action, String service) {
    int result = serviceStarter.startMessagingService(context, new Intent());
    assertThat(result).isEqualTo(ServiceStarter.SUCCESS);

    Intent serviceIntent = shadowOf(context).getNextStartedService();
    assertThat(serviceIntent.getAction()).isEqualTo(action);
    assertThat(serviceIntent.getComponent())
        .isEquivalentAccordingToCompareTo(new ComponentName(context.getPackageName(), service));
  }
}
