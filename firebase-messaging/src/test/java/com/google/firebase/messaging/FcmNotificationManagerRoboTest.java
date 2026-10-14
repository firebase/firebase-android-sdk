// Copyright 2026 Google LLC
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
import static org.junit.Assert.assertThrows;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.messaging.Constants.MessagePayloadKeys;
import com.google.android.gms.cloudmessaging.NotificationCallback;
import com.google.android.gms.cloudmessaging.TestNotificationCallbacks;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Tests for {@link FcmNotificationManager}.
 *
 * <p>SDK coverage is chosen per test. One runs below O, since the module supports back to API 16;
 * the rest do not vary by version, so they pin to O.
 */
@RunWith(AndroidJUnit4.class)
@Config(manifest = Config.NONE)
public class FcmNotificationManagerRoboTest {

  private static final String APP_CHANNEL = "test_channel";
  private static final String TAG = "tag";
  private static final int ID = 42;

  private Context context;
  private FakeNotificationCallback playServices;
  private FcmNotificationManager manager;
  private Notification notification;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    setTargetSdkVersion(VERSION_CODES.O);

    playServices = new FakeNotificationCallback();
    manager = new FcmNotificationManager(sdkCallback(), context, (Bundle) null);

    notification = buildNotification(APP_CHANNEL);
  }

  /** Wraps the fake binder in the real callback the SDK hands to developers. */
  private NotificationCallback sdkCallback() {
    return TestNotificationCallbacks.create("msg_123", playServices);
  }

  /**
   * Builds a notification naming {@code channelId}. Below O the channel-taking constructor does not
   * exist, which is the situation {@link #postNow_belowO_postsNotificationUnchanged} covers.
   */
  @SuppressWarnings(
      "deprecation") // The channel-less constructor is the only one available below O.
  private Notification buildNotification(String channelId) {
    Notification.Builder builder =
        Build.VERSION.SDK_INT >= VERSION_CODES.O
            ? new Notification.Builder(context, channelId)
            : new Notification.Builder(context);
    return builder.setContentTitle("Title").setSmallIcon(android.R.drawable.ic_dialog_info).build();
  }

  private void createChannel(String channelId) {
    notificationManager()
        .createNotificationChannel(
            new NotificationChannel(channelId, "Test", NotificationManager.IMPORTANCE_DEFAULT));
  }

  private NotificationManager notificationManager() {
    // Looked up by name rather than by class, because getSystemService(Class) only exists from M
    // and one test runs below that.
    return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
  }

  /** The notification the platform actually received, or null if nothing was posted. */
  private Notification postedNotification() {
    return shadowOf(notificationManager()).getNotification(TAG, ID);
  }

  private void setTargetSdkVersion(int version) throws Exception {
    int flags =
        PackageManager.GET_META_DATA
            | PackageManager.GET_SERVICES
            | PackageManager.GET_ACTIVITIES
            | PackageManager.GET_RECEIVERS;
    PackageManager pm = context.getPackageManager();
    PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), flags);
    packageInfo.applicationInfo.targetSdkVersion = version;
    shadowOf(pm).installPackage(packageInfo);
  }

  @Test
  @Config(minSdk = VERSION_CODES.O)
  public void postNow_postsAndReportsToPlayServices() {
    createChannel(APP_CHANNEL);

    manager.postNow(TAG, ID, notification);

    assertThat(postedNotification()).isNotNull();
    assertThat(playServices.getPosted()).containsExactly("tag/42");
  }

  @Test
  @Config(minSdk = VERSION_CODES.O)
  public void postNow_channelExists_keepsRequestedChannel() {
    createChannel(APP_CHANNEL);

    manager.postNow(TAG, ID, notification);

    assertThat(postedNotification().getChannelId()).isEqualTo(APP_CHANNEL);
  }

  @Test
  @Config(minSdk = VERSION_CODES.O)
  public void postNow_channelMissing_doesNotReportToPlayServices() {
    manager.postNow(TAG, ID, notification);

    assertThat(playServices.getPosted()).isEmpty();
  }

  @Test
  @Config(minSdk = VERSION_CODES.O)
  public void postNow_channelImportanceNone_doesNotReportToPlayServices() {
    notificationManager()
        .createNotificationChannel(
            new NotificationChannel(APP_CHANNEL, "Test", NotificationManager.IMPORTANCE_NONE));

    manager.postNow(TAG, ID, notification);

    assertThat(playServices.getPosted()).isEmpty();
  }

  @Test
  @Config(minSdk = VERSION_CODES.O)
  public void postNow_targetSdkBelowO_channelMissing_reportsToPlayServices() throws Exception {
    setTargetSdkVersion(VERSION_CODES.N_MR1);

    manager.postNow(TAG, ID, notification);

    assertThat(playServices.getPosted()).containsExactly("tag/42");
  }

  @Test
  @Config(minSdk = Config.OLDEST_SDK, maxSdk = VERSION_CODES.N_MR1)
  public void postNow_belowO_postsNotificationUnchanged() {
    // The module still supports back to API 16, so cover the oldest version it runs on.
    manager.postNow(TAG, ID, notification);

    assertThat(postedNotification()).isSameInstanceAs(notification);
    assertThat(playServices.getPosted()).containsExactly("tag/42");
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void cancelNow_cancelsAndReportsToPlayServices() {
    createChannel(APP_CHANNEL);
    manager.postNow(TAG, ID, notification);
    assertThat(postedNotification()).isNotNull();

    manager.cancelNow(TAG, ID);

    assertThat(postedNotification()).isNull();
    assertThat(playServices.getCancelled()).containsExactly("tag/42");
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void finished_invalidatesManagerAndReturnsEmptyResponse() {
    NotificationResponse response = manager.finished();
    assertThat(response.getActions()).isEmpty();

    assertThrows(IllegalStateException.class, () -> manager.postNow(TAG, ID, notification));
    assertThrows(IllegalStateException.class, () -> manager.cancelNow(TAG, ID));
    assertThat(manager.finished().getActions()).isEmpty();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void expiredDeadline_rejectsFurtherUse() {
    Bundle payload = new Bundle();
    payload.putLong(MessagePayloadKeys.TIMEOUT_TIMESTAMP_MS, SystemClock.elapsedRealtime() - 1000);
    FcmNotificationManager expiredManager =
        new FcmNotificationManager(sdkCallback(), context, payload);

    assertThrows(IllegalStateException.class, () -> expiredManager.postNow(TAG, ID, notification));
    assertThrows(IllegalStateException.class, () -> expiredManager.cancelNow(TAG, ID));
    assertThat(playServices.getPosted()).isEmpty();
  }
}
