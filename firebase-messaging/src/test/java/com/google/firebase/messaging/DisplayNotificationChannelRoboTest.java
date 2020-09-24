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

import static android.content.Context.NOTIFICATION_SERVICE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
public final class DisplayNotificationChannelRoboTest {

  static final String KEY_PREFIX = "gcm.n.";
  static final String KEY_BODY = KEY_PREFIX + "body";
  static final String KEY_CHANNEL = KEY_PREFIX + "android_channel_id";

  static final String FCM_FALLBACK_CHANNEL = "fcm_fallback_notification_channel";

  static final String METADATA_DEFAULT_CHANNEL =
      "com.google.firebase.messaging.default_notification_channel_id";

  Context context = ApplicationProvider.getApplicationContext();

  NotificationManager notificationManager;
  Executor executor;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    // Clear out the processes so that the app will be treated as not in the foreground.
    shadowOf(context.getSystemService(ActivityManager.class)).setProcesses(new ArrayList<>());
    notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
    swipeAwayExistingNotifications(notificationManager);
    executor = Executors.newSingleThreadExecutor();
    // Cleanup channels that have could have been created during API 26 tests
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      deleteChannel(notificationManager, FCM_FALLBACK_CHANNEL);
    }
  }

  @Test
  @Config(minSdk = 23, maxSdk = 25) // getActiveNotifications() not available <23
  public void channelIgnored_belowApi26TargetSdk25() throws Exception {
    setTargetSdkVersion(25);
    createDisplayNotification(context, createNotificationMessage(), executor).handleNotification();

    // Just check that the notification has been successfully displayed,
    // and we didn't crash while trying to use the non-existent API setChannel()
    assertThat(getNotifications(notificationManager)).hasSize(1);
  }

  @Test
  @Config(minSdk = 23, maxSdk = 25) // getActiveNotifications() not available <23
  public void channelIgnored_belowApi26TargetSdk26() throws Exception {
    setTargetSdkVersion(26);
    createDisplayNotification(context, createNotificationMessage(), executor).handleNotification();

    // Just check that the notification has been successfully displayed,
    // and we didn't crash while trying to use the non-existent API setChannel();
    assertThat(getNotifications(notificationManager)).hasSize(1);
  }

  @Test
  @Config(minSdk = 26, maxSdk = 28)
  public void channelIgnored_Api26PlusTargetSdk25() throws Exception {
    setTargetSdkVersion(25);
    createDisplayNotification(context, createNotificationMessage(), executor).handleNotification();

    // If the app is using targetSdk < 26, we should NOT set the channel.
    // check that the notification was displayed, and that there is not channel associated to it.
    List<Notification> notifications = getNotifications(notificationManager);
    assertThat(notifications).hasSize(1);
    assertThat(getChannel(notifications.get(0))).isNull();

    // Also check that we did NOT create a notification channel
    assertThat(doesChannelExist(notificationManager, FCM_FALLBACK_CHANNEL)).isFalse();
  }

  @Test
  @Config(minSdk = 26, maxSdk = 28)
  public void channelUsed_Api26PlusTargetSdk26() throws Exception {
    setTargetSdkVersion(26);
    createDisplayNotification(context, createNotificationMessage(), executor).handleNotification();

    // If the app is using targetSdk >= 26, we MUST set the channel.
    // check that the notification was displayed, and that there is a channel associated to it.
    List<Notification> notifications = getNotifications(notificationManager);
    assertThat(notifications).hasSize(1);
    assertThat(getChannel(notifications.get(0))).isEqualTo(FCM_FALLBACK_CHANNEL);
  }

  @Test
  @Config(minSdk = 26, maxSdk = 28)
  public void defaultFcmChannel() throws Exception {
    setTargetSdkVersion(26);
    createDisplayNotification(context, createNotificationMessage(), executor).handleNotification();

    // If the app is using targetSdk >= 26, we MUST set the channel.
    // check that the notification was displayed, and that there is a channel associated to it.
    List<Notification> notifications = getNotifications(notificationManager);
    assertThat(notifications).hasSize(1);
    assertThat(getChannel(notifications.get(0))).isEqualTo(FCM_FALLBACK_CHANNEL);

    // Also check that we DID create a notification channel
    assertThat(doesChannelExist(notificationManager, FCM_FALLBACK_CHANNEL)).isTrue();
  }

  @Test
  @Config(minSdk = 26, maxSdk = 28)
  public void defaultFcmChannel_alreadyExisting() throws Exception {
    setTargetSdkVersion(26);
    createChannel(notificationManager, FCM_FALLBACK_CHANNEL, "label");
    createDisplayNotification(context, createNotificationMessage(), executor).handleNotification();

    // If the app is using targetSdk >= 26, we MUST set the channel.
    // check that the notification was displayed, and that there is a channel associated to it.
    List<Notification> notifications = getNotifications(notificationManager);
    assertThat(notifications).hasSize(1);
    assertThat(getChannel(notifications.get(0))).isEqualTo(FCM_FALLBACK_CHANNEL);
  }

  @Test
  @Config(minSdk = 26, maxSdk = 28)
  public void defaultManifestChannel_notExisting() throws Exception {
    setTargetSdkVersion(26);
    fakeManifestMetadata(METADATA_DEFAULT_CHANNEL, "notExistingManifestChannel");

    createDisplayNotification(context, createNotificationMessage(), executor).handleNotification();

    // Check that the notification was displayed
    List<Notification> notifications = getNotifications(notificationManager);
    assertThat(notifications).hasSize(1);
    // Check that it was assigned to the FCM_FALLBACK_CHANNEL, not to the notExistingChannel
    assertThat(getChannel(notifications.get(0))).isEqualTo(FCM_FALLBACK_CHANNEL);
    // Check that the notExistingChannel has NOT been created by mistake
    assertThat(doesChannelExist(notificationManager, "notExistingManifestChannel")).isFalse();
  }

  @Test
  @Config(minSdk = 26, maxSdk = 28)
  public void defaultManifestChannel_Existing() throws Exception {
    setTargetSdkVersion(26);
    fakeManifestMetadata(METADATA_DEFAULT_CHANNEL, "existingManifestChannel");
    createChannel(notificationManager, "existingManifestChannel", "label");

    createDisplayNotification(context, createNotificationMessage(), executor).handleNotification();

    // Check that the notification was displayed
    List<Notification> notifications = getNotifications(notificationManager);
    assertThat(notifications).hasSize(1);
    // Check that it was assigned to the existingManifestChannel
    assertThat(getChannel(notifications.get(0))).isEqualTo("existingManifestChannel");
  }

  @Test
  @Config(minSdk = 26, maxSdk = 28)
  public void messageProvidedChannel_notExisting() throws Exception {
    setTargetSdkVersion(26);
    Bundle message = createNotificationMessage();
    message.putString(KEY_CHANNEL, "notExistingMessageChannel");

    createDisplayNotification(context, message, executor).handleNotification();

    // Check that the notification was displayed
    List<Notification> notifications = getNotifications(notificationManager);
    assertThat(notifications).hasSize(1);
    // Check that it was assigned to the FCM_FALLBACK_CHANNEL, not to the notExistingChannel
    assertThat(getChannel(notifications.get(0))).isEqualTo(FCM_FALLBACK_CHANNEL);
    // Check that the notExistingChannel has NOT been created by mistake
    assertThat(doesChannelExist(notificationManager, "notExistingMessageChannel")).isFalse();
  }

  private static DisplayNotification createDisplayNotification(
      Context context, Bundle message, Executor executor) {
    return new DisplayNotification(context, new NotificationParams(message), executor);
  }

  @Test
  @Config(minSdk = 26, maxSdk = 28)
  public void messageProvidedChannel_Existing() throws Exception {
    setTargetSdkVersion(26);
    Bundle message = createNotificationMessage();
    message.putString(KEY_CHANNEL, "existingMessageChannel");
    createChannel(notificationManager, "existingMessageChannel", "label");

    createDisplayNotification(context, message, executor).handleNotification();

    // Check that the notification was displayed
    List<Notification> notifications = getNotifications(notificationManager);
    assertThat(notifications).hasSize(1);
    // Check that it was assigned to the existingManifestChannel
    assertThat(getChannel(notifications.get(0))).isEqualTo("existingMessageChannel");
  }

  /** Returns the notification channel id from a Notification object */
  static String getChannel(Notification notification) throws Exception {
    return (String) notification.getClass().getMethod("getChannel").invoke(notification);
  }

  /** Returns {@code true} true if channel exists */
  static boolean doesChannelExist(NotificationManager notificationManager, String channel)
      throws Exception {
    Object result =
        notificationManager
            .getClass()
            .getMethod("getNotificationChannel", String.class)
            .invoke(notificationManager, channel);
    return result != null;
  }

  /** return the notification channel from a Notification object */
  static void deleteChannel(NotificationManager notificationManager, String channel)
      throws Exception {
    notificationManager
        .getClass()
        .getMethod("deleteNotificationChannel", String.class)
        .invoke(notificationManager, channel);
  }

  /** create a notification channel with the provided name */
  static void createChannel(NotificationManager notificationManager, String name, String label)
      throws Exception {
    Class<?> klassNotificationChannel = Class.forName("android.app.NotificationChannel");
    Object notificationChannel =
        klassNotificationChannel
            .getConstructor(String.class, CharSequence.class, Integer.TYPE)
            .newInstance(name, label, NotificationManager.IMPORTANCE_DEFAULT);
    notificationManager
        .getClass()
        .getMethod("createNotificationChannel", klassNotificationChannel)
        .invoke(notificationManager, notificationChannel);
  }

  /** Replace the targetSdkVersion. */
  void setTargetSdkVersion(int version) throws Exception {
    PackageInfo packageInfo =
        context
            .getPackageManager()
            .getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
    ApplicationInfo appInfo = packageInfo.applicationInfo;

    appInfo.targetSdkVersion = version;

    shadowOf(context.getPackageManager()).installPackage(packageInfo);
  }

  /** set the metadata (k1,v1, k2, v2) in the Android Manifest */
  void fakeManifestMetadata(String key, String value) throws Exception {
    PackageInfo packageInfo =
        context
            .getPackageManager()
            .getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
    ApplicationInfo info = packageInfo.applicationInfo;

    if (info.metaData == null) {
      info.metaData = new Bundle();
    }

    info.metaData.putString(key, value);

    shadowOf(context.getPackageManager()).installPackage(packageInfo);
  }

  /** returns the array of notifications displayed in the notification tray */
  static List<Notification> getNotifications(NotificationManager notificationManager) {
    return shadowOf(notificationManager).getAllNotifications();
  }

  static void swipeAwayExistingNotifications(NotificationManager notificationManager)
      throws Exception {
    notificationManager.cancelAll();
    // NotificationManager.cancelAll() is async, give it the time to swipe away those notifications
    int i = 0;
    while (!shadowOf(notificationManager).getAllNotifications().isEmpty()) {
      Thread.sleep(100 /* ms */);
      i++;
      if (i > 50 /* 5 seconds */) {
        fail("NotificationManager is taking too long to cancel old notifications");
      }
    }
  }

  /** create a Bundle to simulate an incoming notification message */
  static Bundle createNotificationMessage() {
    Bundle bundle = new Bundle();
    bundle.putString(KEY_BODY, "Hello World");
    return bundle;
  }
}
