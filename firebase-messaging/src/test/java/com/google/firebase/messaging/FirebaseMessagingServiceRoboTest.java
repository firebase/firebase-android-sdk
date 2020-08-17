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
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Application;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceIdReceiver;
import com.google.firebase.iid.MessengerIpcClient;
import com.google.firebase.iid.ServiceStarter;
import com.google.firebase.messaging.AnalyticsTestHelper.Analytics;
import com.google.firebase.messaging.shadows.ShadowMessenger;
import com.google.firebase.messaging.testing.AnalyticsValidator;
import com.google.firebase.messaging.testing.Bundles;
import com.google.firebase.messaging.testing.FakeConnectorComponent;
import com.google.firebase.messaging.testing.MessengerIpcClientTester;
import com.google.firebase.messaging.testing.MessengerIpcClientTester.Request;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowMessenger.class})
@LooperMode(Mode.PAUSED)
public class FirebaseMessagingServiceRoboTest {

  // Constants are copied so that tests will break if these change
  private static final String ACTION_NEW_TOKEN = "com.google.firebase.messaging.NEW_TOKEN";

  private static final String DEFAULT_FROM = "123";
  private static final String DEFAULT_TO = "456";

  // Extra for the token within a NEW_TOKEN event
  private static final String EXTRA_TOKEN = "token";

  private static final AnalyticsValidator analyticsValidator =
      FakeConnectorComponent.getAnalyticsValidator();

  private Application context;
  private FirebaseInstanceIdReceiver receiver;
  private FirebaseMessagingService service;
  private NotificationManager notificationManager;
  private ExecutorService executorService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    context = spy(ApplicationProvider.getApplicationContext());
    FirebaseMessagingService.resetForTesting();
    MessengerIpcClient.resetForTesting();
    mockAppForeground(false);

    receiver = new FirebaseInstanceIdReceiver();

    ServiceController<FirebaseMessagingService> serviceController =
        Robolectric.buildService(FirebaseMessagingService.class);
    service = spy(serviceController.get());
    doReturn(context).when(service).getApplicationContext();
    service.onCreate();

    notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

    FirebaseApp.clearInstancesForTest();
    // Create a test FirebaseApp instance
    FirebaseOptions.Builder firebaseOptionsBuilder =
        new FirebaseOptions.Builder()
            .setApplicationId("test_app_id")
            .setApiKey("api_key")
            .setGcmSenderId("1234567890");
    FirebaseApp.initializeApp(context, firebaseOptionsBuilder.build());
    analyticsValidator.reset();

    // Disable receivers registered in the manifest.
    shadowOf((Application) ApplicationProvider.getApplicationContext()).clearRegisteredReceivers();

