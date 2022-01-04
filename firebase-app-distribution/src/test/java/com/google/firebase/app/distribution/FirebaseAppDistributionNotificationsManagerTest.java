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

package com.google.firebase.app.distribution;

import static android.content.Context.NOTIFICATION_SERVICE;
import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.app.distribution.FirebaseAppDistributionNotificationsManager.NOTIFICATION_TAG;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationManager;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseAppDistributionNotificationsManagerTest {

  private FirebaseAppDistributionNotificationsManager firebaseAppDistributionNotificationsManager;
  private NotificationManager notificationManager;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    notificationManager =
        (NotificationManager)
            ApplicationProvider.getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
    firebaseAppDistributionNotificationsManager =
        new FirebaseAppDistributionNotificationsManager(
            ApplicationProvider.getApplicationContext());
  }

  @Test
  public void updateNotification_withProgress() {
    firebaseAppDistributionNotificationsManager.updateNotification(
        1000, 900, UpdateStatus.DOWNLOADING);
    assertThat(shadowOf(notificationManager).size()).isEqualTo(1);
    Notification notification = shadowOf(notificationManager).getNotification(NOTIFICATION_TAG, 0);
    assertThat(shadowOf(notification).getProgress()).isEqualTo(90);
    assertThat(shadowOf(notification).getContentTitle().toString()).contains("Downloading");
  }

  @Test
  public void updateNotification_withError() {
    firebaseAppDistributionNotificationsManager.updateNotification(
        1000, 1000, UpdateStatus.DOWNLOAD_FAILED);
    assertThat(shadowOf(notificationManager).size()).isEqualTo(1);
    Notification notification = shadowOf(notificationManager).getNotification(NOTIFICATION_TAG, 0);
    assertThat(shadowOf(notification).getProgress()).isEqualTo(100);
    assertThat(shadowOf(notification).getContentTitle().toString()).contains("Download failed");
  }

  @Test
  public void updateNotification_withSuccess() {
    firebaseAppDistributionNotificationsManager.updateNotification(
        1000, 1000, UpdateStatus.DOWNLOADED);
    assertThat(shadowOf(notificationManager).size()).isEqualTo(1);
    Notification notification = shadowOf(notificationManager).getNotification(NOTIFICATION_TAG, 0);
    assertThat(shadowOf(notification).getProgress()).isEqualTo(100);
    assertThat(shadowOf(notification).getContentTitle().toString()).contains("Download completed");
  }

  @Test
  public void updateNotification_withZeroTotalBytes_shows0Percent() {
    firebaseAppDistributionNotificationsManager.updateNotification(0, 0, UpdateStatus.DOWNLOADING);
    assertThat(shadowOf(notificationManager).size()).isEqualTo(1);
    Notification notification = shadowOf(notificationManager).getNotification(NOTIFICATION_TAG, 0);
    assertThat(shadowOf(notification).getProgress()).isEqualTo(0);
  }
}
