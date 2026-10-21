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

import android.app.Notification;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 26)
public class NotificationResponseRoboTest {

  private Notification notification;

  @Before
  public void setUp() {
    notification =
        new Notification.Builder(ApplicationProvider.getApplicationContext())
            .setContentTitle("Title")
            .setContentText("Text")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build();
  }

  @Test
  public void testCreatePost() {
    NotificationResponse response = NotificationResponse.create("tag", 1, notification);
    assertThat(response.getActions()).hasSize(1);
    NotificationResponse.NotificationAction action = response.getActions().get(0);
    assertThat(action.type).isEqualTo(NotificationResponse.ActionType.POST);
    assertThat(action.tag).isEqualTo("tag");
    assertThat(action.id).isEqualTo(1);
    assertThat(action.notification).isEqualTo(notification);
  }

  @Test
  public void testSuppress() {
    NotificationResponse response = NotificationResponse.suppress();
    assertThat(response.getActions()).isEmpty();
  }

  @Test
  public void testCreateCancel() {
    NotificationResponse response = NotificationResponse.createCancel("tag", 1);
    assertThat(response.getActions()).hasSize(1);
    NotificationResponse.NotificationAction action = response.getActions().get(0);
    assertThat(action.type).isEqualTo(NotificationResponse.ActionType.CANCEL);
    assertThat(action.tag).isEqualTo("tag");
    assertThat(action.id).isEqualTo(1);
    assertThat(action.notification).isNull();
  }

  @Test
  public void testAddAction() {
    NotificationResponse response = NotificationResponse.suppress();
    response.add("tag1", 1, notification);
    response.addCancel("tag2", 2);

    assertThat(response.getActions()).hasSize(2);
    assertThat(response.getActions().get(0).type).isEqualTo(NotificationResponse.ActionType.POST);
    assertThat(response.getActions().get(1).type).isEqualTo(NotificationResponse.ActionType.CANCEL);
  }
}
