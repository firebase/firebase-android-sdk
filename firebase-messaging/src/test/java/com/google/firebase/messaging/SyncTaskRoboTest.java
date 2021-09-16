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
import static com.google.firebase.messaging.testing.FirebaseIidRoboTestHelper.API_KEY;
import static com.google.firebase.messaging.testing.FirebaseIidRoboTestHelper.APP_ID;
import static com.google.firebase.messaging.testing.FirebaseIidRoboTestHelper.PROJECT_ID;
import static com.google.firebase.messaging.testing.FirebaseIidRoboTestHelper.SENDER_ID;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.testing.FakeScheduledExecutorService;
import com.google.firebase.messaging.testing.FirebaseIidRoboTestHelper;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

/** Robolectric test for the FirebaseInstanceIdService. */
@RunWith(RobolectricTestRunner.class)
public class SyncTaskRoboTest {

  private FirebaseApp defaultApp;
  @Mock private FirebaseMessaging firebaseMessaging;

  private Application context;
  private SyncTask syncTask;
  private FakeScheduledExecutorService fakeExecutorService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    context = spy(ApplicationProvider.getApplicationContext());
    doReturn(context).when(context).getApplicationContext();

    // Clear static singleton instances
    FirebaseApp.clearInstancesForTest();

    defaultApp =
        FirebaseApp.initializeApp(
            context,
            new FirebaseOptions.Builder()
                .setApplicationId(APP_ID)
                .setGcmSenderId(SENDER_ID)
                .setApiKey(API_KEY)
                .setProjectId(PROJECT_ID)
                .build());

    firebaseMessaging = mock(FirebaseMessaging.class);
    doReturn(context).when(firebaseMessaging).getApplicationContext();
    syncTask = spy(new SyncTask(firebaseMessaging, 30 /* next delay if task fails */));
    syncTask.processorExecutor = fakeExecutorService = new FakeScheduledExecutorService();

    FirebaseIidRoboTestHelper.addGmsCorePackageInfo();
  }

  @Test
  public void testIsDeviceConnected_NPEracecondition() throws Exception {
    ConnectivityManager cm = mock(ConnectivityManager.class);
    NetworkInfo ni = mock(NetworkInfo.class);
    when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(cm);
    when(ni.isConnected()).thenReturn(true);
    // Simulate a racecondition between two calls to getActiveNetworkInfo(). b/69658859 cl/69658859
    when(cm.getActiveNetworkInfo()).thenReturn(ni).thenReturn(null);
    assertTrue(syncTask.isDeviceConnected());
  }

  @Test
  public void testMaybeRefreshToken_needsRefresh() throws Exception {
    when(firebaseMessaging.getTokenWithoutTriggeringSync()).thenReturn(null);
    when(firebaseMessaging.tokenNeedsRefresh(any())).thenReturn(true);
    when(firebaseMessaging.blockingGetToken()).thenReturn("new");

    boolean continueSync = syncTask.maybeRefreshToken();

    assertTrue(continueSync);
    verify(firebaseMessaging).blockingGetToken();
  }

  @Test
  public void testMaybeRefreshToken_failureNull() throws Exception {
    when(firebaseMessaging.getTokenWithoutTriggeringSync()).thenReturn(null);
    when(firebaseMessaging.tokenNeedsRefresh(any())).thenReturn(true);
    when(firebaseMessaging.blockingGetToken()).thenReturn(null);

    boolean continueSync = syncTask.maybeRefreshToken();

    assertFalse(continueSync);
  }

  @Test
  public void testMaybeRefreshToken_failureIOException() throws Exception {
    when(firebaseMessaging.getTokenWithoutTriggeringSync()).thenReturn(null);
    when(firebaseMessaging.tokenNeedsRefresh(any())).thenReturn(true);
    when(firebaseMessaging.blockingGetToken()).thenThrow(new IOException());

    boolean continueSync = syncTask.maybeRefreshToken();

    assertFalse(continueSync);
  }

  @Test
  public void testMaybeRefreshToken_failureSecurityException() throws Exception {
    when(firebaseMessaging.getTokenWithoutTriggeringSync()).thenReturn(null);
    when(firebaseMessaging.tokenNeedsRefresh(any())).thenReturn(true);
    when(firebaseMessaging.blockingGetToken()).thenThrow(new SecurityException());

    boolean continueSync = syncTask.maybeRefreshToken();

    assertFalse(continueSync);
  }

  @Test
  public void testMaybeRefreshToken_errorServiceNotAvailable() throws Exception {
    when(firebaseMessaging.getTokenWithoutTriggeringSync()).thenReturn(null);
    when(firebaseMessaging.tokenNeedsRefresh(any())).thenReturn(true);
    when(firebaseMessaging.blockingGetToken()).thenThrow(new IOException("SERVICE_NOT_AVAILABLE"));

    assertThat(syncTask.maybeRefreshToken()).isFalse(); // will retry
  }

  @Test
  public void testMaybeRefreshToken_errorInternalServerError() throws Exception {
    when(firebaseMessaging.getTokenWithoutTriggeringSync()).thenReturn(null);
    when(firebaseMessaging.tokenNeedsRefresh(any())).thenReturn(true);
    when(firebaseMessaging.blockingGetToken()).thenThrow(new IOException("INTERNAL_SERVER_ERROR"));

    assertThat(syncTask.maybeRefreshToken()).isFalse(); // will retry
  }

  @Test
  public void testMaybeRefreshToken_errorInternalServerError_camelCase() throws Exception {
    when(firebaseMessaging.getTokenWithoutTriggeringSync()).thenReturn(null);
    when(firebaseMessaging.tokenNeedsRefresh(any())).thenReturn(true);
    when(firebaseMessaging.blockingGetToken()).thenThrow(new IOException("InternalServerError"));

    assertThat(syncTask.maybeRefreshToken()).isFalse(); // will retry
  }

  @Test
  public void testRun_retryWithWhiteListError() throws Exception {
    doReturn(true).when(firebaseMessaging).isGmsCorePresent();
    doReturn(true).when(syncTask).isDeviceConnected();

    // Should retry if token registration process gets a whitelist error and topic subscription
    // process gets whitelist error
    doReturn(false).when(syncTask).maybeRefreshToken();
    syncTask.run();

    verify(firebaseMessaging).setSyncScheduledOrRunning(true);
    verify(firebaseMessaging).syncWithDelaySecondsInternal(anyLong());
  }

  @Test
  public void testRun_noRetryWithNonWhitelistError() throws Exception {
    doReturn(true).when(firebaseMessaging).isGmsCorePresent();
    doReturn(true).when(syncTask).isDeviceConnected();

    // Should not retry if token registration process gets a server error and topic subscription
    // process gets a whitelist error
    doThrow(new IOException()).when(syncTask).maybeRefreshToken();

    syncTask.run();

    verify(firebaseMessaging, never()).syncWithDelaySecondsInternal(anyLong());
  }

  @Test
  public void testRun_noRetryWhenTopicOperationsFail() throws Exception {
    doReturn(true).when(firebaseMessaging).isGmsCorePresent();
    doReturn(true).when(syncTask).isDeviceConnected();

    // Should not retry if token registration process gets a server error and topic subscription
    // process gets a whitelist error
    doReturn(true).when(syncTask).maybeRefreshToken();

    syncTask.run();

    verify(firebaseMessaging, never()).syncWithDelaySecondsInternal(anyLong());
  }
}
