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
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_BODY;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_CHANNEL_ID;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_CLICK_ACTION;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_COLOR;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_DEFAULT_LIGHT_SETTINGS;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_DEFAULT_SOUND;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_DEFAULT_VIBRATE_TIMINGS;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_ENABLE;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_EVENT_TIME;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_ICON;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_IMAGE;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_LIGHT_SETTINGS;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_LINK;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_LINK_ANDROID;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_LOCAL_ONLY;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_NOTIFICATION_COUNT;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_NOTIFICATION_PRIORITY;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_PREFIX;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_PREFIX_OLD;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_SOUND_2;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_STICKY;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_TAG;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_TICKER;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_TITLE;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_VIBRATE_TIMINGS;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.KEY_VISIBILITY;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.TEXT_ARGS_SUFFIX;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.TEXT_RESOURCE_SUFFIX;
import static com.google.firebase.messaging.DisplayNotificationRoboTest.jsonArray;
import static com.google.firebase.messaging.RemoteMessage.PRIORITY_HIGH;
import static com.google.firebase.messaging.RemoteMessage.PRIORITY_NORMAL;
import static com.google.firebase.messaging.RemoteMessage.PRIORITY_UNKNOWN;
import static com.google.firebase.messaging.RemoteMessageBuilder.EXTRA_DELIVERED_PRIORITY;
import static com.google.firebase.messaging.RemoteMessageBuilder.EXTRA_ORIGINAL_PRIORITY;
import static com.google.firebase.messaging.RemoteMessageBuilder.EXTRA_PRIORITY_REDUCED_V19;
import static com.google.firebase.messaging.RemoteMessageBuilder.EXTRA_PRIORITY_V19;
import static com.google.firebase.messaging.RemoteMessageBuilder.EXTRA_SENDER_ID;
import static com.google.firebase.messaging.RemoteMessageBuilder.EXTRA_SENT_TIME;
import static com.google.firebase.messaging.RemoteMessageBuilder.messagesEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.Constants.MessagePayloadKeys;
import com.google.firebase.messaging.testing.FirebaseIidRoboTestHelper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Remote message tests. */
@RunWith(RobolectricTestRunner.class)
public class RemoteMessageRoboTest {

  private static final String EXTRA_TTL = "google.ttl";
  private static final long[] VIBRATE_TIMINGS_TEST = {1000L, 2000L, 3000L};

  private FirebaseInstanceId iid;

  @Before
  public void setUp() {
    // Clear static singleton instances
    FirebaseApp.clearInstancesForTest();
    FirebaseInstanceId.clearInstancesForTest();
    iid = FirebaseIidRoboTestHelper.initMockFirebaseIid(RuntimeEnvironment.application);
  }

  @Test
  public void testBuilder() {
    doReturn("default_token").when(iid).getToken();
    final byte[] rawData = {42, 123, 0, 1};
    RemoteMessage message =
        new RemoteMessage.Builder("test_to")
            .addData("key", "value")
            .setRawData(rawData)
            .setMessageId("test_message_id")
            .setMessageType("test_message_type")
            .setTtl(123)
            .setCollapseKey("test_collapse_key")
            .build();
    assertEquals("test_to", message.getTo());
    assertNull("default_token", message.getFrom());
    assertNull(message.getSenderId());
    assertEquals(1, message.getData().size());
    assertEquals("value", message.getData().get("key"));
    assertEquals("test_message_id", message.getMessageId());
    assertEquals("test_message_type", message.getMessageType());
    assertEquals(0 /* will be set when sent */, message.getSentTime());
    assertEquals(123, message.getTtl());
    assertEquals("test_collapse_key", message.getCollapseKey());
    assertNull(message.getNotification());
    assertEquals(PRIORITY_UNKNOWN, message.getOriginalPriority());
    assertEquals(PRIORITY_UNKNOWN, message.getPriority());
  }