    executorService = Executors.newSingleThreadExecutor();
  }

  @After
  public void tearDown() throws Exception {
    executorService.shutdown();

    executorService.awaitTermination(1, TimeUnit.SECONDS);
  }

  @Test
  public void testMessage_empty() throws Exception {
    RemoteMessageBuilder builder = new RemoteMessageBuilder().setFrom(DEFAULT_FROM);
    startServiceViaReceiver(builder.buildIntent());
    verify(service).onMessageReceived(argThat(builder.buildMatcher()));
  }

  @Test
  public void testMessage_complete() throws Exception {
    RemoteMessageBuilder builder =
        new RemoteMessageBuilder()
            .setFrom(DEFAULT_FROM)
            .setTo(DEFAULT_TO)
            .addData("key1", "value1")
            .addData("key2", "value2")
            .setRawData(new byte[] {1, 1, 2, 3, 5, 8, 13})
            .setCollapseKey("collapse_key_123")
            .setMessageId("message_id_456")
            .setSentTime(123456789)
            .setTtl(12345);
    startServiceViaReceiver(builder.buildIntent());
    verify(service).onMessageReceived(argThat(builder.buildMatcher()));
  }

  @Test
  public void testMessage_analytics() throws Exception {
    RemoteMessageBuilder builder = new RemoteMessageBuilder();
    AnalyticsTestHelper.addAnalyticsExtras(builder);

    startServiceViaReceiver(builder.buildIntent());
    verify(service).onMessageReceived(argThat(builder.buildMatcher()));

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(Analytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(Analytics.EVENT_NOTIFICATION_RECEIVE);
    assertThat(event.getParams().getString(Analytics.PARAM_MESSAGE_ID)).isEqualTo("composer_key");
    assertThat(event.getParams().getString(Analytics.PARAM_MESSAGE_NAME))
        .isEqualTo("composer_label");
    assertThat(event.getParams().getInt(Analytics.PARAM_MESSAGE_TIME)).isEqualTo(1234567890);
    assertThat(event.getParams().containsKey(Analytics.PARAM_TOPIC)).isFalse();
  }

  /** Test that an Intent with no extras (getExtras() returns null) doesn't result in a crash. */
  @Test
  public void testMessage_nullIntentExtras() throws Exception {
    RemoteMessageBuilder builder = new RemoteMessageBuilder();
    Intent intent = builder.buildIntent();
    assertNull("Test isn't passing intent with null extras", intent.getExtras());
    startServiceViaReceiver(intent);
    verify(service).onMessageReceived(argThat(builder.buildMatcher()));
  }

  @Test
  public void testAckSentForMessage() throws Exception {
    String messageId = "a.message.id";
    MessengerIpcClientTester messengerIpcClientTester =
        MessengerIpcClientTester.createWithFirebaseIidMessengerCompat();

    startServiceViaReceiver(new RemoteMessageBuilder().setMessageId(messageId).buildIntent());

    verifyAckSent(messengerIpcClientTester, messageId);
  }

  /**
   * Verifies an ack for the given {@code messageId} was sent by polling a single request off the
   * {@link MessengerIpcClientTester} queue.
   */
  private static void verifyAckSent(
      MessengerIpcClientTester messengerIpcClientTester, String messageId)
      throws InterruptedException {
    Request request = messengerIpcClientTester.pollSentRequestWithTimeout();
    assertThat(request).isNotNull();
    assertThat(request.what).isEqualTo(MessengerIpcClient.What.FCM_ACK);

    Bundle requestData = new Bundle();
    requestData.putString(RemoteMessageBuilder.EXTRA_MSGID, messageId);
    Bundles.assertEquals(requestData, request.data);
  }

  @Test
  public void testAckSentForMessage_evenWhenOnMessageReceivedBlocks() throws Exception {
    CountDownLatch foreverLatch = new CountDownLatch(1);
    // Block until test is over
    setOnMessageReceivedImplementation(foreverLatch::await);

    try {
      String messageId = "a.message.id";
      MessengerIpcClientTester messengerIpcClientTester =
          MessengerIpcClientTester.createWithFirebaseIidMessengerCompat();

      // Run the service in a background thread so that the blocking doesn't block the test
      handleIntent(
          new RemoteMessageBuilder().setMessageId(messageId).buildIntent(), 2, TimeUnit.SECONDS);

      verifyAckSent(messengerIpcClientTester, messageId);
    } finally {
      foreverLatch.countDown();
    }
  }

  @Test
  public void testWaitsForAckToSend() throws Exception {
    setAckTimeoutMillis(TimeUnit.SECONDS.toMillis(3));

    MessengerIpcClientTester messengerIpcClientTester =
        MessengerIpcClientTester.createWithFirebaseIidMessengerCompat();
    // Request isn't finished until it is acked
    messengerIpcClientTester.setAckOneWayRequests(false);

    CountDownLatch serviceFinished =
        handleIntent(
            new RemoteMessageBuilder().setMessageId("a.message.id").buildIntent(),
            1,
            TimeUnit.SECONDS);

    Request request = messengerIpcClientTester.pollSentRequestWithTimeout(1, TimeUnit.SECONDS);
    assertThat(request.what).isEqualTo(MessengerIpcClient.What.FCM_ACK);

    // Service shouldn't finish
    assertThat(serviceFinished.await(100, TimeUnit.MILLISECONDS)).isFalse();

    // Acking the request (from GmsCore) should allow the service to finish
    request.sendAck();

    assertThat(serviceFinished.await(100, TimeUnit.MILLISECONDS)).isTrue();
  }

  @Test
  public void testTimesOutWaitingForAckToSend() throws Exception {
    // set a higher ack timeout to decrease chance of flakes
    setAckTimeoutMillis(TimeUnit.SECONDS.toMillis(3));

    MessengerIpcClientTester messengerIpcClientTester =
        MessengerIpcClientTester.createWithFirebaseIidMessengerCompat();
    // Request isn't finished until it is acked
    messengerIpcClientTester.setAckOneWayRequests(false);

    long startedAtMillis = System.currentTimeMillis();

    CountDownLatch serviceFinished =
        handleIntent(
            new RemoteMessageBuilder().setMessageId("a.message.id").buildIntent(),
            1,
            TimeUnit.SECONDS);

    Request request =
        messengerIpcClientTester.pollSentRequestWithTimeout(500, TimeUnit.MILLISECONDS);
    assertThat(request.what).isEqualTo(MessengerIpcClient.What.FCM_ACK);

    long millisUntilTimeout =
        (startedAtMillis + TimeUnit.SECONDS.toMillis(3)) - System.currentTimeMillis();

    assertWithMessage("polling took too long, service already finished")
        .that(millisUntilTimeout)
        .isGreaterThan(TimeUnit.SECONDS.toMillis(1));

    // Service shouldn't finish until timeout
    assertThat(serviceFinished.await(1, TimeUnit.SECONDS)).isFalse();
    // But should be timed out after 1s
    millisUntilTimeout =
        (startedAtMillis + TimeUnit.SECONDS.toMillis(3)) - System.currentTimeMillis();
    assertThat(serviceFinished.await(millisUntilTimeout, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void testDuplicateMessageDropped() throws Exception {
    String messageId = "a.message.id";
    MessengerIpcClientTester messengerIpcClientTester =
        MessengerIpcClientTester.createWithFirebaseIidMessengerCompat();

    Intent messageIntent = new RemoteMessageBuilder().setMessageId(messageId).buildIntent();
    startServiceViaReceiver(messageIntent);
    verify(service).onMessageReceived(any(RemoteMessage.class));
    verifyAckSent(messengerIpcClientTester, messageId);

    // Second time message shouldn't be passed to onMessageReceived(), but ack should still be sent
    startServiceViaReceiver(messageIntent);
    verify(service, times(1)).onMessageReceived(any(RemoteMessage.class));
    verifyAckSent(messengerIpcClientTester, messageId);
  }

  @Test
  public void testDuplicateMessage_onlyKeeps10() throws Exception {
    RemoteMessageBuilder firstMessage = new RemoteMessageBuilder().setMessageId("first.message.id");
    startServiceViaReceiver(firstMessage.buildIntent());
    verify(service).onMessageReceived(argThat(firstMessage.buildMatcher()));

    // Send 10 messages which should flush the first message ID out of the queue
    for (int i = 0; i < 10; i++) {
      RemoteMessageBuilder message = new RemoteMessageBuilder().setMessageId("extra_message." + i);
      startServiceViaReceiver(message.buildIntent());
      verify(service).onMessageReceived(argThat(message.buildMatcher()));
    }

    // Now resending the first one should invoke the client callback as its message ID should have
    // been removed.
    startServiceViaReceiver(firstMessage.buildIntent());
    verify(service, times(2)).onMessageReceived(argThat(firstMessage.buildMatcher()));
  }

  @Test
  public void testDuplicateMessage_noMessageId_notDropped() throws Exception {
    Intent messageIntent = new RemoteMessageBuilder().setMessageId("").buildIntent();
    startServiceViaReceiver(messageIntent);
    startServiceViaReceiver(messageIntent);
    verify(service, times(2)).onMessageReceived(any(RemoteMessage.class));
  }

  /** Test a deleted messages message. */
  @Test
  public void testDeletedMessages() throws Exception {
    RemoteMessageBuilder builder =
        new RemoteMessageBuilder().setMessageType(RemoteMessageBuilder.MESSAGE_TYPE_DELETED);
    startServiceViaReceiver(builder.buildIntent());
    verify(service).onDeletedMessages();
  }

  /** Test a send event message. */
  @Test
  public void testSendEvent() throws Exception {
    RemoteMessageBuilder builder =
        new RemoteMessageBuilder()
            .setMessageType(RemoteMessageBuilder.MESSAGE_TYPE_SEND_EVENT)
            .setMessageId("msg-123");
    startServiceViaReceiver(builder.buildIntent());
    verify(service).onMessageSent(eq("msg-123"));
  }

  /** Test a send error message. */
  @Test
  public void testSendError() throws Exception {
    String msgId = "error msg-123";
    String error = "something went wrong";
    RemoteMessageBuilder builder =
        new RemoteMessageBuilder()
            .setMessageType(RemoteMessageBuilder.MESSAGE_TYPE_SEND_ERROR)
            .setMessageId(msgId)
            .addData(RemoteMessageBuilder.EXTRA_ERROR, error);
    startServiceViaReceiver(builder.buildIntent());
    verify(service)
        .onSendError(eq(msgId), argThat(sendException(SendException.ERROR_UNKNOWN, error)));
  }

  /** Test a send error message with the server sent "message_id" passes the id to onSendError */
  @Test
  public void testSendError_messageIdServer() throws Exception {
    String msgId = "error msg-456";
    String error = "your request failed";
    RemoteMessageBuilder builder =
        new RemoteMessageBuilder()
            .setMessageType(RemoteMessageBuilder.MESSAGE_TYPE_SEND_ERROR)
            .addData("message_id", msgId)
            .addData(RemoteMessageBuilder.EXTRA_ERROR, error);
    startServiceViaReceiver(builder.buildIntent());
    verify(service)
        .onSendError(eq(msgId), argThat(sendException(SendException.ERROR_UNKNOWN, error)));
  }

  @Test
  public void testOnNewToken() throws Exception {
    Intent intent = new Intent(ACTION_NEW_TOKEN);
    intent.putExtra(EXTRA_TOKEN, "token123");
    ServiceStarter.startMessagingServiceViaReceiver(context, intent);

    List<Intent> broadcastIntents = shadowOf(context).getBroadcastIntents();
    assertThat(broadcastIntents).hasSize(1);
    startServiceViaReceiver(broadcastIntents.get(0));

    verify(service).onNewToken("token123");
  }

  /**
   * Test a notification message causes a notification to be posted, and none of the callbacks to be
   * invoked.
   */
  @Test
  public void testNotification() throws Exception {
    RemoteMessageBuilder builder = new RemoteMessageBuilder();
    builder.addData("gcm.n.e", "1");
    startServiceViaReceiver(builder.buildIntent());

    verifyNoServiceMethodsInvoked();

    // Check a notification was posted
    assertEquals(1, shadowOf(notificationManager).size());
  }

  /**
   * Test a notification message invokes onMessageReceived() instead of posting a notification when
   * the app is in the foreground.
   */
  @Test
  public void testNotification_fgCallback() throws Exception {
    mockAppForeground(true);

    RemoteMessageBuilder builder = new RemoteMessageBuilder();
    builder.addData("gcm.n.e", "1");
    // TODO(morepork) Add some notification parameters, e.g. title/icon to the test once they
    // are exposed through RemoteMessage
    startServiceViaReceiver(builder.buildIntent());

    // Check onMessageReceived() was called with the correct parameters
    verify(service).onMessageReceived(argThat(builder.buildMatcher()));

    // Notification manager should not have been invoked
    assertTrue(shadowOf(notificationManager).getAllNotifications().isEmpty());
  }

  /**
   * Test a notification message logs a notification foreground event if the app is in the
   * foreground an onMessageReceived() is invoked.
   */
  @Test
  public void testNotification_fgCallbackAnalytics() throws Exception {
    mockAppForeground(true);

    RemoteMessageBuilder builder = new RemoteMessageBuilder();
    builder.addData("gcm.n.e", "1");
    AnalyticsTestHelper.addAnalyticsExtras(builder);
    startServiceViaReceiver(builder.buildIntent());

    verify(service).onMessageReceived(argThat(builder.buildMatcher()));

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(2);
    AnalyticsValidator.LoggedEvent receiveEvent = events.get(0);
    assertThat(receiveEvent.getOrigin()).isEqualTo(Analytics.ORIGIN_FCM);
    assertThat(receiveEvent.getName()).isEqualTo(Analytics.EVENT_NOTIFICATION_RECEIVE);
    assertThat(receiveEvent.getParams().getString(Analytics.PARAM_MESSAGE_ID))
        .isEqualTo("composer_key");
    assertThat(receiveEvent.getParams().getString(Analytics.PARAM_MESSAGE_NAME))
        .isEqualTo("composer_label");
    assertThat(receiveEvent.getParams().getInt(Analytics.PARAM_MESSAGE_TIME)).isEqualTo(1234567890);
    assertThat(receiveEvent.getParams().containsKey(Analytics.PARAM_TOPIC)).isFalse();

    AnalyticsValidator.LoggedEvent foregroundEvent = events.get(1);
    assertThat(foregroundEvent.getOrigin()).isEqualTo(Analytics.ORIGIN_FCM);
    assertThat(foregroundEvent.getName()).isEqualTo(Analytics.EVENT_NOTIFICATION_FOREGROUND);
    Bundles.assertEquals(foregroundEvent.getParams(), receiveEvent.getParams());
  }

  /** Test that a notification logs the correct event on click. */
  @Test
  public void testNotification_clickAnalytics() throws Exception {
    simulateNotificationMessageWithAnalytics();

    assertEquals(1, shadowOf(notificationManager).size());
    Notification n = shadowOf(notificationManager).getAllNotifications().remove(0);
    assertOpenDismissPendingIntentsDiffer(n);
    dispatchToReceiver(n.contentIntent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(2);
    AnalyticsValidator.LoggedEvent receiveEvent = events.get(0);
    assertThat(receiveEvent.getOrigin()).isEqualTo(Analytics.ORIGIN_FCM);
    assertThat(receiveEvent.getName()).isEqualTo(Analytics.EVENT_NOTIFICATION_RECEIVE);
    assertThat(receiveEvent.getParams().getString(Analytics.PARAM_MESSAGE_ID))
        .isEqualTo("composer_key");
    assertThat(receiveEvent.getParams().getString(Analytics.PARAM_MESSAGE_NAME))
        .isEqualTo("composer_label");
    assertThat(receiveEvent.getParams().getInt(Analytics.PARAM_MESSAGE_TIME)).isEqualTo(1234567890);
    assertThat(receiveEvent.getParams().containsKey(Analytics.PARAM_TOPIC)).isFalse();

    AnalyticsValidator.LoggedEvent openEvent = events.get(1);
    assertThat(openEvent.getOrigin()).isEqualTo(Analytics.ORIGIN_FCM);
    assertThat(openEvent.getName()).isEqualTo(Analytics.EVENT_NOTIFICATION_OPEN);
    // Remove the message type param from receiveEvent before checking equality since that
    // param should only be included on receive, not dismiss.
    receiveEvent.getParams().remove("_nmc");
    Bundles.assertEquals(openEvent.getParams(), receiveEvent.getParams());

    // App's main activity should be started
    assertThat(
            shadowOf((Application) ApplicationProvider.getApplicationContext())
                .getNextStartedActivity())
        .isNotNull();
  }

  /** Test that a notification logs the correct event on dismiss. */
  @Test
  public void testNotification_dismissAnalytics() throws Exception {
    simulateNotificationMessageWithAnalytics();

    assertEquals(1, shadowOf(notificationManager).size());
    Notification n = shadowOf(notificationManager).getAllNotifications().remove(0);
    assertOpenDismissPendingIntentsDiffer(n);
    dispatchToReceiver(n.deleteIntent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(2);
    AnalyticsValidator.LoggedEvent receiveEvent = events.get(0);
    assertThat(receiveEvent.getOrigin()).isEqualTo(Analytics.ORIGIN_FCM);
    assertThat(receiveEvent.getName()).isEqualTo(Analytics.EVENT_NOTIFICATION_RECEIVE);
    assertThat(receiveEvent.getParams().getString(Analytics.PARAM_MESSAGE_ID))
        .isEqualTo("composer_key");
    assertThat(receiveEvent.getParams().getString(Analytics.PARAM_MESSAGE_NAME))
        .isEqualTo("composer_label");
    assertThat(receiveEvent.getParams().getInt(Analytics.PARAM_MESSAGE_TIME)).isEqualTo(1234567890);
    assertThat(receiveEvent.getParams().containsKey(Analytics.PARAM_TOPIC)).isFalse();

    AnalyticsValidator.LoggedEvent dismissEvent = events.get(1);
    assertThat(dismissEvent.getOrigin()).isEqualTo(Analytics.ORIGIN_FCM);
    assertThat(dismissEvent.getName()).isEqualTo(Analytics.EVENT_NOTIFICATION_DISMISS);
    // Remove the message type param from receiveEvent before checking equality since that
    // param should only be included on receive, not dismiss.
    receiveEvent.getParams().remove("_nmc");
    Bundles.assertEquals(dismissEvent.getParams(), receiveEvent.getParams());

    // No activity should be started
    assertNull(
        shadowOf((Application) ApplicationProvider.getApplicationContext())
            .getNextStartedActivity());
  }

  /**
   * Test that a no UI notification received in the background doesn't post a notification nor
   * invoke any callbacks, but does log a receive event.
   */
  @Test
  public void testNotification_noUiBg() throws Exception {
    checkNoUiNotificationOnlyDoesAnalytics();
  }

  /**
   * Test that a no UI notification received in the foreground doesn't post a notification nor
   * invoke any callbacks, but does log a receive event.
   */
  @Test
  public void testNotification_noUiFg() throws Exception {
    mockAppForeground(true);
    checkNoUiNotificationOnlyDoesAnalytics();
  }

  private void checkNoUiNotificationOnlyDoesAnalytics() throws InterruptedException {
    RemoteMessageBuilder builder = new RemoteMessageBuilder();
    builder.addData("gcm.n.e", "1");
    AnalyticsTestHelper.addAnalyticsExtras(builder);
    builder.addData(DisplayNotificationRoboTest.KEY_NO_UI, "1");
    startServiceViaReceiver(builder.buildIntent());

    verifyNoServiceMethodsInvoked();
    assertEquals(0, shadowOf(notificationManager).size());

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(Analytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(Analytics.EVENT_NOTIFICATION_RECEIVE);
  }

  // TODO(dgiorgini) add tests with multiple notifications click / dismissal

  @Test
  public void testShouldIntentUploadMetrics_true() {
    Intent intent = new Intent();
    intent.putExtra(AnalyticsTestHelper.ANALYTICS_ENABLED, "1");
    assertTrue(MessagingAnalytics.shouldUploadScionMetrics(intent));
  }

  @Test
  public void testShouldIntentUploadMetrics_empty() {
    assertFalse(MessagingAnalytics.shouldUploadScionMetrics(new Intent()));
  }

  @Test
  public void testShouldIntentUploadMetrics_null() {
    assertFalse(MessagingAnalytics.shouldUploadScionMetrics((Intent) null));
  }

  @Test
  public void testNullTransportFactory_handleGracefully() {
    // To simulate when FirebaseMessagingService receives a message when FirebaseMessaging hasn't
    // been initialized.
    FirebaseMessaging.clearTransportFactoryForTest();
    assertThat(FirebaseMessaging.getTransportFactory()).isNull();

    MessagingAnalytics.setDeliveryMetricsExportToBigQuery(true);
    Intent messageIntent = new RemoteMessageBuilder().setMessageId("a.message.id").buildIntent();
    executorService.execute(() -> service.handleIntent(messageIntent));
    // process not crashing, we handled it gracefully. Ideally, we want to verify
    // MessagingAnalytics#logNotificationReceived(Intent, Transport) is not called, but we don't
    // have good way to verify static method calls
  }

  private void simulateNotificationMessageWithAnalytics() throws InterruptedException {
    RemoteMessageBuilder builder = new RemoteMessageBuilder();
    builder.addData("gcm.n.e", "1");
    // Robo manifest doesn't have a default activity, so set click action so that we get a
    // click pending intent.
    builder.addData(DisplayNotificationRoboTest.KEY_CLICK_ACTION, "click_action");
    AnalyticsTestHelper.addAnalyticsExtras(builder);
    startServiceViaReceiver(builder.buildIntent());
  }

  private void dispatchToReceiver(PendingIntent pi) throws InterruptedException {
    Intent intent = shadowOf(pi).getSavedIntent();

    // Ensure the intent is a broadcast to the receiver, then dispatch it
    assertWithMessage("expected broadcast intent").that(shadowOf(pi).isBroadcastIntent()).isTrue();

    assertEquals(
        intent.getComponent(), new ComponentName(context, FirebaseInstanceIdReceiver.class));
    startServiceViaReceiver(intent);
  }

  private ArgumentMatcher<Exception> sendException(final int errorCode, final String message) {
    return new ArgumentMatcher<Exception>() {
      @Override
      public boolean matches(Exception argument) {
        if (!(argument instanceof SendException)) {
          return false;
        }
        SendException exception = (SendException) argument;
        return exception.getErrorCode() == errorCode && exception.getMessage().equals(message);
      }
    };
  }

  private void mockAppForeground(boolean foreground) {
    KeyguardManager mockKeyguardManager = mock(KeyguardManager.class);
    doReturn(mockKeyguardManager).when(context).getSystemService(eq(Context.KEYGUARD_SERVICE));

    // Simulate screen on and unlocked
    doReturn(false).when(mockKeyguardManager).inKeyguardRestrictedInputMode();
    String packageName = context.getPackageName();
    RunningAppProcessInfo process =
        new RunningAppProcessInfo(packageName, Process.myPid(), new String[] {packageName});
    process.importance =
        foreground
            ? RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            : RunningAppProcessInfo.IMPORTANCE_BACKGROUND;
    ActivityManager activityManager =
        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    shadowOf(activityManager).setProcesses(Arrays.asList(process));
  }

  private void assertOpenDismissPendingIntentsDiffer(Notification n) {
    assertNotEquals(
        shadowOf(n.contentIntent).getRequestCode(), shadowOf(n.deleteIntent).getRequestCode());
  }

  private void verifyNoServiceMethodsInvoked() {
    verify(service, never()).onMessageReceived(any(RemoteMessage.class));
    verify(service, never()).onDeletedMessages();
    verify(service, never()).onMessageSent(anyString());
    verify(service, never()).onSendError(anyString(), any(Exception.class));
  }

  private void setOnMessageReceivedImplementation(ThrowingRunnable runnable) {
    doAnswer(
            invocation -> {
              runnable.run();
              return null; // returns void
            })
        .when(service)
        .onMessageReceived(any(RemoteMessage.class));
  }

  private void setAckTimeoutMillis(long timeoutMillis) {
    doReturn(timeoutMillis).when(service).getAckTimeoutMillis();
  }

  public void startServiceViaReceiver(Intent intent) throws InterruptedException {
    // Throw away any other service starts that may have happened before this one
    shadowOf(context).clearStartedServices();

    CountDownLatch latch = new CountDownLatch(1);

    HandlerThread handlerThread = new HandlerThread("receiver-handler-thread");
    handlerThread.start();
    Handler scheduler = new Handler(handlerThread.getLooper());

    try {
      context.registerReceiver(
          receiver,
          new IntentFilter(intent.getAction()),
          /* broadcastPermission= */ null,
          /* scheduler= */ scheduler);

      context.sendOrderedBroadcast(
          intent,
          /* receiverPermission= */ null,
          /* resultReceiver= */ new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
              latch.countDown();
            }
          },
          /* scheduler= */ scheduler,
          /* initialCode= */ 0,
          /* initialData= */ null,
          /* initialExtras= */ null);

      shadowOf(Looper.getMainLooper()).idle();

      // wait for broadcast to finish processing
      assertWithMessage("timed out waiting for FirebaseInstanceIdReceiver to process intent")
          .that(latch.await(5, TimeUnit.SECONDS))
          .isTrue();
    } finally {
      context.unregisterReceiver(receiver);
      handlerThread.quitSafely();
    }

    processInternalStartService(context);
  }

  /**
   * Take a single start service call and process it so that it starts the corresponding service.
   */
  public void processInternalStartService(Application application) throws InterruptedException {
    Intent serviceIntent = shadowOf(application).getNextStartedService();
    assertEquals(application.getPackageName(), serviceIntent.getPackage());
    assertNotNull("No service found for: " + serviceIntent, serviceIntent);

    service.onStartCommand(serviceIntent, 0 /* flags */, 1 /* startId */);
    flushTasks();
  }

  // Flush the Service background tasks
  private void flushTasks() throws InterruptedException {
    // Should be able to use:
    // Robolectric.flushBackgroundThreadScheduler();
    // but it doesn't work, see: https://github.com/robolectric/robolectric/issues/2149
    CountDownLatch latch = new CountDownLatch(1);
    service.executor.execute(latch::countDown);
    // This timeout needs to be quite long as generating the key pair sometimes take >10s
    assertTrue("Task didn't finish", latch.await(20, TimeUnit.SECONDS));
  }

  private static Set<ServiceConnection> getBoundServiceConnections() {
    // robolectric's getBoundServiceConnections() returns a ref to their list of bound service
    // connections, so we can't use any iterator-based APIs unless we want to crash sometimes on a
    // ConcurrentModificationException. Instead, we'll dump the contents to an array (no iterators).
    List<ServiceConnection> connList =
        shadowOf((Application) ApplicationProvider.getApplicationContext())
            .getBoundServiceConnections();
    return ImmutableSet.copyOf(connList.toArray(new ServiceConnection[0]));
  }

  /**
   * Calls handleIntent on {@link #service} in a background thread and connects any outgoing
   * bindService calls made in the meantime.
   *
   * <p>Returns a CountDownLatch that's finished when the call to handleIntent finishes.
   */
  private CountDownLatch handleIntent(Intent intent, int time, TimeUnit unit) throws Exception {
    long timeoutAtMillis = System.currentTimeMillis() + unit.toMillis(time);

    // Connections that were active before we started
    Set<ServiceConnection> previousConnections = getBoundServiceConnections();

    // Run the thing that will cause a bindService call in the background
    CountDownLatch finishedLatch = new CountDownLatch(1);
    executorService.execute(
        () -> {
          service.handleIntent(intent);
          finishedLatch.countDown();
        });

    // wait for the call to be made (service connection to appear)
    while (Sets.difference(getBoundServiceConnections(), previousConnections).isEmpty()) {
      assertWithMessage("timed out waiting for binder connection")
          .that(System.currentTimeMillis())
          .isLessThan(timeoutAtMillis);

      TimeUnit.MILLISECONDS.sleep(50);
    }

    CountDownLatch flushLatch = new CountDownLatch(1);
    new Handler(Looper.getMainLooper()).post(flushLatch::countDown);
    shadowOf(Looper.getMainLooper()).idle();

    long millisRemaining = Math.max(0, timeoutAtMillis - System.currentTimeMillis());
    assertThat(flushLatch.await(millisRemaining, TimeUnit.MILLISECONDS)).isTrue();

    return finishedLatch;
  }
}
