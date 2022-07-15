package com.google.firebase.messaging;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION_CODES;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.testing.FakeScheduledExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPowerManager;

/** Robolectric test for FcmBroadcastProcessor. */
@RunWith(RobolectricTestRunner.class)
public class FcmBroadcastProcessorRoboTest {

  private static final String ACTION_FCM_MESSAGE = "com.google.android.c2dm.intent.RECEIVE";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private Application context;
  private FcmBroadcastProcessor processor;
  private FakeScheduledExecutorService fakeExecutorService;
  @Mock private ServiceStarter serviceStarter;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    ServiceStarter.setForTesting(serviceStarter);
    fakeExecutorService = new FakeScheduledExecutorService();
    processor = new FcmBroadcastProcessor(context, fakeExecutorService);
  }

  @After
  public void resetStaticState() {
    ServiceStarter.setForTesting(null);
    FcmBroadcastProcessor.reset();
    WakeLockHolder.reset();
    ShadowPowerManager.clearWakeLocks();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testStartMessagingService_NormalPriorityBackgroundCheck() {
    // Subject to background check when run on Android O and targetSdkVersion set to O.
    context.getApplicationInfo().targetSdkVersion = VERSION_CODES.O;
    when(serviceStarter.hasWakeLockPermission(any(Context.class))).thenReturn(true);

    Task<Integer> startServiceTask =
        processor.startMessagingService(context, new Intent(ACTION_FCM_MESSAGE));

    // Should return immediately with SUCCESS, bind to the Service, and acquire a WakeLock.
    assertThat(startServiceTask.getResult()).isEqualTo(ServiceStarter.SUCCESS);
    verify(serviceStarter, never()).startMessagingService(any(), any());
    assertThat(shadowOf(context).getBoundServiceConnections()).hasSize(1);
    assertThat(ShadowPowerManager.getLatestWakeLock()).isNotNull();
    assertThat(ShadowPowerManager.getLatestWakeLock().isHeld()).isTrue();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testStartMessagingService_bindNoWakeLockPermission() {
    // Subject to background check when run on Android O and targetSdkVersion set to O.
    context.getApplicationInfo().targetSdkVersion = VERSION_CODES.O;
    when(serviceStarter.hasWakeLockPermission(any(Context.class))).thenReturn(false);

    Task<Integer> startServiceTask =
        processor.startMessagingService(context, new Intent(ACTION_FCM_MESSAGE));

    // Should return immediately with SUCCESS and bind to the Service, but not try to acquire a
    // WakeLock since it doesn't hold the permission.
    assertThat(startServiceTask.getResult()).isEqualTo(ServiceStarter.SUCCESS);
    verify(serviceStarter, never()).startMessagingService(any(), any());
    assertThat(shadowOf(context).getBoundServiceConnections()).hasSize(1);
    assertThat(ShadowPowerManager.getLatestWakeLock()).isNull();
  }
}
