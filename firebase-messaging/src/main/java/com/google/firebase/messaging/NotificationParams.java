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

import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.Constants.MessageNotificationKeys;
import com.google.firebase.messaging.Constants.MessagePayloadKeys;
import java.util.Arrays;
import java.util.MissingFormatArgumentException;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * A wrapper over a Bundle of notification data that provides typed accessors for notification
 * fields.
 *
 * @hide
 */
public class NotificationParams {

  private static final int COLOR_TRANSPARENT_IN_HEX = 0xFF000000;
  private static final int EMPTY_JSON_ARRAY_LENGTH = 1;

  // see:
  // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.html#PRIORITY
  private static final int VISIBILITY_MIN = NotificationCompat.VISIBILITY_SECRET; // -1
  private static final int VISIBILITY_MAX = NotificationCompat.VISIBILITY_PUBLIC; // 1

  private static final String TAG = "NotificationParams";

  @NonNull private final Bundle data;

  public NotificationParams(@NonNull Bundle data) {
    if (data == null) {
      throw new NullPointerException("data");
    }

    this.data = new Bundle(data); // make a copy of the bundle
  }

  @Nullable
  Integer getNotificationCount() {
    Integer notificationCount = getInteger(MessageNotificationKeys.NOTIFICATION_COUNT);
    if (notificationCount == null) {
      return null;
    }

    if (notificationCount < 0) {
      Log.w(
          Constants.TAG,
          "notificationCount is invalid: "
              + notificationCount
              + ". Skipping setting notificationCount.");
      return null;
    }

    return notificationCount;
  }

  @Nullable
  Integer getNotificationPriority() {
    Integer notificationPriority = getInteger(MessageNotificationKeys.NOTIFICATION_PRIORITY);
    if (notificationPriority == null) {
      return null;
    }

    if (notificationPriority < NotificationCompat.PRIORITY_MIN
        || notificationPriority > NotificationCompat.PRIORITY_MAX) {
      Log.w(
          Constants.TAG,
          "notificationPriority is invalid "
              + notificationPriority
              + ". Skipping setting notificationPriority.");
      return null;
    }

    return notificationPriority;
  }

  Integer getVisibility() {
    Integer visibility = getInteger(MessageNotificationKeys.VISIBILITY);
    if (visibility == null) {
      return null;
    }

    if (visibility < VISIBILITY_MIN || visibility > VISIBILITY_MAX) {
      Log.w(TAG, "visibility is invalid: " + visibility + ". Skipping setting visibility.");
      return null;
    }

    return visibility;
  }

  public String getString(String key) {
    return data.getString(normalizePrefix(key));
  }

  private String normalizePrefix(String key) {
    if (!data.containsKey(key) && key.startsWith(MessageNotificationKeys.NOTIFICATION_PREFIX)) {
      String keyWithOldPrefix = keyWithOldPrefix(key);
      if (data.containsKey(keyWithOldPrefix)) {
        return keyWithOldPrefix;
      }
    }

    return key;
  }

  public boolean getBoolean(String key) {
    String value = getString(key);

    return "1".equals(value) || Boolean.parseBoolean(value);
  }

