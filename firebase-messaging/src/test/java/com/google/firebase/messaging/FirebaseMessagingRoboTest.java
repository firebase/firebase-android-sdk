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
import static com.google.firebase.messaging.FirebaseMessaging.GMS_PACKAGE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Transformer;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.android.gms.cloudmessaging.CloudMessage;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.components.ComponentDiscoveryService;
import com.google.firebase.events.Subscriber;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.messaging.reporting.MessagingClientEventExtension;
import com.google.firebase.messaging.shadows.ShadowPreconditions;
import com.google.firebase.messaging.testing.FakeScheduledExecutorService;
import com.google.firebase.messaging.testing.FirebaseIidRoboTestHelper;
import com.google.firebase.messaging.testing.MessagingTestHelper;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;
import org.robolectric.shadows.ShadowLooper;

/**
 * Firebase Messaging tests.
 *
 * <p>Use ShadowPreconditions to override the Tasks.await()'s expectation for task to run on
 * background threads.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = ShadowPreconditions.class)
@LooperMode(Mode.PAUSED)
public final class FirebaseMessagingRoboTest {

  private static final String INVALID_TOPIC = "@invalid";
  private static final String TOPIC_NAME = "name";
  private static final String VALID_TOPIC = "/topics/" + TOPIC_NAME;
  private static final String APPLICATION_ID = FirebaseIidRoboTestHelper.APP_ID;
  private static final String PROJECT_ID = FirebaseIidRoboTestHelper.PROJECT_ID;
  private static final String API_KEY = FirebaseIidRoboTestHelper.API_KEY;

  private static final Provider<TransportFactory> EMPTY_TRANSPORT_FACTORY = () -> null;

  private Application context;
  private final FakeScheduledExecutorService fakeScheduledExecutorService =
      new FakeScheduledExecutorService();

  @Before
  public void setUp() throws InterruptedException, ExecutionException, TimeoutException {
    FirebaseIidRoboTestHelper.addGmsCorePackageInfo();
    FirebaseApp.clearInstancesForTest();
    context = ApplicationProvider.getApplicationContext();
    // Set the app's uid so that ProxyNotificationInitializer.allowedToUse() returns true.
    context.getApplicationInfo().uid = Binder.getCallingUid();
    // Disable auto-init to prevent it from automatically getting a token, which interferes with
    // some tests.
    editManifestApplicationMetadata().putBoolean("firebase_messaging_auto_init_enabled", false);
    ProxyNotificationPreferences.setProxyNotificationsInitialized(context, false);
    FirebaseApp.initializeApp(
        context,
        new FirebaseOptions.Builder()
            .setApplicationId(APPLICATION_ID)
            .setProjectId(PROJECT_ID)
            .setApiKey(API_KEY)
            .setGcmSenderId(FirebaseIidRoboTestHelper.SENDER_ID)
            .build());

    // To make sure proxy initialization happens before test execution.
    ProxyNotificationInitializer.initialize(context);
  }

  private void clearTopicOperations() {
    Context context = ApplicationProvider.getApplicationContext();
    TopicsStore store = TopicsStore.getInstance(context, fakeScheduledExecutorService);
    store.clearTopicOperations();

    // To make sure store pending operations are executed.
    fakeScheduledExecutorService.simulateNormalOperationFor(0, SECONDS);
  }

  @Test
  public void testGetInstance() {
    assertThat(FirebaseMessaging.getInstance()).isNotNull();
  }

  @Test
  public void testGetInstance_noRegistrarThrowsException() throws Exception {
    // Get the ServiceInfo without the metadata included (no flag GET_META_DATA).
    ServiceInfo serviceInfo =
        context
            .getPackageManager()
            .getServiceInfo(new ComponentName(context, ComponentDiscoveryService.class), 0);
    // Update ComponentDiscoveryService with the metadata-less ServiceInfo so the FirebaseMessaging
    // registrar will not be found.
    shadowOf(context.getPackageManager()).addOrUpdateService(serviceInfo);
    FirebaseApp.clearInstancesForTest();
    FirebaseApp.initializeApp(
        context,
        new FirebaseOptions.Builder()
            .setApplicationId(APPLICATION_ID)
            .setProjectId(PROJECT_ID)
            .setApiKey(API_KEY)
            .build());

    assertThrows(NullPointerException.class, FirebaseMessaging::getInstance);
  }

  /** Test that auto-init is enabled by default. */
  @Test
  public void testIsFcmAutoInitEnabled_default() {
    // Remove the firebase_messaging_auto_init_enabled metadata to return to the default state.
    editManifestApplicationMetadata().remove("firebase_messaging_auto_init_enabled");

    FirebaseMessaging messaging =
        new FirebaseMessaging(
            FirebaseApp.getInstance(),
            /* iid= */ null,
            () -> mock(UserAgentPublisher.class),
            () -> mock(HeartBeatInfo.class),
            mock(FirebaseInstallationsApi.class),
            EMPTY_TRANSPORT_FACTORY,
            mock(Subscriber.class));

    assertThat(messaging.isAutoInitEnabled()).isTrue();
  }

  /** Test that auto-init is disabled when disabled in the manifest through the Firebase flag. */
  @Test
  public void testIsFcmAutoInitEnabled_firebase_manifest() {
    editManifestApplicationMetadata().putBoolean("firebase_data_collection_default_enabled", false);
    editManifestApplicationMetadata().remove("firebase_messaging_auto_init_enabled");
    FirebaseApp.clearInstancesForTest();
    FirebaseApp.initializeApp(
        context,
        new FirebaseOptions.Builder()
            .setApplicationId(APPLICATION_ID)
            .setProjectId(PROJECT_ID)
            .setApiKey(API_KEY)
            .setGcmSenderId(FirebaseIidRoboTestHelper.SENDER_ID)
            .build());

    FirebaseMessaging messaging =
        new FirebaseMessaging(
            FirebaseApp.getInstance(),
            /* iid= */ null,
            () -> mock(UserAgentPublisher.class),
            () -> mock(HeartBeatInfo.class),
            mock(FirebaseInstallationsApi.class),
            EMPTY_TRANSPORT_FACTORY,
            mock(Subscriber.class));

    assertThat(messaging.isAutoInitEnabled()).isFalse();
  }

  /** Test that auto-init is disabled when disabled in the manifest. */
  @Test
  public void testIsFcmAutoInitEnabled_manifest() {
    editManifestApplicationMetadata().putBoolean("firebase_messaging_auto_init_enabled", false);

    FirebaseMessaging messaging =
        new FirebaseMessaging(
            FirebaseApp.getInstance(),
            /* iid= */ null,
            () -> mock(UserAgentPublisher.class),
            () -> mock(HeartBeatInfo.class),
            mock(FirebaseInstallationsApi.class),
            EMPTY_TRANSPORT_FACTORY,
            mock(Subscriber.class));

    assertThat(messaging.isAutoInitEnabled()).isFalse();
  }

  /** Test that auto-init is disabled when disabled through setting the Firebase flag at runtime. */
  @Test
  public void testIsFcmAutoInitEnabled_firebase_setDataCollectionDefaultDisabled() {
    FirebaseApp.getInstance().setDataCollectionDefaultEnabled(false);
    editManifestApplicationMetadata().remove("firebase_messaging_auto_init_enabled");

    assertThat(FirebaseMessaging.getInstance().isAutoInitEnabled()).isFalse();
  }

  /** Test that setting auto-init at runtime overrides the default setting. */
  @Test
  public void testSetAutoInitEnabled() {
    editManifestApplicationMetadata().remove("firebase_messaging_auto_init_enabled");

    FirebaseMessaging.getInstance().setAutoInitEnabled(false);

    assertThat(FirebaseMessaging.getInstance().isAutoInitEnabled()).isFalse();
  }

  /** Test that setting auto-init enabled at runtime overrides the Firebase flag. */
  @Test
  public void testSetFcmAutoInitEnabled_overrideDataCollectionDefaultEnabled() {
    FirebaseApp.getInstance().setDataCollectionDefaultEnabled(false);
    editManifestApplicationMetadata().remove("firebase_messaging_auto_init_enabled");

    FirebaseMessaging.getInstance().setAutoInitEnabled(true);

    assertThat(FirebaseMessaging.getInstance().isAutoInitEnabled()).isTrue();
  }

  /** Test that setting auto-init enabled at runtime overrides the manifest value. */
  @Test
  public void testSetFcmAutoInitEnabled_firebase_manifest() {
    editManifestApplicationMetadata().putBoolean("firebase_messaging_auto_init_enabled", false);
    FirebaseMessaging messaging =
        new FirebaseMessaging(
            FirebaseApp.getInstance(),
            /* iid= */ null,
            () -> mock(UserAgentPublisher.class),
            () -> mock(HeartBeatInfo.class),
            mock(FirebaseInstallationsApi.class),
            EMPTY_TRANSPORT_FACTORY,
            mock(Subscriber.class));

    messaging.setAutoInitEnabled(true);

    assertThat(messaging.isAutoInitEnabled()).isTrue();
  }

  @Test
  public void testGetToken() throws Exception {
    resetForTokenTests();
    GmsRpc mockGmsRpc = mock(GmsRpc.class);
    FirebaseMessaging messaging =
        new FirebaseMessaging(
            FirebaseApp.getInstance(),
            /* iid= */ null,
            EMPTY_TRANSPORT_FACTORY,
            mock(Subscriber.class),
            new Metadata(context),
            mockGmsRpc,
            Runnable::run,
            Runnable::run,
            Runnable::run);
    when(mockGmsRpc.getToken()).thenReturn(Tasks.forResult("fake_token"));

    Task<String> getTokenTask = messaging.getToken();

    ShadowLooper.idleMainLooper();
    assertThat(Tasks.await(getTokenTask, 5, SECONDS)).isEqualTo("fake_token");
    verifyOnNewTokenInvoked("fake_token");
    // TODO: Verify add to store.
  }

  @Test
  public void getToken_withFiid() throws Exception {
    resetForTokenTests();
    FirebaseInstanceIdInternal mockFiid = mock(FirebaseInstanceIdInternal.class);
    GmsRpc mockGmsRpc = mock(GmsRpc.class);
    FirebaseMessaging messaging =
        new FirebaseMessaging(
            FirebaseApp.getInstance(),
            mockFiid,
            EMPTY_TRANSPORT_FACTORY,
            mock(Subscriber.class),
            mock(Metadata.class),
            mockGmsRpc,
            Runnable::run,
            Runnable::run,
            Runnable::run);
    when(mockFiid.getTokenTask()).thenReturn(Tasks.forResult("fake_token"));

    Task<String> getTokenTask = messaging.getToken();

    ShadowLooper.idleMainLooper();
    assertThat(Tasks.await(getTokenTask, 5, SECONDS)).isEqualTo("fake_token");
    verifyNoMoreInteractions(mockGmsRpc);
  }

  @Test
  public void testDeleteToken() throws Exception {
    resetForTokenTests();
    GmsRpc mockGmsRpc = mock(GmsRpc.class);
    FirebaseMessaging messaging =
        new FirebaseMessaging(
            FirebaseApp.getInstance(),
            /* iid= */ null,
            EMPTY_TRANSPORT_FACTORY,
            mock(Subscriber.class),
            new Metadata(context),
            mockGmsRpc,
            Runnable::run,
            Runnable::run,
            Runnable::run);
    when(mockGmsRpc.getToken()).thenReturn(Tasks.forResult("fake_token"));
    when(mockGmsRpc.deleteToken()).thenReturn(Tasks.forResult(null));
    Tasks.await(messaging.getToken());

    Task<Void> deleteTokenTask = messaging.deleteToken();

    ShadowLooper.idleMainLooper();
    Tasks.await(deleteTokenTask, 5, SECONDS);
    verify(mockGmsRpc).deleteToken();
    // TODO: Verify delete from store.
  }

  @Test
  public void deleteToken_withFiid() throws Exception {
    resetForTokenTests();
    FirebaseInstanceIdInternal mockFiid = mock(FirebaseInstanceIdInternal.class);
    GmsRpc mockGmsRpc = mock(GmsRpc.class);
    FirebaseMessaging messaging =
        new FirebaseMessaging(
            FirebaseApp.getInstance(),
            mockFiid,
            EMPTY_TRANSPORT_FACTORY,
            mock(Subscriber.class),
            mock(Metadata.class),
            mockGmsRpc,
            Runnable::run,
            Runnable::run,
            Runnable::run);

    Task<Void> deleteTokenTask = messaging.deleteToken();

    ShadowLooper.idleMainLooper();
    Tasks.await(deleteTokenTask, 5, SECONDS);
    verify(mockFiid)
        .deleteToken(FirebaseIidRoboTestHelper.SENDER_ID, FirebaseMessaging.INSTANCE_ID_SCOPE);
    verifyNoMoreInteractions(mockGmsRpc);
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void isProxyNotificationEnabledDefaultsToTrueForNewerDevices() {
    GmsRpc mockGmsRpc = mock(GmsRpc.class);
    when(mockGmsRpc.setRetainProxiedNotifications(anyBoolean())).thenReturn(Tasks.forResult(null));
    when(mockGmsRpc.getProxyNotificationData()).thenReturn(Tasks.forResult(null));
    FirebaseMessaging messaging =
        new FirebaseMessaging(
            FirebaseApp.getInstance(),
            /* iid= */ null,
            EMPTY_TRANSPORT_FACTORY,
            mock(Subscriber.class),
            mock(Metadata.class),
            mockGmsRpc,
            Runnable::run,
            Runnable::run,
            Runnable::run);

    assertThat(messaging.isNotificationDelegationEnabled()).isTrue();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void isProxyNotificationEnabledDefaultsToFalseForOlderDevices() {
    FirebaseMessaging messaging =
        new FirebaseMessaging(
            FirebaseApp.getInstance(),
            /* iid= */ null,
            EMPTY_TRANSPORT_FACTORY,
            mock(Subscriber.class),
            mock(Metadata.class),
            mock(GmsRpc.class),
            Runnable::run,
            Runnable::run,
            Runnable::run);

    assertThat(messaging.isNotificationDelegationEnabled()).isFalse();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void setEnableProxyNotificationFailsOnOlderDevices() throws Exception {
    Tasks.await(FirebaseMessaging.getInstance().setNotificationDelegationEnabled(true));
    assertThat(FirebaseMessaging.getInstance().isNotificationDelegationEnabled()).isFalse();
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void setEnableProxyNotificationWorksOnNewerDevices() throws Exception {
    Tasks.await(FirebaseMessaging.getInstance().setNotificationDelegationEnabled(false));
    Tasks.await(FirebaseMessaging.getInstance().setNotificationDelegationEnabled(true));
    assertThat(FirebaseMessaging.getInstance().isNotificationDelegationEnabled()).isTrue();
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void setDisableProxyNotificationWorksOnNewerDevices() throws Exception {
    Tasks.await(FirebaseMessaging.getInstance().setNotificationDelegationEnabled(false));
    assertThat(FirebaseMessaging.getInstance().isNotificationDelegationEnabled()).isFalse();
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void proxyNotificationEnabledIsFalseWhenUserSetsAnotherNotificationDelegate()
      throws Exception {
    Tasks.await(FirebaseMessaging.getInstance().setNotificationDelegationEnabled(true));
    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    notificationManager.setNotificationDelegate("other.package");
    assertThat(FirebaseMessaging.getInstance().isNotificationDelegationEnabled()).isFalse();
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void proxyNotificationEnabledIsTrueWhenGMSCoreIsSetAsNotificationDelegate()
      throws Exception {
    Tasks.await(FirebaseMessaging.getInstance().setNotificationDelegationEnabled(false));
    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    notificationManager.setNotificationDelegate(GMS_PACKAGE);
    assertThat(FirebaseMessaging.getInstance().isNotificationDelegationEnabled()).isTrue();
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void proxyNotificationEnabledIsFalseWhenUidIsWrong() throws Exception {
    // Set the app's uid so that ProxyNotificationInitializer.allowedToUse() returns false.
    context.getApplicationInfo().uid = Binder.getCallingUid() + 1;

    Tasks.await(FirebaseMessaging.getInstance().setNotificationDelegationEnabled(true));

    assertThat(FirebaseMessaging.getInstance().isNotificationDelegationEnabled()).isFalse();
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void initialProxy_noGoogleAnalyticsNoBigQuery() {
    // Delete SharedPreferences to clear out the RetainProxiedNotifications setting.
    context.deleteSharedPreferences("com.google.firebase.messaging");
    GmsRpc mockGmsRpc = createMockGmsRpc();

    createFirebaseMessageInstance(mockGmsRpc, /* analyticsConnectorPresent= */ false);

    // GA not present and BigQuery export not enabled, shouldn't retain proxy notifications.
    verify(mockGmsRpc).setRetainProxiedNotifications(false);
    verify(mockGmsRpc, never()).getProxyNotificationData();
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void initialProxy_googleAnalyticsNoBigQuery() {
    // Delete SharedPreferences to clear out the RetainProxiedNotifications setting.
    context.deleteSharedPreferences("com.google.firebase.messaging");
    GmsRpc mockGmsRpc = createMockGmsRpc();

    createFirebaseMessageInstance(mockGmsRpc, /* analyticsConnectorPresent= */ true);

    // GA present and BigQuery export not enabled, should retain proxy notifications.
    verify(mockGmsRpc).setRetainProxiedNotifications(true);
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void initialProxy_noGoogleAnalyticsBigQuery() {
    // Delete SharedPreferences to clear out the RetainProxiedNotifications setting.
    context.deleteSharedPreferences("com.google.firebase.messaging");
    editManifestApplicationMetadata()
        .putBoolean("delivery_metrics_exported_to_big_query_enabled", true);
    GmsRpc mockGmsRpc = createMockGmsRpc();

    createFirebaseMessageInstance(mockGmsRpc, /* analyticsConnectorPresent= */ false);

    // GA not present and BigQuery export enabled, should retain proxy notifications.
    verify(mockGmsRpc).setRetainProxiedNotifications(true);
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void initialProxy_googleAnalyticsBigQuery() {
    // Delete SharedPreferences to clear out the RetainProxiedNotifications setting.
    context.deleteSharedPreferences("com.google.firebase.messaging");
    // Enable BigQuery export.
    editManifestApplicationMetadata()
        .putBoolean("delivery_metrics_exported_to_big_query_enabled", true);
    GmsRpc mockGmsRpc = createMockGmsRpc();

    createFirebaseMessageInstance(mockGmsRpc, /* analyticsConnectorPresent= */ true);

    // GA present and BigQuery export enabled, should retain proxy notifications.
    verify(mockGmsRpc).setRetainProxiedNotifications(true);
  }

  @Test
  @Config(sdk = VERSION_CODES.P)
  public void initialProxy_preQ() {
    // Delete SharedPreferences to clear out the RetainProxiedNotifications setting.
    context.deleteSharedPreferences("com.google.firebase.messaging");
    // Enable BigQuery export.
    editManifestApplicationMetadata()
        .putBoolean("delivery_metrics_exported_to_big_query_enabled", true);
    GmsRpc mockGmsRpc = createMockGmsRpc();

    createFirebaseMessageInstance(mockGmsRpc, /* analyticsConnectorPresent= */ true);

    // Shouldn't set retention on a version that doesn't support proxy.
    verify(mockGmsRpc, never()).setRetainProxiedNotifications(anyBoolean());
    verify(mockGmsRpc, never()).getProxyNotificationData();
  }

  private static class FakeFirelogTransportFactory implements TransportFactory {
    final Transport<?> mockTransport;

    FakeFirelogTransportFactory(Transport<?> transport) {
      mockTransport = transport;
    }

    @Override
    public <T> Transport<T> getTransport(
        String name, Class<T> payloadType, Transformer<T, byte[]> payloadTransformer) {
      throw new IllegalStateException("unimplemented");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Transport<T> getTransport(
        String name,
        Class<T> payloadType,
        Encoding payloadEncoding,
        Transformer<T, byte[]> payloadTransformer) {
      return (Transport<T>) mockTransport;
    }
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  @SuppressWarnings("unchecked")
  public void initializeProxy_handlesProxyNotifications() {
    // Enable BigQuery export so that it will try to log to Firelog if it receives proxy data.
    editManifestApplicationMetadata()
        .putBoolean("delivery_metrics_exported_to_big_query_enabled", true);
    GmsRpc mockGmsRpc = createMockGmsRpc();
    when(mockGmsRpc.getProxyNotificationData())
        .thenReturn(
            Tasks.forResult(new CloudMessage(new Intent())),
            Tasks.forResult(new CloudMessage(new Intent())),
            Tasks.forResult(null));
    Transport<MessagingClientEventExtension> mockTransport = mock(Transport.class);

    FirebaseMessaging unused =
        new FirebaseMessaging(
            FirebaseApp.getInstance(),
            /* iid= */ null,
            () -> new FakeFirelogTransportFactory(mockTransport),
            mock(Subscriber.class),
            mock(Metadata.class),
            mockGmsRpc,
            Runnable::run,
            Runnable::run,
            Runnable::run);

    // Should have logged to Firelog twice, once for each notification returned.
    verify(mockTransport, times(2)).send(any());
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  @SuppressWarnings("unchecked")
  public void initializeProxy_handlesNoPendingProxyNotifications() {
    // Enable BigQuery export so that it will try to log to Firelog if it receives proxy data.
    editManifestApplicationMetadata()
        .putBoolean("delivery_metrics_exported_to_big_query_enabled", true);
    GmsRpc mockGmsRpc = createMockGmsRpc();
    when(mockGmsRpc.getProxyNotificationData()).thenReturn(Tasks.forResult(null));
    Transport<MessagingClientEventExtension> mockTransport = mock(Transport.class);

    FirebaseMessaging unused =
        new FirebaseMessaging(
            FirebaseApp.getInstance(),
            /* iid= */ null,
            () -> new FakeFirelogTransportFactory(mockTransport),
            mock(Subscriber.class),
            mock(Metadata.class),
            mockGmsRpc,
            Runnable::run,
            Runnable::run,
            Runnable::run);

    // Should have tried to retrieve data, but should not have logged since there is no pending
    // proxy notification data.
    verify(mockGmsRpc).getProxyNotificationData();
    verify(mockTransport, never()).send(any());
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void setDeliveryMetricsExportToBigQuery_enablesProxyRetention() {
    GmsRpc mockGmsRpc = createMockGmsRpc();
    FirebaseMessaging messaging =
        createFirebaseMessageInstance(mockGmsRpc, /* analyticsConnectorPresent= */ false);
    clearInvocations(mockGmsRpc);

    messaging.setDeliveryMetricsExportToBigQuery(true);

    // Enabling BigQuery should start retaining proxy notifications.
    verify(mockGmsRpc).setRetainProxiedNotifications(true);
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void setDeliveryMetricsExportToBigQuery_disablesProxyRetention() {
    // Enable BigQuery export.
    editManifestApplicationMetadata()
        .putBoolean("delivery_metrics_exported_to_big_query_enabled", true);
    GmsRpc mockGmsRpc = createMockGmsRpc();
    // Create an instance that should retain proxy notifications because BigQuery is enabled.
    FirebaseMessaging messaging =
        createFirebaseMessageInstance(mockGmsRpc, /* analyticsConnectorPresent= */ false);
    clearInvocations(mockGmsRpc);

    messaging.setDeliveryMetricsExportToBigQuery(false);

    // Disabling BigQuery should stop retaining proxy notifications.
    verify(mockGmsRpc).setRetainProxiedNotifications(false);
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void setDeliveryMetricsExportToBigQuery_noProxyRetentionChange() {
    // Enable BigQuery export.
    editManifestApplicationMetadata()
        .putBoolean("delivery_metrics_exported_to_big_query_enabled", true);
    // Create an instance that should retain proxy notifications due to AnalyticsConnector and
    // BigQuery enabled.
    GmsRpc mockGmsRpc = createMockGmsRpc();
    FirebaseMessaging messaging =
        createFirebaseMessageInstance(mockGmsRpc, /* analyticsConnectorPresent= */ true);
    clearInvocations(mockGmsRpc);

    messaging.setDeliveryMetricsExportToBigQuery(false);

    // Disabling BigQuery should keep retaining proxy notifications since AnalyticsConnector is
    // still present.
    verify(mockGmsRpc, never()).setRetainProxiedNotifications(anyBoolean());
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void setNotificationDelegationEnabled_disablesProxyRetention() throws Exception {
    GmsRpc mockGmsRpc = createMockGmsRpc();
    // Create an instance that should save that proxy notifications are retained.
    FirebaseMessaging messaging =
        createFirebaseMessageInstance(mockGmsRpc, /* analyticsConnectorPresent= */ true);
    clearInvocations(mockGmsRpc);

    Tasks.await(messaging.setNotificationDelegationEnabled(false));

    // Disabling proxy should stop retaining proxy notifications.
    ShadowLooper.idleMainLooper();
    verify(mockGmsRpc).setRetainProxiedNotifications(false);
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void setNotificationDelegationEnabled_enablesProxyRetention() throws Exception {
    GmsRpc mockGmsRpc = createMockGmsRpc();
    // Create an instance that should retain proxy notifications, but disable proxy notifications.
    FirebaseMessaging messaging =
        createFirebaseMessageInstance(mockGmsRpc, /* analyticsConnectorPresent= */ true);
    Tasks.await(messaging.setNotificationDelegationEnabled(false));
    ShadowLooper.idleMainLooper();
    clearInvocations(mockGmsRpc);

    Tasks.await(messaging.setNotificationDelegationEnabled(true));

    // Enabling proxy should start retaining proxy notifications again.
    ShadowLooper.idleMainLooper();
    verify(mockGmsRpc).setRetainProxiedNotifications(true);
  }

  private GmsRpc createMockGmsRpc() {
    GmsRpc mockGmsRpc = mock(GmsRpc.class);
    when(mockGmsRpc.setRetainProxiedNotifications(anyBoolean())).thenReturn(Tasks.forResult(null));
    when(mockGmsRpc.getProxyNotificationData()).thenReturn(Tasks.forResult(null));
    return mockGmsRpc;
  }

  @CanIgnoreReturnValue
  private FirebaseMessaging createFirebaseMessageInstance(
      GmsRpc gmsRpc, boolean analyticsConnectorPresent) {
    FirebaseApp firebaseAppSpy = spy(FirebaseApp.getInstance());
    when(firebaseAppSpy.get(AnalyticsConnector.class))
        .thenReturn(analyticsConnectorPresent ? mock(AnalyticsConnector.class) : null);

    return new FirebaseMessaging(
        firebaseAppSpy,
        /* iid= */ null,
        () -> mock(TransportFactory.class),
        mock(Subscriber.class),
        mock(Metadata.class),
        gmsRpc,
        Runnable::run,
        Runnable::run,
        Runnable::run);
  }

  /*
  TODO b/286544512
  @Test
  public void testSubscribeToTopic_withPrefix() {
    firebaseMessaging.subscribeToTopic(VALID_TOPIC);
    shadowOf(getMainLooper()).runToEndOfTasks();
    assertThat(topicSubscriber.getStore().getNextTopicOperation())
        .isEqualTo(TopicOperation.subscribe(VALID_TOPIC));
  }

  @Test
  public void testUnsubscribeFromTopic_withPrefix() {
    firebaseMessaging.unsubscribeFromTopic(VALID_TOPIC);
    shadowOf(getMainLooper()).runToEndOfTasks();
    assertThat(topicSubscriber.getStore().getNextTopicOperation())
        .isEqualTo(TopicOperation.unsubscribe(VALID_TOPIC));
  }

  @Test
  public void testSubscribeToTopic_invalid() {
    Task<Void> task = FirebaseMessaging.getInstance().subscribeToTopic(INVALID_TOPIC);
    shadowOf(getMainLooper()).runToEndOfTasks();
    assertThat(task.getException()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testUnsubscribeFromTopic_invalid() {
    Task<Void> task = FirebaseMessaging.getInstance().unsubscribeFromTopic(INVALID_TOPIC);
    shadowOf(getMainLooper()).runToEndOfTasks();
    assertThat(task.getException()).isInstanceOf(IllegalArgumentException.class);
  }
   */

  @Test
  public void testLibraryVersionRegistration_valid() {
    assertThat(FirebaseApp.getInstance().get(UserAgentPublisher.class).getUserAgent())
        .contains("fire-fcm/" + BuildConfig.VERSION_NAME);
  }

  @Test
  public void blockingGetToken_calledTwice_OnNewTokenInvokedOnce() throws Exception {
    resetForTokenTests();
    GmsRpc mockGmsRpc = mock(GmsRpc.class);
    TaskCompletionSource<String> tokenTaskCompletionSource = new TaskCompletionSource<>();
    when(mockGmsRpc.getToken()).thenReturn(tokenTaskCompletionSource.getTask());
    FirebaseMessaging messaging =
        new FirebaseMessaging(
            FirebaseApp.getInstance(),
            /* iid= */ null,
            EMPTY_TRANSPORT_FACTORY,
            mock(Subscriber.class),
            new Metadata(context),
            mockGmsRpc,
            Runnable::run,
            Runnable::run,
            Runnable::run);

    // Call blockingGetToken() twice. Since GmsRpc.getToken() is blocked, neither call should see a
    // cached token so they should both get to the requestDeduplicator step.
    CountDownLatch getTokenLatch = new CountDownLatch(2);
    new Thread(
            () -> {
              try {
                messaging.blockingGetToken();
                getTokenLatch.countDown();
              } catch (IOException e) {
                e.printStackTrace();
              }
            })
        .start();
    new Thread(
            () -> {
              try {
                messaging.blockingGetToken();
                getTokenLatch.countDown();
              } catch (IOException e) {
                e.printStackTrace();
              }
            })
        .start();

    // Return a result for GmsRpc.getToken(), which should cause the requestDeduplicator Task to
    // complete and allow the blockingGetToken() calls to finish.
    tokenTaskCompletionSource.setResult("fake_token");
    getTokenLatch.await();
    ShadowLooper.idleMainLooper();
    // onNewToken should only be called once, for the original Task, not for the deduplicated Task.
    verifyOnNewTokenInvoked("fake_token");
    verifyOnNewTokenNotInvoked();
  }

  private Bundle editManifestApplicationMetadata() {
    return shadowOf(ApplicationProvider.getApplicationContext().getPackageManager())
        .getInternalMutablePackageInfo(context.getPackageName())
        .applicationInfo
        .metaData;
  }

  private void resetForTokenTests() {
    // Set to not auto-init so that the auto-token generation is not triggered.
    FirebaseApp.getInstance().setDataCollectionDefaultEnabled(false);
    // Delete any cached tokens.
    MessagingTestHelper.clearIidState(context);
    // Reset the Store so that it doesn't keep the token SharedPreferences that might have a stored
    // token.
    FirebaseMessaging.clearStoreForTest();
    // Clear out any Services that have already been started.
    shadowOf(context).clearStartedServices();
  }

  private void verifyOnNewTokenInvoked(String token) {
    Intent serviceIntent = shadowOf(context).getNextStartedService();
    assertThat(serviceIntent.getPackage()).isEqualTo(context.getPackageName());
    assertThat(serviceIntent.getAction()).isEqualTo("com.google.firebase.MESSAGING_EVENT");

    Intent messagingIntent = ServiceStarter.getInstance().getMessagingEvent();
    assertThat(messagingIntent.getAction()).isEqualTo("com.google.firebase.messaging.NEW_TOKEN");
    assertThat(messagingIntent.getStringExtra("token")).isEqualTo(token);
  }

  private void verifyOnNewTokenNotInvoked() {
    assertThat(shadowOf(context).getNextStartedService()).isNull();
  }
}
