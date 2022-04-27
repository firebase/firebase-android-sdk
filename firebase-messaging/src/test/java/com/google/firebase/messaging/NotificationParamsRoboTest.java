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

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.Constants.MessageNotificationKeys;
import com.google.firebase.messaging.testing.Bundles;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class NotificationParamsRoboTest {

  @Before
  public void setUp() throws Exception {}

  @Test
  public void getNotificationCount_negativeNumber() {
    NotificationParams params =
        new NotificationParams(Bundles.of(MessageNotificationKeys.NOTIFICATION_COUNT, "-1"));

    assertThat(params.getNotificationCount()).isNull();
  }

  @Test
  public void getNotificationCount_nonNumber() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(MessageNotificationKeys.NOTIFICATION_COUNT, "not_a_number"));

    assertThat(params.getNotificationCount()).isNull();
  }

  @Test
  public void getNotificationCount_zero() {
    NotificationParams params =
        new NotificationParams(Bundles.of(MessageNotificationKeys.NOTIFICATION_COUNT, "0"));

    assertThat(params.getNotificationCount()).isEqualTo(0);
  }

  @Test
  public void getNotificationCount_someNumber() {
    NotificationParams params =
        new NotificationParams(Bundles.of(MessageNotificationKeys.NOTIFICATION_COUNT, "7"));

    assertThat(params.getNotificationCount()).isEqualTo(7);
  }

  @Test
  public void getNotificationPriority_tooLow() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(
                MessageNotificationKeys.NOTIFICATION_PRIORITY,
                String.valueOf(NotificationCompat.PRIORITY_MIN - 1)));

    assertThat(params.getNotificationPriority()).isNull();
  }

  @Test
  public void getNotificationPriority_tooHigh() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(
                MessageNotificationKeys.NOTIFICATION_PRIORITY,
                String.valueOf(NotificationCompat.PRIORITY_MAX + 1)));

    assertThat(params.getNotificationPriority()).isNull();
  }

  @Test
  public void getNotificationPriority_default() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(
                MessageNotificationKeys.NOTIFICATION_PRIORITY,
                String.valueOf(NotificationCompat.PRIORITY_DEFAULT)));

    assertThat(params.getNotificationPriority()).isEqualTo(NotificationCompat.PRIORITY_DEFAULT);
  }

  @Test
  public void getNotificationPriority_high() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(
                MessageNotificationKeys.NOTIFICATION_PRIORITY,
                String.valueOf(NotificationCompat.PRIORITY_HIGH)));

    assertThat(params.getNotificationPriority()).isEqualTo(NotificationCompat.PRIORITY_HIGH);
  }

  @Test
  public void getNotificationPriority_low() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(
                MessageNotificationKeys.NOTIFICATION_PRIORITY,
                String.valueOf(NotificationCompat.PRIORITY_LOW)));

    assertThat(params.getNotificationPriority()).isEqualTo(NotificationCompat.PRIORITY_LOW);
  }

  @Test
  public void getNotificationPriority_min() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(
                MessageNotificationKeys.NOTIFICATION_PRIORITY,
                String.valueOf(NotificationCompat.PRIORITY_MIN)));

    assertThat(params.getNotificationPriority()).isEqualTo(NotificationCompat.PRIORITY_MIN);
  }

  @Test
  public void getNotificationPriority_max() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(
                MessageNotificationKeys.NOTIFICATION_PRIORITY,
                String.valueOf(NotificationCompat.PRIORITY_MAX)));

    assertThat(params.getNotificationPriority()).isEqualTo(NotificationCompat.PRIORITY_MAX);
  }

  @Test
  public void getVisibility_private() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(
                MessageNotificationKeys.VISIBILITY,
                String.valueOf(NotificationCompat.VISIBILITY_PRIVATE)));

    assertThat(params.getVisibility()).isEqualTo(NotificationCompat.VISIBILITY_PRIVATE);
  }

  @Test
  public void getVisibility_public() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(
                MessageNotificationKeys.VISIBILITY,
                String.valueOf(NotificationCompat.VISIBILITY_PUBLIC)));

    assertThat(params.getVisibility()).isEqualTo(NotificationCompat.VISIBILITY_PUBLIC);
  }

  @Test
  public void getVisibility_secret() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(
                MessageNotificationKeys.VISIBILITY,
                String.valueOf(NotificationCompat.VISIBILITY_SECRET)));

    assertThat(params.getVisibility()).isEqualTo(NotificationCompat.VISIBILITY_SECRET);
  }

  @Test
  public void getVisibility_tooHigh() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(
                MessageNotificationKeys.VISIBILITY,
                String.valueOf(NotificationCompat.VISIBILITY_PUBLIC + 1)));

    assertThat(params.getVisibility()).isNull();
  }

  @Test
  public void getVisibility_tooLow() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(
                MessageNotificationKeys.VISIBILITY,
                String.valueOf(NotificationCompat.VISIBILITY_SECRET - 1)));

    assertThat(params.getVisibility()).isNull();
  }

  @Test
  public void getString_honorsOldPrefix() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(MessageNotificationKeys.NOTIFICATION_PREFIX_OLD + "some_key", "some_value"));

    assertThat(params.getString(MessageNotificationKeys.NOTIFICATION_PREFIX + "some_key"))
        .isEqualTo("some_value");
  }

  @Test
  public void getString_honorsNewPrefix() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(MessageNotificationKeys.NOTIFICATION_PREFIX + "some_key", "some_value"));

    assertThat(params.getString(MessageNotificationKeys.NOTIFICATION_PREFIX + "some_key"))
        .isEqualTo("some_value");
  }

  @Test
  public void getBoolean_unsetIsFalse() {
    NotificationParams params = new NotificationParams(Bundle.EMPTY);

    assertThat(params.getBoolean("anything")).isFalse();
  }

  @Test
  public void getBoolean_oneIsTrue() {
    NotificationParams params = new NotificationParams(Bundles.of("b", "1"));

    assertThat(params.getBoolean("b")).isTrue();
  }

  @Test
  public void getBoolean_trueIsTrue() {
    NotificationParams params = new NotificationParams(Bundles.of("b", "true"));

    assertThat(params.getBoolean("b")).isTrue();
  }

  @Test
  public void getBoolean_nonsenseIsFalse() {
    NotificationParams params = new NotificationParams(Bundles.of("b", "nonsense"));

    assertThat(params.getBoolean("b")).isFalse();
  }

  @Test
  public void getBoolean_emptyStringIsFalse() {
    NotificationParams params = new NotificationParams(Bundles.of("b", ""));

    assertThat(params.getBoolean("b")).isFalse();
  }

  @Test
  public void getInteger_badInt() {
    NotificationParams params = new NotificationParams(Bundles.of("i", "3.14"));

    assertThat(params.getInteger("i")).isNull();
  }

  @Test
  public void getInteger_emptyInt() {
    NotificationParams params = new NotificationParams(Bundles.of("i", ""));

    assertThat(params.getInteger("i")).isNull();
  }

  @Test
  public void getInteger_validInt() {
    NotificationParams params = new NotificationParams(Bundles.of("i", "7"));

    assertThat(params.getInteger("i")).isEqualTo(7);
  }

  @Test
  public void getLong_badLong() {
    NotificationParams params = new NotificationParams(Bundles.of("i", "3.14"));

    assertThat(params.getLong("i")).isNull();
  }

  @Test
  public void getLong_emptyLong() {
    NotificationParams params = new NotificationParams(Bundles.of("i", ""));

    assertThat(params.getLong("i")).isNull();
  }

  @Test
  public void getLong_validLong() {
    NotificationParams params = new NotificationParams(Bundles.of("i", "1337"));

    assertThat(params.getLong("i")).isEqualTo(1337L);
  }

  @Test
  public void getLocalizationResourceForKey() {
    NotificationParams params =
        new NotificationParams(Bundles.of("totranslate_loc_key", "resource_key"));

    assertThat(params.getLocalizationResourceForKey("totranslate")).isEqualTo("resource_key");
  }

  @Test
  public void getLocalizationArgsForKey_validArgs() {
    NotificationParams params =
        new NotificationParams(Bundles.of("totranslate_loc_args", "[\"arg1\"," + " \"arg2\"]"));

    assertThat(params.getLocalizationArgsForKey("totranslate"))
        .asList()
        .containsExactly("arg1", "arg2")
        .inOrder();
  }

  @Test
  public void getLocalizationArgsForKey_emptyArgs() {
    NotificationParams params = new NotificationParams(Bundles.of("totranslate_loc_args", "[]"));

    assertThat(params.getLocalizationArgsForKey("totranslate")).asList().isEmpty();
  }

  @Test
  public void getLocalizationArgsForKey_emptyString() {
    NotificationParams params = new NotificationParams(Bundles.of("totranslate_loc_args", ""));

    assertThat(params.getLocalizationArgsForKey("totranslate")).isNull();
  }

  @Test
  public void getLocalizationArgsForKey_malformedJson() {
    NotificationParams params =
        new NotificationParams(Bundles.of("totranslate_loc_args", "this isn't" + " json"));

    assertThat(params.getLocalizationArgsForKey("totranslate")).isNull();
  }

  @Test
  public void getJSONArray_emptyArray() throws JSONException {
    NotificationParams params = new NotificationParams(Bundles.of("a", "[]"));

    assertThat(toList(params.getJSONArray("a"))).isEmpty();
  }

  @Test
  public void getJSONArray_arrayWithNonStrings() throws JSONException {
    NotificationParams params = new NotificationParams(Bundles.of("a", "[1, 2, 3, \"foo\"]"));

    assertThat(toList(params.getJSONArray("a"))).containsExactly(1, 2, 3, "foo").inOrder();
  }

  @Test
  public void getJSONArray_emptyString() throws JSONException {
    NotificationParams params = new NotificationParams(Bundles.of("a", ""));

    assertThat(toList(params.getJSONArray("a"))).isNull();
  }

  @Test
  public void getLink_androidLink() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(MessageNotificationKeys.LINK_ANDROID, "https://google.com/android"));

    assertThat(params.getLink()).isEqualTo(Uri.parse("https://google.com/android"));
  }

  @Test
  public void getLink_regularLink() {
    NotificationParams params =
        new NotificationParams(Bundles.of(MessageNotificationKeys.LINK, "https://google.com"));

    assertThat(params.getLink()).isEqualTo(Uri.parse("https://google.com"));
  }

  @Test
  public void getLink_bothAndroidAndRegularLinks() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(
                MessageNotificationKeys.LINK_ANDROID, "https://developer.android.com",
                MessageNotificationKeys.LINK, "https://google.com"));

    assertThat(params.getLink()).isEqualTo(Uri.parse("https://developer.android.com"));
  }

  @Test
  public void getLink_noLink() {
    NotificationParams params = new NotificationParams(Bundle.EMPTY);

    assertThat(params.getLink()).isNull();
  }

  @Test
  public void getSoundResourceName_sound1() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(MessageNotificationKeys.SOUND, "never_gonna_give_you_up.mp3"));

    assertThat(params.getSoundResourceName()).isEqualTo("never_gonna_give_you_up.mp3");
  }

  @Test
  public void getSoundResourceName_sound2() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(MessageNotificationKeys.SOUND_2, "never_gonna_give_you_up.mp3"));

    assertThat(params.getSoundResourceName()).isEqualTo("never_gonna_give_you_up.mp3");
  }

  @Test
  public void getSoundResourceName_bothSound1AndSound2() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(
                MessageNotificationKeys.SOUND, "a.mp3",
                MessageNotificationKeys.SOUND_2, "b.mp3"));

    assertThat(params.getSoundResourceName()).isEqualTo("b.mp3");
  }

  @Test
  public void getSoundResourceName_noSound() {
    NotificationParams params = new NotificationParams(Bundle.EMPTY);

    assertThat(params.getSoundResourceName()).isNull();
  }

  @Test
  public void getVibrateTimings_emptyArray() {
    NotificationParams params =
        new NotificationParams(Bundles.of(MessageNotificationKeys.VIBRATE_TIMINGS, "[]"));

    assertThat(params.getVibrateTimings()).isNull();
  }

  @Test
  public void getVibrateTimings_emptyString() {
    NotificationParams params =
        new NotificationParams(Bundles.of(MessageNotificationKeys.VIBRATE_TIMINGS, ""));

    assertThat(params.getVibrateTimings()).isNull();
  }

  @Test
  public void getVibrateTimings_validTimings() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(MessageNotificationKeys.VIBRATE_TIMINGS, "[100, 200, 100]"));

    assertThat(params.getVibrateTimings()).asList().containsExactly(100L, 200L, 100L).inOrder();
  }

  @Test
  public void getLightSettings_emptyString() {
    NotificationParams params =
        new NotificationParams(Bundles.of(MessageNotificationKeys.LIGHT_SETTINGS, ""));

    assertThat(params.getLightSettings()).isNull();
  }

  @Test
  public void getLightSettings_emptyArray() {
    NotificationParams params =
        new NotificationParams(Bundles.of(MessageNotificationKeys.LIGHT_SETTINGS, "[]"));

    assertThat(params.getLightSettings()).isNull();
  }

  @Test
  public void getLightSettings_badArrayContents() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(MessageNotificationKeys.LIGHT_SETTINGS, "[\"a\", \"b\", \"c\"]"));

    assertThat(params.getLightSettings()).isNull();
  }

  @Test
  public void getLightSettings_validArray() {
    NotificationParams params =
        new NotificationParams(
            Bundles.of(MessageNotificationKeys.LIGHT_SETTINGS, "[\"#e53935\", \"2\", \"1\"]"));

    assertThat(params.getLightSettings())
        .asList()
        .containsExactly(Color.parseColor("#e53935"), 2, 1);
  }

  @Test
  public void getLightSettings_notSet() {
    NotificationParams params = new NotificationParams(Bundle.EMPTY);

    assertThat(params.getLightSettings()).isNull();
  }

  @Test
  public void staticIsNotification_newEnableKeySet() {
    assertThat(
            NotificationParams.isNotification(
                Bundles.of(MessageNotificationKeys.ENABLE_NOTIFICATION, "1")))
        .isTrue();
  }

  @Test
  public void staticIsNotification_oldEnableKeySet() {
    assertThat(
            NotificationParams.isNotification(
                Bundles.of(keyWithOldPrefix(MessageNotificationKeys.ENABLE_NOTIFICATION), "1")))
        .isTrue();
  }

  @Test
  public void staticIsNotification_newIconKeySet() {
    assertThat(
            NotificationParams.isNotification(
                Bundles.of(MessageNotificationKeys.ICON, "some_icon.png")))
        .isFalse();
  }

  @Test
  public void staticIsNotification_oldIconKeySet() {
    assertThat(
            NotificationParams.isNotification(
                Bundles.of(keyWithOldPrefix(MessageNotificationKeys.ICON), "some_icon.png")))
        .isFalse();
  }

  @Test
  public void isNotification_newEnableKeySet() {
    assertThat(
            new NotificationParams(Bundles.of(MessageNotificationKeys.ENABLE_NOTIFICATION, "1"))
                .isNotification())
        .isTrue();
  }

  @Test
  public void isNotification_oldEnableKeySet() {
    assertThat(
            new NotificationParams(
                    Bundles.of(keyWithOldPrefix(MessageNotificationKeys.ENABLE_NOTIFICATION), "1"))
                .isNotification())
        .isTrue();
  }

  @Test
  public void isNotification_newIconKeySet() {
    assertThat(
            new NotificationParams(Bundles.of(MessageNotificationKeys.ICON, "some_icon.png"))
                .isNotification())
        .isFalse();
  }

  @Test
  public void isNotification_oldIconKeySet() {
    assertThat(
            new NotificationParams(
                    Bundles.of(keyWithOldPrefix(MessageNotificationKeys.ICON), "some_icon.png"))
                .isNotification())
        .isFalse();
  }

  @Test
  public void isNotification_newEnableKeySetToZero() {
    assertThat(
            new NotificationParams(Bundles.of(MessageNotificationKeys.ENABLE_NOTIFICATION, "0"))
                .isNotification())
        .isFalse();
  }

  @Test
  public void isNotification_oldEnableKeySetToZero() {
    assertThat(
            new NotificationParams(
                    Bundles.of(keyWithOldPrefix(MessageNotificationKeys.ENABLE_NOTIFICATION), "0"))
                .isNotification())
        .isFalse();
  }

  @Test
  public void isNotification_emptyParamsIsNotNotification() {
    assertThat(new NotificationParams(Bundle.EMPTY).isNotification()).isFalse();
  }

  @Test
  public void hasImage_noImage() {
    assertThat(new NotificationParams(Bundle.EMPTY).hasImage()).isFalse();
  }

  @Test
  public void hasImage_emptyString() {
    assertThat(new NotificationParams(Bundles.of(MessageNotificationKeys.IMAGE_URL, "")).hasImage())
        .isFalse();
  }

  @Test
  public void hasImage_nonEmptyString() {
    assertThat(
            new NotificationParams(
                    Bundles.of(MessageNotificationKeys.IMAGE_URL, "https://example.com/image.png"))
                .hasImage())
        .isTrue();
  }

  @Test
  public void getNotificationChannelId_noChannel() {
    assertThat(new NotificationParams(Bundle.EMPTY).getNotificationChannelId()).isNull();
  }

  @Test
  public void getNotificationChannelId_emptyString() {
    assertThat(
            new NotificationParams(Bundles.of(MessageNotificationKeys.CHANNEL, ""))
                .getNotificationChannelId())
        .isEmpty();
  }

  @Test
  public void getNotificationChannelId_validChannel() {
    assertThat(
            new NotificationParams(Bundles.of(MessageNotificationKeys.CHANNEL, "valid_channel_id"))
                .getNotificationChannelId())
        .isEqualTo("valid_channel_id");
  }

  private static List<Object> toList(JSONArray array) throws JSONException {
    if (array == null) {
      return null;
    }
    List<Object> l = new ArrayList<>(array.length());
    for (int i = 0; i < array.length(); i++) {
      l.add(array.get(i));
    }
    return l;
  }

  private static String keyWithOldPrefix(String key) {
    if (!key.startsWith(MessageNotificationKeys.NOTIFICATION_PREFIX)) {
      return key;
    }

    return key.replace(
        MessageNotificationKeys.NOTIFICATION_PREFIX,
        MessageNotificationKeys.NOTIFICATION_PREFIX_OLD);
  }
}