  public Integer getInteger(String key) {
    String value = getString(key);

    if (!TextUtils.isEmpty(value)) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        Log.w(
            TAG, "Couldn't parse value of " + userFriendlyKey(key) + "(" + value + ") into an int");
      }
    }

    return null;
  }

  public Long getLong(String key) {
    String value = getString(key);

    if (!TextUtils.isEmpty(value)) {
      try {
        return Long.parseLong(value);
      } catch (NumberFormatException e) {
        Log.w(
            TAG, "Couldn't parse value of " + userFriendlyKey(key) + "(" + value + ") into a long");
      }
    }

    return null;
  }

  @Nullable
  public String getLocalizationResourceForKey(String key) {
    return getString(key + MessageNotificationKeys.TEXT_RESOURCE_SUFFIX);
  }

  @Nullable
  public Object[] getLocalizationArgsForKey(String key) {
    JSONArray jsonArray = getJSONArray(key + MessageNotificationKeys.TEXT_ARGS_SUFFIX);
    if (jsonArray == null) {
      return null;
    }

    String[] args = new String[jsonArray.length()];
    for (int i = 0; i < args.length; i++) {
      args[i] = jsonArray.optString(i);
    }
    return args;
  }

  @Nullable
  public JSONArray getJSONArray(String key) {
    String json = getString(key);
    if (!TextUtils.isEmpty(json)) {
      try {
        return new JSONArray(json);
      } catch (JSONException e) {
        Log.w(
            TAG,
            "Malformed JSON for key "
                + userFriendlyKey(key)
                + ": "
                + json
                + ", falling back to default");
      }
    }

    return null;
  }

  private static String userFriendlyKey(String key) {
    if (key.startsWith(MessageNotificationKeys.NOTIFICATION_PREFIX)) {
      return key.substring(MessageNotificationKeys.NOTIFICATION_PREFIX.length());
    }

    return key;
  }

  @Nullable
  public Uri getLink() {
    // Use the android override for link if present, otherwise use the main link
    String link = getString(MessageNotificationKeys.LINK_ANDROID);
    if (TextUtils.isEmpty(link)) {
      link = getString(MessageNotificationKeys.LINK);
    }

    if (!TextUtils.isEmpty(link)) {
      return Uri.parse(link);
    }
    return null;
  }

  /**
   * Get the sound resource name.
   *
   * <p>This first checks "sound2" (see comment on KEY_SOUND_2) then falls back to checking "sound"
   * so that we have an eventual path back to not sending "sound2".
   */
  @Nullable
  public String getSoundResourceName() {
    String sound = getString(MessageNotificationKeys.SOUND_2);
    if (TextUtils.isEmpty(sound)) {
      sound = getString(MessageNotificationKeys.SOUND);
    }
    return sound;
  }

  @Nullable
  public long[] getVibrateTimings() {
    // Arguments are set as a JSON list, parse into an array
    JSONArray jsonArray = getJSONArray(MessageNotificationKeys.VIBRATE_TIMINGS);
    if (jsonArray == null) {
      // error already logged
      return null;
    }

    try {
      if (jsonArray.length() <= EMPTY_JSON_ARRAY_LENGTH) {
        throw new JSONException("vibrateTimings have invalid length");
      }

      long[] result = new long[jsonArray.length()];
      for (int i = 0; i < result.length; i++) {
        result[i] = jsonArray.optLong(i);
      }
      return result;
    } catch (JSONException | NumberFormatException e) {
      Log.w(
          TAG,
          "User defined vibrateTimings is invalid: "
              + jsonArray
              + ". Skipping setting vibrateTimings.");
    }
    return null;
  }

  /**
   * Returns a integer array contains COLOR,LIGHT_ON_DURATION, LIGHT_OFF_DURATION respectively if
   * they exist and are valid. Else, returns null.
   */
  @Nullable
  int[] getLightSettings() {
    JSONArray lightSettings = getJSONArray(MessageNotificationKeys.LIGHT_SETTINGS);
    if (lightSettings == null) {
      return null;
    }

    int[] result = new int[3];
    try {
      // lightSettingsRaw has the format of "COLOR, LIGHT_ON_DURATION, LIGHT_OFF_DURATION".
      // ex: "[\"#FFFFFFFF\",\"1000\",\"1000\"]"
      if (lightSettings.length() != 3) {
        throw new JSONException("lightSettings don't have all three fields");
      }

      // No need to check for presence of COLOR, LIGHT_ON_DURATION and LIGHT_OFF_DURATION. These
      // three fields are guaranteed to exist since backend proto always gives default values.
      result[0] = getLightColor(lightSettings.optString(0)); // color
      result[1] = lightSettings.optInt(1); // lightOnDuration
      result[2] = lightSettings.optInt(2); // lightOnDuration

      return result;

    } catch (JSONException e) {
      Log.w(TAG, "LightSettings is invalid: " + lightSettings + ". Skipping setting LightSettings");
    } catch (IllegalArgumentException e) {
      Log.w(
          TAG,
          "LightSettings is invalid: "
              + lightSettings
              + ". "
              + e.getMessage()
              + ". Skipping setting LightSettings");
    }
    return null;
  }

  /** Returns a Bundle with only the user-facing keys preserved. */
  public Bundle paramsWithReservedKeysRemoved() {
    Bundle cleanedData = new Bundle(data);

    for (String key : data.keySet()) {
      if (isReservedKey(key)) {
        cleanedData.remove(key);
      }
    }

    return cleanedData;
  }

  /** Returns a Bundle with only the analytics keys preserved. */
  public Bundle paramsForAnalyticsIntent() {
    Bundle analyticsBundle = new Bundle(data);

    for (String key : data.keySet()) {
      if (!isAnalyticsKey(key)) {
        analyticsBundle.remove(key);
      }
    }

    return analyticsBundle;
  }

  @Nullable
  public String getLocalizedString(Resources resources, String packageName, String key) {
    // Raw string not set, look for a resource name
    String resourceKey = getLocalizationResourceForKey(key);
    if (TextUtils.isEmpty(resourceKey)) {
      return null;
    }

    int id = resources.getIdentifier(resourceKey, "string", packageName);
    if (id == 0) {
      Log.w(
          TAG,
          userFriendlyKey(key + MessageNotificationKeys.TEXT_RESOURCE_SUFFIX)
              + " resource not found: "
              + key
              + " Default value will be used.");
      return null;
    }

    Object[] args = getLocalizationArgsForKey(key);
    if (args == null) {
      return resources.getString(id);
    } else {
      try {
        return resources.getString(id, args);
      } catch (MissingFormatArgumentException e) {
        // Happens in the format string has more arguments than in the JSON array
        Log.w(
            TAG,
            "Missing format argument for "
                + userFriendlyKey(key)
                + ": "
                + Arrays.toString(args)
                + " Default value will be used.",
            e);
      }
    }
    // In case of error, or if the string is missing, returns null.
    return null;
  }

  public String getPossiblyLocalizedString(Resources resources, String packageName, String key) {
    String unlocalized = getString(key);
    if (!TextUtils.isEmpty(unlocalized)) {
      return unlocalized;
    }

    return getLocalizedString(resources, packageName, key);
  }

  public boolean hasImage() {
    return !TextUtils.isEmpty(getString(MessageNotificationKeys.IMAGE_URL));
  }

  public String getNotificationChannelId() {
    return getString(MessageNotificationKeys.CHANNEL);
  }

  private static boolean isAnalyticsKey(String key) {
    return key.startsWith(Constants.AnalyticsKeys.PREFIX) || key.equals(MessagePayloadKeys.FROM);
  }

  private static boolean isReservedKey(String key) {
    return key.startsWith(MessagePayloadKeys.RESERVED_CLIENT_LIB_PREFIX)
        || key.startsWith(MessageNotificationKeys.NOTIFICATION_PREFIX)
        || key.startsWith(MessageNotificationKeys.NOTIFICATION_PREFIX_OLD);
  }

  /** @throws IllegalArgumentException if {@code color} maps to color "transparent". */
  private static int getLightColor(String color) {
    int result = Color.parseColor(color);

    if (result == COLOR_TRANSPARENT_IN_HEX) {
      throw new IllegalArgumentException("Transparent color is invalid");
    }

    return result;
  }

  public boolean isNotification() {
    return getBoolean(MessageNotificationKeys.ENABLE_NOTIFICATION);
  }

  public static boolean isNotification(Bundle data) {
    return "1".equals(data.getString(MessageNotificationKeys.ENABLE_NOTIFICATION))
        || "1"
            .equals(data.getString(keyWithOldPrefix(MessageNotificationKeys.ENABLE_NOTIFICATION)));
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