  /**
   * Test that we don't NPE if getToken() is returning null.
   *
   * <p>This will only happen in real usage of the API if send is called while the app is getting
   * its initial token or during a rotation.
   */
  @Test
  public void testFrom_noToken() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    assertNull(message.getFrom());
  }

  @Test
  public void testSenderId_noFound() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    assertNull(message.getSenderId());
  }

  @Test
  public void testFrom_string() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_SENDER_ID, "123456");
    assertEquals("123456", message.getSenderId());
  }

  @Test
  public void testTtlUsesStringInSendIntents() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").setTtl(123).build();
    Intent intent = new Intent();
    message.populateSendMessageIntent(intent);
    assertEquals("123", intent.getStringExtra(EXTRA_TTL));
  }

  @Test
  public void testGetSentTime_string() {
    final long time = 10_000_000_000L; // larger than an int
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_SENT_TIME, String.valueOf(time));
    assertEquals(time, message.getSentTime());
  }

  @Test
  public void testGetSentTime_long() {
    final long time = 20_000_000_000L; // larger than an int
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putLong(EXTRA_SENT_TIME, time);
    assertEquals(time, message.getSentTime());
  }

  @Test
  public void testGetSentTime_default() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    assertEquals(0, message.getSentTime());
  }

  @Test
  public void testGetSentTime_badString() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_SENT_TIME, "abc");
    assertEquals(0, message.getSentTime());
  }

  @Test
  public void testGetTtl_string() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_TTL, "1234");
    assertEquals(1234, message.getTtl());
  }

  @Test
  public void testGetTtl_int() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putInt(EXTRA_TTL, 12345);
    assertEquals(12345, message.getTtl());
  }

  @Test
  public void testGetTtl_default() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    assertEquals(0, message.getTtl());
  }

  @Test
  public void testGetTtl_badString() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_TTL, "abc");
    assertEquals(0, message.getTtl());
  }

  @Test
  public void testGetOriginalPriority_notFound() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    assertEquals(PRIORITY_UNKNOWN, message.getOriginalPriority());
  }

  @Test
  public void testGetOriginalPriority_high() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_ORIGINAL_PRIORITY, "high");
    assertEquals(PRIORITY_HIGH, message.getOriginalPriority());
  }

  @Test
  public void testGetOriginalPriority_high_v19() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_PRIORITY_V19, "high");
    assertEquals(PRIORITY_HIGH, message.getOriginalPriority());
  }

  @Test
  public void testGetOriginalPriority_normal() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_ORIGINAL_PRIORITY, "normal");
    assertEquals(PRIORITY_NORMAL, message.getOriginalPriority());
  }

  @Test
  public void testGetOriginalPriority_normal_v19() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_PRIORITY_V19, "normal");
    assertEquals(PRIORITY_NORMAL, message.getOriginalPriority());
  }

  @Test
  public void testGetOriginalPriorityy_badString() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_ORIGINAL_PRIORITY, "test");
    assertEquals(PRIORITY_UNKNOWN, message.getOriginalPriority());
  }

  @Test
  public void testGetOriginalPriorityy_badString_v19() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_PRIORITY_V19, "test");
    assertEquals(PRIORITY_UNKNOWN, message.getOriginalPriority());
  }

  @Test
  public void testGetPriority_notFound() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    assertEquals(PRIORITY_UNKNOWN, message.getPriority());
  }

  @Test
  public void testGetPriority_high() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_DELIVERED_PRIORITY, "high");
    assertEquals(PRIORITY_HIGH, message.getPriority());
  }

  @Test
  public void testGetPriority_high_v19() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_PRIORITY_V19, "high");
    assertEquals(PRIORITY_HIGH, message.getPriority());
  }

  @Test
  public void testGetPriority_normal() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_DELIVERED_PRIORITY, "normal");
    assertEquals(PRIORITY_NORMAL, message.getPriority());
  }

  @Test
  public void testGetPriority_normal_v19_1() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_PRIORITY_V19, "high");
    message.bundle.putString(EXTRA_PRIORITY_REDUCED_V19, "1");
    assertEquals(PRIORITY_NORMAL, message.getPriority());
  }

  @Test
  public void testGetPriority_normal_v19_2() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_PRIORITY_V19, "normal");
    assertEquals(PRIORITY_NORMAL, message.getPriority());
  }

  @Test
  public void testGetPriority_badString() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_DELIVERED_PRIORITY, "test");
    assertEquals(PRIORITY_UNKNOWN, message.getPriority());
  }

  @Test
  public void testGetPriority_badString_v19() {
    RemoteMessage message = new RemoteMessage.Builder("test_to").build();
    message.bundle.putString(EXTRA_PRIORITY_V19, "test");
    message.bundle.putString(EXTRA_PRIORITY_REDUCED_V19, "test");
    assertEquals(PRIORITY_UNKNOWN, message.getPriority());
  }

  @Test
  public void testToIntent() {
    RemoteMessage message =
        new RemoteMessage.Builder("test_to")
            .addData("key", "value")
            .setMessageId("test_message_id")
            .setMessageType("test_message_type")
            .setTtl(123)
            .setCollapseKey("test_collapse_key")
            .build();
    Bundle bundle = message.toIntent().getExtras();
    RemoteMessage newMessage = new RemoteMessage(bundle);
    assertEquals(message.getTo(), newMessage.getTo());
    assertEquals(message.getMessageId(), newMessage.getMessageId());
    assertEquals(message.getMessageType(), newMessage.getMessageType());
    assertEquals(message.getTtl(), newMessage.getTtl());
    assertEquals(message.getCollapseKey(), newMessage.getCollapseKey());

    assertEquals(1, newMessage.getData().size());
    assertEquals("value", newMessage.getData().get("key"));
  }

  @Test
  public void testToIntent_fieldsThatDoNotHaveSetters() {
    Bundle bundle = new Bundle();
    bundle.putString(MessagePayloadKeys.TO, "test_to");
    bundle.putString(MessagePayloadKeys.SENT_TIME, "31415");
    bundle.putString(MessagePayloadKeys.FROM, "test_from");
    RemoteMessage message = new RemoteMessage(bundle);

    Bundle newBundle = message.toIntent().getExtras();
    assertEquals("31415", newBundle.getString(MessagePayloadKeys.SENT_TIME));
    assertEquals("test_from", newBundle.getString(MessagePayloadKeys.FROM));
  }

  @Test
  public void testClearData() {
    RemoteMessage message =
        new RemoteMessage.Builder("test_to").addData("key", "value").clearData().build();
    assertTrue(message.getData().isEmpty());
  }

  @Test
  public void testSetData() {
    Map<String, String> data = new HashMap<>();
    data.put("abc", "123");
    data.put("def", "456");

    RemoteMessage message =
        new RemoteMessage.Builder("test_to")
            .addData("key", "value")
            .setData(data) // should clobber previous key/value
            .build();
    assertEquals(data, message.getData());
  }

  @Test
  public void testParcel() {
    RemoteMessage message =
        new RemoteMessage.Builder("test_to")
            .addData("key", "value")
            .setRawData(new byte[] {1, 4, 9, 16, 25})
            .setMessageId("test_message_id")
            .setMessageType("test_message_type")
            .setTtl(123)
            .setCollapseKey("test_collapse_key")
            .build();

    Parcel parcel = Parcel.obtain();
    parcel.writeParcelable(message, 0 /* flags */);

    parcel.setDataPosition(0);
    RemoteMessage parcelledMessage = parcel.readParcelable(null);
    assertTrue(messagesEqual(message, parcelledMessage));
  }

  /** Test the "message_id" sent from the server is used for getMessageId() */
  @Test
  public void testMessageIdServer() {
    Bundle bundle = new Bundle();
    bundle.putString("message_id", "abc123");
    RemoteMessage message = new RemoteMessage(bundle);
    assertEquals("abc123", message.getMessageId());
  }

  @Test
  public void testNotification() {
    doTestNotification(true);
  }

  @Test
  public void testNotification_oldPrefix() {
    doTestNotification(false);
  }

  private void doTestNotification(boolean newPrefix) {
    String[][] params = {
      {KEY_ENABLE, "1"},
      {KEY_TITLE, "a_title"},
      {KEY_BODY, "a_body"},
      {KEY_ICON, "an_icon"},
      {KEY_SOUND_2, "a_sound"},
      {KEY_COLOR, "#123456"},
      {KEY_CLICK_ACTION, "a_click_action"},
      {KEY_CHANNEL_ID, "a_channel_id"},
      {KEY_TAG, "a_tag"},
      {KEY_IMAGE, "https://an.image"},
      {KEY_TICKER, "ticker"},
      {KEY_NOTIFICATION_PRIORITY, "1"},
      {KEY_VISIBILITY, "2"},
      {KEY_NOTIFICATION_COUNT, "3"},
      {KEY_EVENT_TIME, "123"},
      {KEY_STICKY, "true"},
      {KEY_LOCAL_ONLY, "true"},
      {KEY_DEFAULT_SOUND, "true"},
      {KEY_DEFAULT_LIGHT_SETTINGS, "true"},
      {KEY_DEFAULT_VIBRATE_TIMINGS, "true"},
      {KEY_LIGHT_SETTINGS, "[\"#FFFFFFFF\",\"2000\",\"3000\"]"},
      {KEY_VIBRATE_TIMINGS, "[\"1000\",\"2000\",\"3000\"]"}
    };
    Bundle bundle = new Bundle();
    for (String[] keyValue : params) {
      String key = keyValue[0];
      if (!newPrefix) {
        key = key.replace(KEY_PREFIX, KEY_PREFIX_OLD);
      }
      bundle.putString(key, keyValue[1]);
    }

    int[] lightSettingsExpected = new int[] {0xFFFFFFFF, 2000, 3000};

    RemoteMessage message = new RemoteMessage(bundle);
    RemoteMessage.Notification n = message.getNotification();
    assertEquals("a_title", n.getTitle());
    assertNull(n.getTitleLocalizationKey());
    assertNull(n.getTitleLocalizationArgs());
    assertEquals("a_body", n.getBody());
    assertNull(n.getBodyLocalizationKey());
    assertNull(n.getBodyLocalizationArgs());
    assertEquals("an_icon", n.getIcon());
    assertEquals("a_sound", n.getSound());
    assertEquals("#123456", n.getColor());
    assertEquals("a_click_action", n.getClickAction());
    assertEquals("a_channel_id", n.getChannelId());
    assertEquals("a_tag", n.getTag());
    assertThat(n.getImageUrl().toString()).isEqualTo("https://an.image");
    assertThat(n.getTicker()).isEqualTo("ticker");
    assertThat(n.getSticky()).isTrue();
    assertThat(n.getLocalOnly()).isTrue();
    assertThat(n.getVisibility()).isEqualTo(2);
    assertThat(n.getNotificationCount()).isEqualTo(3);
    assertThat(n.getEventTime()).isEqualTo(123L);
    assertThat(n.getDefaultLightSettings()).isTrue();
    assertThat(n.getDefaultVibrateSettings()).isTrue();
    assertThat(n.getDefaultSound()).isTrue();
    assertThat(n.getVibrateTimings()).isEqualTo(VIBRATE_TIMINGS_TEST);
    assertThat(n.getLightSettings()).isEqualTo(lightSettingsExpected);
    assertNull(n.getLink());
  }

  @Test
  public void testNotification_localization() {
    String[] titleArgs = new String[] {"1", "2", "3"};
    String[] bodyArgs = new String[] {"1", "2", "3"};

    Bundle bundle = new Bundle();
    bundle.putString(KEY_ENABLE, "1");
    bundle.putString(KEY_TITLE + TEXT_RESOURCE_SUFFIX, "title_resource");
    bundle.putString(KEY_TITLE + TEXT_ARGS_SUFFIX, jsonArray(titleArgs));
    bundle.putString(KEY_BODY + TEXT_RESOURCE_SUFFIX, "body_resource");
    bundle.putString(KEY_BODY + TEXT_ARGS_SUFFIX, jsonArray(bodyArgs));

    RemoteMessage message = new RemoteMessage(bundle);
    RemoteMessage.Notification n = message.getNotification();
    assertEquals("title_resource", n.getTitleLocalizationKey());
    assertTrue(Arrays.equals(titleArgs, n.getTitleLocalizationArgs()));
    assertEquals("body_resource", n.getBodyLocalizationKey());
    assertTrue(Arrays.equals(bodyArgs, n.getBodyLocalizationArgs()));
  }

  /**
   * Test that link is retrieved correctly.
   *
   * <p>Don't reuse testNotification as click action and links are mutually exclusive.
   */
  @Test
  public void testNotification_link() {
    Bundle bundle = new Bundle();
    bundle.putString(KEY_ENABLE, "1");
    bundle.putString(KEY_LINK, "https://www.example.com");

    RemoteMessage message = new RemoteMessage(bundle);
    assertEquals("https://www.example.com", message.getNotification().getLink().toString());
  }

  /**
   * Test that link android override is retrieved correctly.
   *
   * <p>Don't reuse testNotification as click action and links are mutually exclusive.
   */
  @Test
  public void testNotification_linkAndroid() {
    Bundle bundle = new Bundle();
    bundle.putString(KEY_ENABLE, "1");
    bundle.putString(KEY_LINK, "https://www.example.com");
    bundle.putString(KEY_LINK_ANDROID, "https://www.android.com");

    RemoteMessage message = new RemoteMessage(bundle);
    assertEquals("https://www.android.com", message.getNotification().getLink().toString());
  }

  @Test
  public void testNotification_imageUrl() {
    Bundle bundle = new Bundle();
    bundle.putString(KEY_ENABLE, "1");
    bundle.putString(KEY_IMAGE, "an_image_thing");

    RemoteMessage message = new RemoteMessage(bundle);
    assertThat(message.getNotification().getImageUrl().toString()).isEqualTo("an_image_thing");
  }

  @Test
  public void testNotification_noImageUrl() {
    Bundle bundle = new Bundle();
    bundle.putString(KEY_ENABLE, "1");

    RemoteMessage message = new RemoteMessage(bundle);
    assertThat(message.getNotification().getImageUrl()).isNull();
  }
}
