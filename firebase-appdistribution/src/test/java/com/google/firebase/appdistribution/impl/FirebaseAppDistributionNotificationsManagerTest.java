// Copyright 2021 Google LLC
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

package com.google.firebase.appdistribution.impl;

import static android.app.NotificationManager.IMPORTANCE_HIGH;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.os.Looper.getMainLooper;
import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.appdistribution.InterruptionLevel.DEFAULT;
import static com.google.firebase.appdistribution.impl.FirebaseAppDistributionNotificationsManager.CHANNEL_GROUP_ID;
import static com.google.firebase.appdistribution.impl.FirebaseAppDistributionNotificationsManager.NotificationType.APP_UPDATE;
import static com.google.firebase.appdistribution.impl.FirebaseAppDistributionNotificationsManager.NotificationType.FEEDBACK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.annotations.concurrent.UiThread;
import com.google.firebase.appdistribution.InterruptionLevel;
import com.google.firebase.concurrent.TestOnlyExecutors;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseAppDistributionNotificationsManagerTest {

  @UiThread private final Executor uiThreadExecutor = TestOnlyExecutors.ui();

  private FirebaseAppDistributionNotificationsManager firebaseAppDistributionNotificationsManager;
  private NotificationManager notificationManager;

  @Mock private FirebaseAppDistributionLifecycleNotifier mockLifecycleNotifier;

  @Lightweight @Mock private ScheduledExecutorService mockLightweightExecutorService;

  @Mock ScheduledFuture<?> mockScheduledFuture;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    notificationManager =
        (NotificationManager)
            ApplicationProvider.getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
    firebaseAppDistributionNotificationsManager =
        new FirebaseAppDistributionNotificationsManager(
            ApplicationProvider.getApplicationContext(),
            new AppIconSource(),
            mockLifecycleNotifier,
            mockLightweightExecutorService,
            uiThreadExecutor);
  }

  @Test
  public void showAppUpdateNotification_createsGroupAndChannel() {
    firebaseAppDistributionNotificationsManager.showAppUpdateNotification(
        1000, 900, R.string.downloading_app_update);
    assertThat(shadowOf(notificationManager).getNotificationChannelGroup(CHANNEL_GROUP_ID))
        .isNotNull();
    assertThat(shadowOf(notificationManager).getNotificationChannels()).hasSize(1);
    NotificationChannel channel =
        (NotificationChannel) shadowOf(notificationManager).getNotificationChannels().get(0);
    assertThat(channel.getId()).isEqualTo(APP_UPDATE.channelId);
  }

  @Test
  public void showAppUpdateNotification_withProgress() {
    firebaseAppDistributionNotificationsManager.showAppUpdateNotification(
        1000, 900, R.string.downloading_app_update);
    assertThat(shadowOf(notificationManager).size()).isEqualTo(1);
    Notification notification =
        shadowOf(notificationManager).getNotification(APP_UPDATE.tag, APP_UPDATE.id);
    assertThat(shadowOf(notification).getProgress()).isEqualTo(90);
    assertThat(shadowOf(notification).getContentTitle().toString()).contains("Downloading");
  }

  @Test
  public void showAppUpdateNotification_withError() {
    firebaseAppDistributionNotificationsManager.showAppUpdateNotification(
        1000, 1000, R.string.download_failed);
    assertThat(shadowOf(notificationManager).size()).isEqualTo(1);
    Notification notification =
        shadowOf(notificationManager).getNotification(APP_UPDATE.tag, APP_UPDATE.id);
    assertThat(shadowOf(notification).getProgress()).isEqualTo(100);
    assertThat(shadowOf(notification).getContentTitle().toString()).contains("Download failed");
  }

  @Test
  public void showAppUpdateNotification_withSuccess() {
    firebaseAppDistributionNotificationsManager.showAppUpdateNotification(
        1000, 1000, R.string.download_completed);
    assertThat(shadowOf(notificationManager).size()).isEqualTo(1);
    Notification notification =
        shadowOf(notificationManager).getNotification(APP_UPDATE.tag, APP_UPDATE.id);
    assertThat(shadowOf(notification).getProgress()).isEqualTo(100);
    assertThat(shadowOf(notification).getContentTitle().toString()).contains("Download complete");
  }

  @Test
  public void showAppUpdateNotification_withZeroTotalBytes_shows0Percent() {
    firebaseAppDistributionNotificationsManager.showAppUpdateNotification(
        0, 0, R.string.downloading_app_update);
    assertThat(shadowOf(notificationManager).size()).isEqualTo(1);
    Notification notification =
        shadowOf(notificationManager).getNotification(APP_UPDATE.tag, APP_UPDATE.id);
    assertThat(shadowOf(notification).getProgress()).isEqualTo(0);
  }

  @Test
  public void showFeedbackNotification_createsGroupAndChannel() {
    firebaseAppDistributionNotificationsManager.showFeedbackNotification(
        "Terms and conditions", InterruptionLevel.HIGH);

    shadowOf(getMainLooper()).idle(); // make sure main (UI) thread has finished executing
    assertThat(shadowOf(notificationManager).getNotificationChannelGroup(CHANNEL_GROUP_ID))
        .isNotNull();
    assertThat(shadowOf(notificationManager).getNotificationChannels()).hasSize(1);
    NotificationChannel channel =
        (NotificationChannel) shadowOf(notificationManager).getNotificationChannels().get(0);
    assertThat(channel.getImportance()).isEqualTo(IMPORTANCE_HIGH);
    assertThat(channel.getId()).isEqualTo(FEEDBACK.channelId);
  }

  @Test
  public void showFeedbackNotification_setsIntentToScreenshotActivity() {
    firebaseAppDistributionNotificationsManager.showFeedbackNotification(
        "Terms and conditions", InterruptionLevel.HIGH);

    shadowOf(getMainLooper()).idle(); // make sure main (UI) thread has finished executing
    assertThat(shadowOf(notificationManager).size()).isEqualTo(1);
    Notification notification =
        shadowOf(notificationManager).getNotification(FEEDBACK.tag, FEEDBACK.id);
    Intent expectedIntent =
        new Intent(
            ApplicationProvider.getApplicationContext(),
            TakeScreenshotAndStartFeedbackActivity.class);
    Intent actualIntent = shadowOf(notification.contentIntent).getSavedIntent();
    assertThat(actualIntent.getComponent()).isEqualTo(expectedIntent.getComponent());
    assertThat(
            actualIntent.getStringExtra(
                TakeScreenshotAndStartFeedbackActivity.ADDITIONAL_FORM_TEXT_EXTRA_KEY))
        .isEqualTo("Terms and conditions");
  }

  @Test
  public void showFeedbackNotification_convertsImportanceToPriority() {
    firebaseAppDistributionNotificationsManager.showFeedbackNotification(
        "Terms and conditions", InterruptionLevel.HIGH);

    shadowOf(getMainLooper()).idle(); // make sure main (UI) thread has finished executing
    assertThat(shadowOf(notificationManager).size()).isEqualTo(1);
    Notification notification =
        shadowOf(notificationManager).getNotification(FEEDBACK.tag, FEEDBACK.id);
    assertThat(notification.priority).isEqualTo(Notification.PRIORITY_HIGH);
  }

  @Test
  public void onPaused_schedulesHideFeedbackNotification() {
    when(mockLightweightExecutorService.schedule(any(Runnable.class), anyLong(), any()))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(0, Runnable.class).run(); // pretend enough time has passed
              return mockScheduledFuture;
            });
    firebaseAppDistributionNotificationsManager.showFeedbackNotification("feed me back", DEFAULT);
    shadowOf(getMainLooper()).idle(); // make sure main (UI) thread has finished executing

    firebaseAppDistributionNotificationsManager.onPaused(null);

    assertThat(shadowOf(notificationManager).size()).isEqualTo(0);
    verifyNoInteractions(mockScheduledFuture); // cancel did not get called
  }

  @Test
  public void onResumedBeforeTimeout_cancelsScheduledHideFeedbackNotification() {
    doReturn(mockScheduledFuture) // do not run runnable, pretend not enough time has passed
        .when(mockLightweightExecutorService)
        .schedule(any(Runnable.class), anyLong(), any());
    firebaseAppDistributionNotificationsManager.showFeedbackNotification("feed me back", DEFAULT);
    shadowOf(getMainLooper()).idle(); // make sure main (UI) thread has finished executing

    firebaseAppDistributionNotificationsManager.onPaused(null);
    firebaseAppDistributionNotificationsManager.onResumed(null);

    assertThat(shadowOf(notificationManager).size()).isEqualTo(1);
    verify(mockScheduledFuture).cancel(anyBoolean());
  }

  @Test
  public void onResumedAfterTimeout_showsFeedbackNotificationAgain() {
    when(mockLightweightExecutorService.schedule(any(Runnable.class), anyLong(), any()))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(0, Runnable.class).run(); // pretend enough time has passed
              return mockScheduledFuture;
            });
    firebaseAppDistributionNotificationsManager.showFeedbackNotification("feed me back", DEFAULT);
    shadowOf(getMainLooper()).idle(); // make sure main (UI) thread has finished executing

    firebaseAppDistributionNotificationsManager.onPaused(null);
    firebaseAppDistributionNotificationsManager.onResumed(null);

    assertThat(shadowOf(notificationManager).size()).isEqualTo(1);
  }

  @Test
  public void onPausedWithoutFeedbackNotification_noOp() {
    firebaseAppDistributionNotificationsManager.onPaused(null);
  }

  @Test
  public void onResumedWithoutFeedbackNotification_noOp() {
    firebaseAppDistributionNotificationsManager.onResumed(null);

    assertThat(shadowOf(notificationManager).size()).isEqualTo(0);
  }
}
