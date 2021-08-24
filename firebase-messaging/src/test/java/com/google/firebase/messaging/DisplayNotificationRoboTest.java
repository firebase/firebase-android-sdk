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
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.robolectric.Shadows.shadowOf;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Application;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.messaging.shadows.ShadowPreconditions;
import com.google.firebase.messaging.testing.Bundles;
import com.google.firebase.messaging.testing.TestImageServer;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowPreconditions.class})
public class DisplayNotificationRoboTest {

  // Constants copy pasted here as they are part of the protocol
  static final String KEY_PREFIX = "gcm.n.";
  static final String KEY_PREFIX_OLD = "gcm.notification.";

  static final String KEY_ENABLE = KEY_PREFIX + "e";

  static final String KEY_NO_UI = KEY_PREFIX + "noui";

  static final String KEY_TITLE = KEY_PREFIX + "title";
  static final String KEY_BODY = KEY_PREFIX + "body";
  static final String KEY_ICON = KEY_PREFIX + "icon";
  static final String KEY_TAG = KEY_PREFIX + "tag";
  static final String KEY_CHANNEL_ID = KEY_PREFIX + "android_channel_id";

  static final String KEY_COLOR = KEY_PREFIX + "color";
  static final String KEY_SOUND = KEY_PREFIX + "sound";
  static final String KEY_SOUND_2 = KEY_PREFIX + "sound2";
  static final String KEY_CLICK_ACTION = KEY_PREFIX + "click_action";
  static final String KEY_LINK = KEY_PREFIX + "link";
  static final String KEY_LINK_ANDROID = KEY_PREFIX + "link_android";
  static final String KEY_IMAGE = KEY_PREFIX + "image";
  static final String KEY_TICKER = KEY_PREFIX + "ticker";
  static final String KEY_LOCAL_ONLY = KEY_PREFIX + "local_only";
  static final String KEY_STICKY = KEY_PREFIX + "sticky";
  static final String KEY_DEFAULT_SOUND = KEY_PREFIX + "default_sound";
  static final String KEY_DEFAULT_VIBRATE_TIMINGS = KEY_PREFIX + "default_vibrate_timings";
  static final String KEY_DEFAULT_LIGHT_SETTINGS = KEY_PREFIX + "default_light_settings";
  static final String KEY_NOTIFICATION_PRIORITY = KEY_PREFIX + "notification_priority";
  static final String KEY_VISIBILITY = KEY_PREFIX + "visibility";
  static final String KEY_NOTIFICATION_COUNT = KEY_PREFIX + "notification_count";
  static final String KEY_LIGHT_SETTINGS = KEY_PREFIX + "light_settings";
  static final String KEY_VIBRATE_TIMINGS = KEY_PREFIX + "vibrate_timings";
  static final String KEY_EVENT_TIME = KEY_PREFIX + "event_time";

  static final String TEXT_RESOURCE_SUFFIX = "_loc_key";
  static final String TEXT_ARGS_SUFFIX = "_loc_args";

  private static final String EXTRA_BINARY_DATA = "rawData";

  // Resource name of the gcm_icon drawable
  private static final String DRAWABLE_GCM_ICON = "gcm_icon";

  private static final String FAKE_ACTIVITY_ACTION =
      "com.google.firebase.messaging.START_FAKE_NOTIFICATION_ACTIVITY";

  // COPY FROM DisplayNotification.java
  // Name of the (optional) AndroidManifest metadata setting default notification icon and color.
  private static final String METADATA_DEFAULT_COLOR =
      "com.google.firebase.messaging.default_notification_color";
  private static final String METADATA_DEFAULT_ICON =
      "com.google.firebase.messaging.default_notification_icon";

  @Rule public TestImageServer testImageServer = new TestImageServer();

  private Application context;
  private ActivityManager activityManager;
  private KeyguardManager keyguardManager;
  private NotificationManager notificationManager;
  private Executor executor;

  @Before
  public void setUp() throws IOException {
    context = spy(RuntimeEnvironment.application);
    activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    executor = Executors.newSingleThreadExecutor();
    RunningAppProcessInfo process =
        createProcessInfo(
            Process.myPid(), context.getPackageName(), RunningAppProcessInfo.IMPORTANCE_SERVICE);
    shadowOf(activityManager).setProcesses(Arrays.asList(process));
  }

  /**
   * Test that a notification with no data is displayed.
   *
   * <p>It should use the application icon.
   */
  @Test
  public void testNoPayload() {
    assertTrue(
        new DisplayNotification(context, new NotificationParams(Bundle.EMPTY), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertThat(shadowOf(n).getContentTitle().toString()).isEmpty();
    // ShadowNotification.getSmallIcon() doesn't work so access the real notification
    assertEquals(getAppIcon(), n.icon);
  }

  /** Test that a notification with no title is displayed. */
  @Test
  public void testNoTitle() {
    Bundle data = new Bundle();
    data.putString(KEY_ICON, DRAWABLE_GCM_ICON);
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertThat(shadowOf(n).getContentTitle().toString()).isEmpty();
    // ShadowNotification.getSmallIcon() doesn't work so access the real notification
    assertEquals(R.drawable.gcm_icon, n.icon);
  }

  /** Test that a notification with no icon shows the app's icon. */
  @Test
  public void testNoIcon() {
    Bundle data = new Bundle();
    data.putString(KEY_TITLE, "title 123");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertEquals("title 123", shadowOf(n).getContentTitle().toString());
    // ShadowNotification.getSmallIcon() doesn't work so access the real notification
    assertEquals(getAppIcon(), n.icon);
  }

  /** Test that the user can choose the default icon via AndroidManifest metadata. */
  @Test
  public void testIconFromMetadata() {
    int resId = 1;
    Bundle metadata = new Bundle();
    metadata.putInt(METADATA_DEFAULT_ICON, resId);
    PackageInfo packageInfo =
        shadowOf(context.getPackageManager()).getPackageInfoForTesting(context.getPackageName());
    packageInfo.applicationInfo.metaData = metadata;

    Bundle data = new Bundle();
    data.putString(KEY_TITLE, "title 123");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertEquals("title 123", shadowOf(n).getContentTitle().toString());
    assertEquals(resId, n.icon);
  }

  /** Test that a notification with a bad icon resource name shows the app's icon. */
  @Test
  public void testBadIconResource() {
    Bundle data = new Bundle();
    data.putString(KEY_ICON, "gcm_nonexistent_icon");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    // ShadowNotification.getSmallIcon() doesn't work so access the real notification
    assertEquals(getAppIcon(), n.icon);
  }

  /**
   * Test that an adaptive icon is detected and avoided, when passed via parameter (on Android O)
   */
  @Config(sdk = Build.VERSION_CODES.O)
  @Test
  public void testAdaptiveIcon_viaParameter() {
    Bundle data = new Bundle();
    data.putString(KEY_ICON, "adaptive_icon");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    // check that we fallback to the default app icon, and that we don't use R.
    assertNotEquals(R.drawable.adaptive_icon, n.icon);
  }

  /** Test that an adaptive icon is detected and avoided, when passed via metadata (on Android O) */
  @Config(sdk = Build.VERSION_CODES.O)
  @Test
  public void testAdaptiveIcon_viaMetadata() {
    Bundle metadata = new Bundle();
    metadata.putInt(METADATA_DEFAULT_ICON, R.drawable.adaptive_icon);
    PackageInfo packageInfo =
        shadowOf(context.getPackageManager()).getPackageInfoForTesting(context.getPackageName());
    packageInfo.applicationInfo.metaData = metadata;

    Bundle data = new Bundle();
    data.putString(KEY_TITLE, "title 123");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    // check that we fallback to the default app icon, and that we don't use R.
    assertNotEquals(R.drawable.adaptive_icon, n.icon);
  }

  /**
   * Test that an adaptive icon is detected and avoided, when passed via default icon (on Android O)
   */
  @Config(sdk = Build.VERSION_CODES.O)
  @Test
  public void testAdaptiveIcon_viaDefaultIcon() {
    setApplicationIcon(context.getPackageName(), R.drawable.adaptive_icon);

    Bundle data = new Bundle();
    data.putString(KEY_TITLE, "title 123");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    // check that we fallback to the default app icon, and that we don't use R.
    assertNotEquals(R.drawable.adaptive_icon, n.icon);
  }

  /** Test that an adaptive icon is ok with Android < O */
  @Config(sdk = Build.VERSION_CODES.N)
  @Test
  public void testAdaptiveIcon_beforeAndroidO() {
    setApplicationIcon(context.getPackageName(), R.drawable.adaptive_icon);

    Bundle data = new Bundle();
    data.putString(KEY_TITLE, "title 123");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    // check that we use R and that we don't fallback to the default app icon.
    assertEquals(R.drawable.adaptive_icon, n.icon);
  }

  /** Test that a non adaptive icon is ok on Android O. */
  @Config(sdk = Build.VERSION_CODES.O)
  @Test
  public void testNonAdaptiveIcon_AndroidO() {
    setApplicationIcon(context.getPackageName(), R.drawable.gcm_icon);

    Bundle data = new Bundle();
    data.putString(KEY_TITLE, "title 123");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();

    // check that we use R and that we don't fallback to the default app icon.
    assertEquals(R.drawable.gcm_icon, n.icon);
  }

  /** Test that a non adaptive icon with gradient is ok on Android O. */
  @Config(sdk = Build.VERSION_CODES.O)
  @Test
  public void testNonAdaptiveIconWithGradient_AndroidO() {
    setApplicationIcon(context.getPackageName(), R.drawable.icon_with_gradient);

    Bundle data = new Bundle();
    data.putString(KEY_TITLE, "title 123");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    // check that we use R and that we don't fallback to the default app icon.
    assertEquals(R.drawable.icon_with_gradient, n.icon);
  }

  /** Test that a notification with title and icon is displayed. */
  @Test
  public void testTitleIcon() {
    Bundle data = new Bundle();
    data.putString(KEY_TITLE, "a title");
    data.putString(KEY_ICON, DRAWABLE_GCM_ICON);
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertEquals("a title", shadowOf(n).getContentTitle().toString());
    // ShadowNotification.getSmallIcon() doesn't work so access the real notification
    assertEquals(R.drawable.gcm_icon, n.icon);
  }

  /** Test that a notification with title and icon, using the old prefix is displayed. */
  @Test
  public void testTitleIcon_oldPrefix() {
    Bundle data = new Bundle();
    data.putString(KEY_PREFIX_OLD + "title", "Notification title");
    data.putString(KEY_PREFIX_OLD + "icon", "gcm_icon");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertEquals("Notification title", shadowOf(n).getContentTitle().toString());
    // ShadowNotification.getSmallIcon() doesn't work so access the real notification
    assertEquals(R.drawable.gcm_icon, n.icon);
  }

  /** Test that a notification with body text is displayed. */
  @Test
  public void testBody() {
    final String body = "Notification text. 123 ABC.";

    Bundle data = new Bundle();
    data.putString(KEY_BODY, body);
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertEquals(body, shadowOf(n).getContentText().toString());
  }

  /**
   * Test that a notification with click action is displayed and when clicked on would start the
   * test activity.
   */
  @Test
  public void testClickAction() {
    Bundle data = new Bundle();
    data.putString(KEY_CLICK_ACTION, FAKE_ACTIVITY_ACTION);

    Intent intent = verifyNotificationLaunchedActivity(data);
    assertThat(intent.getAction(), is(FAKE_ACTIVITY_ACTION));
    assertThat(
        intent.getFlags(), is(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
  }

  /**
   * Test that a notification with the provided data launches an activity.
   *
   * <p>This also adds some payload to the data and ensures it is populated in the activity launch
   * intent.
   *
   * @return the activity start intent
   */
  private Intent verifyNotificationLaunchedActivity(Bundle data) {
    Bundle keyValues = new Bundle();
    keyValues.putString("timestamp", String.valueOf(SystemClock.uptimeMillis()));
    keyValues.putString("key1", "value1");
    keyValues.putString("key2", "value2");
    data.putAll(keyValues);

    byte[] binaryData = new byte[] {1, 2, 3, 4};
    data.putByteArray(EXTRA_BINARY_DATA, binaryData);

    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());
    Notification n = getSingleNotification();
    PendingIntent pi = n.contentIntent;
    assertTrue(shadowOf(pi).isActivityIntent());

    Intent intent = shadowOf(pi).getSavedIntent();
    assertThat(intent.getPackage(), is(context.getPackageName()));
    for (String key : keyValues.keySet()) {
      assertThat(intent.getStringExtra(key), is(keyValues.get(key)));
    }
    assertThat(intent.getByteArrayExtra(EXTRA_BINARY_DATA)).isEqualTo(binaryData);
    return intent;
  }

  /** Test that a notification with link is displayed and when clicked would open the link. */
  @Test
  public void testLink() {
    Bundle data = new Bundle();
    data.putString(KEY_LINK, "https://www.google.com");

    Intent intent = verifyNotificationLaunchedActivity(data);
    assertThat(intent.getAction(), is(Intent.ACTION_VIEW));
    assertThat(intent.getData().toString(), is("https://www.google.com"));
  }

  /** Test that a notification with android override link overrides the main link. */
  @Test
  public void testLinkAndroid() {
    Bundle data = new Bundle();
    data.putString(KEY_LINK, "https://www.google.com");
    data.putString(KEY_LINK_ANDROID, "https://www.android.com");

    Intent intent = verifyNotificationLaunchedActivity(data);
    assertThat(intent.getAction(), is(Intent.ACTION_VIEW));
    assertThat(intent.getData().toString(), is("https://www.android.com"));
  }

  /**
   * Test that for a notification with both click_action and link that the action takes priority.
   */
  @Test
  public void testClickActionAndLink() {
    Bundle data = new Bundle();
    data.putString(KEY_LINK, "https://www.google.com");
    data.putString(KEY_CLICK_ACTION, "action");

    Intent intent = verifyNotificationLaunchedActivity(data);
    assertThat(intent.getAction(), is("action"));
    assertNull(intent.getData());
  }

  /** Test that a valid notification with color is displayed. */
  @Test
  @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
  public void testColor() {
    final String color = "#123456";
    Bundle data = new Bundle();
    data.putString(KEY_COLOR, color);
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertEquals(Color.parseColor(color), n.color);
  }

  @Test
  @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
  public void testNoColor() {
    Bundle data = new Bundle();
    data.putString(KEY_TITLE, "title 123");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertEquals(0, n.color);
  }

  /** Test that the user can choose the default color via AndroidManifest metadata. */
  @Test
  @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
  public void testColorFromMetadata() {
    Bundle metadata = new Bundle();
    metadata.putInt(METADATA_DEFAULT_COLOR, R.color.google_blue);
    PackageInfo packageInfo =
        shadowOf(context.getPackageManager()).getPackageInfoForTesting(context.getPackageName());
    packageInfo.applicationInfo.metaData = metadata;

    Bundle data = new Bundle();
    data.putString(KEY_TITLE, "title 123");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertEquals(context.getResources().getColor(R.color.google_blue), n.color);
  }

  /** Test that a color is ignored pre-Lollipop where it wasn't supported. */
  @Test
  @Config(sdk = Build.VERSION_CODES.KITKAT)
  public void testColor_kitkat() {
    final String color = "#123456";
    Bundle data = new Bundle();
    data.putString(KEY_COLOR, color);
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    assertNotNull(getSingleNotification());
  }

  /** Test a valid notification with default notification sound is displayed. */
  @Test
  public void testDefaultSound() {
    Bundle data = new Bundle();
    data.putString(KEY_SOUND_2, "default");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    assertEquals(sound, n.sound);
  }

  /** Test a valid notification with no sound */
  @Test
  public void testNoSound() {
    Bundle data = new Bundle();
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertNull(n.sound);
  }

  /** Test a valid notification with empty string for sound */
  @Test
  public void testEmptySound() {
    Bundle data = new Bundle();
    data.putString(KEY_SOUND_2, "");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertNull(n.sound);
  }

  /** Test a valid notification with bad resource name in as sound: default sound is played */
  @Test
  public void testBadResourceNameForSound() {
    Bundle data = new Bundle();
    data.putString(KEY_SOUND_2, "bad-resource-name");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    assertEquals(sound, n.sound);
  }

  /** Test a valid notification with custom sound. */
  @Test
  public void testValidSound() {
    Bundle data = new Bundle();
    data.putString(KEY_SOUND_2, "gcm_bip");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    Uri sound =
        Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE
                + "://"
                + context.getPackageName()
                + "/raw/gcm_bip");
    assertEquals(sound, n.sound);
  }

  /**
   * Test a valid notification with a tag will refresh an existing notification with the same tag.
   */
  @Test
  public void testSameTag() {
    Bundle data = new Bundle();
    data.putString(KEY_TAG, "test_tag");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    // Keep the data the same, but change the icon to ensure the notification is different
    data.putString(KEY_ICON, "gcm_icon2");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    assertEquals(1, shadowOf(notificationManager).size());
    Notification n = shadowOf(notificationManager).getNotification("test_tag", 0);
    assertEquals("Icon reosurce wasn't updated", R.drawable.gcm_icon2, n.icon);
  }

  /**
   * Test a valid notification with a different tag creates a new notification and doesn't refresh
   * an existing notification.
   */
  @Test
  public void testDifferentTags() {
    Bundle data = new Bundle();
    data.putString(KEY_TAG, "test_tag1");
    data.putString(KEY_ICON, "gcm_icon");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    // Change the tag, and check a different notification is shown
    data.putString(KEY_TAG, "test_tag2");
    data.putString(KEY_ICON, "gcm_icon2");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    assertEquals(2, shadowOf(notificationManager).size());
    Notification first = shadowOf(notificationManager).getNotification("test_tag1", 0);
    Notification second = shadowOf(notificationManager).getNotification("test_tag2", 0);

    assertEquals("First icon resource not correct", R.drawable.gcm_icon, first.icon);
    assertEquals("Second icon resource not correct", R.drawable.gcm_icon2, second.icon);
  }

  /**
   * Test a valid notification with a no tag creates a new notification and doesn't refresh an
   * existing notification.
   */
  @Test
  public void testNoTags() {
    Bundle data = new Bundle();
    data.putString(KEY_ICON, "gcm_icon");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    // Still with no tag, just a different icon. Move the clock forward as it uses the uptime
    // in the tag.
    data.putString(KEY_ICON, "gcm_icon2");
    SystemClock.setCurrentTimeMillis(ShadowSystemClock.currentTimeMillis() + 1000);
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    assertEquals(2, shadowOf(notificationManager).size());
    // ShadowNotificationManager uses an (unordered) HashMap for storing notifications, so
    // these notifications can be in any order.
    Notification n1 = shadowOf(notificationManager).getAllNotifications().get(0);
    Notification n2 = shadowOf(notificationManager).getAllNotifications().get(1);

    Set<Integer> expectedIcons =
        new HashSet<>(Arrays.asList(R.drawable.gcm_icon, R.drawable.gcm_icon2));
    assertTrue(expectedIcons.remove(n1.icon));
    assertTrue(expectedIcons.remove(n2.icon));
    assertTrue(expectedIcons.isEmpty());
  }

  /** Test a valid notification using a title resource is displayed. */
  @Test
  public void testTitleResource() {
    Bundle data = new Bundle();
    data.remove(KEY_TITLE);
    data.putString(KEY_TITLE + TEXT_RESOURCE_SUFFIX, "gcm_no_args");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertEquals(stringResource(R.string.gcm_no_args), shadowOf(n).getContentTitle().toString());
  }

  /** Test a valid notification using a title resource with args is displayed. */
  @Test
  public void testTitleResourceWithArgs() {
    Bundle data = new Bundle();
    data.remove(KEY_TITLE);
    data.putString(KEY_TITLE + TEXT_RESOURCE_SUFFIX, "gcm_2_args");
    data.putString(KEY_TITLE + TEXT_ARGS_SUFFIX, jsonArray("arg1", "arg2"));
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertEquals(
        stringResource(R.string.gcm_2_args, "arg1", "arg2"),
        shadowOf(n).getContentTitle().toString());
  }

  /** Test a valid notification using a body resource is displayed. */
  @Test
  public void testBodyResource() {
    Bundle data = new Bundle();
    data.putString(KEY_BODY + TEXT_RESOURCE_SUFFIX, "gcm_no_args");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertEquals(stringResource(R.string.gcm_no_args), shadowOf(n).getContentText().toString());
  }

  /** Test a valid notification using a body resource with args is displayed. */
  @Test
  public void testBodyResourceWithArgs() {
    Bundle data = new Bundle();
    data.putString(KEY_BODY + TEXT_RESOURCE_SUFFIX, "gcm_2_args");
    data.putString(KEY_BODY + TEXT_ARGS_SUFFIX, jsonArray("arg1", "arg2"));
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertEquals(
        stringResource(R.string.gcm_2_args, "arg1", "arg2"),
        shadowOf(n).getContentText().toString());
  }

  /**
   * Test that a notification with a bad title resource name is still displayed.
   *
   * <p>This should have no title set.
   */
  @Test
  public void testBadTitleResource() {
    Bundle data = new Bundle();
    data.remove(KEY_TITLE);
    data.putString(KEY_TITLE + TEXT_RESOURCE_SUFFIX, "gcm_nonexistent");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertThat(shadowOf(n).getContentTitle().toString()).isEmpty();
  }

  /**
   * Test that a notification with the wrong number of title args is still displayed, and doesn't
   * crash the app.
   */
  @Test
  public void testWrongTitleArgs() {
    Bundle data = new Bundle();
    data.remove(KEY_TITLE);
    data.putString(KEY_TITLE + TEXT_RESOURCE_SUFFIX, "gcm_2_args");
    data.putString(KEY_TITLE + TEXT_ARGS_SUFFIX, jsonArray("arg"));
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertThat(shadowOf(n).getContentTitle().toString()).isEmpty();
  }

  /** Test that a notification with a bad body resource name is still displayed. */
  @Test
  public void testBadBodyResource() {
    Bundle data = new Bundle();
    data.putString(KEY_BODY + TEXT_RESOURCE_SUFFIX, "gcm_nonexistent");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    Notification n = getSingleNotification();
    assertThat(shadowOf(n).getContentText().toString()).isEmpty();
  }

  @Test
  public void testImage() {
    Bitmap bitmap =
        TestImageServer.getBitmapFromResource(
            ApplicationProvider.getApplicationContext(), R.drawable.gcm_icon);
    String url = testImageServer.serveBitmap("/gcm_icon", bitmap);

    Bundle data = Bundles.of(KEY_IMAGE, url);
    assertThat(
            new DisplayNotification(context, new NotificationParams(data), executor)
                .handleNotification())
        .isTrue();

    Notification n = getSingleNotification();
    assertThat(n.largeIcon.sameAs(bitmap)).isTrue();
    assertThat(shadowOf(n).getBigPicture().sameAs(bitmap)).isTrue();
  }

  @Test
  public void testImage_downloadTimeout() {
    // Serve a bitmap after a 10s delay, but the SDK should time out after 5s and show the
    // notification without the image.
    Bitmap bitmap =
        TestImageServer.getBitmapFromResource(
            ApplicationProvider.getApplicationContext(), R.drawable.gcm_icon);
    String url = testImageServer.serveBitmapAfterDelay("/timeout", /* delaySeconds= */ 10, bitmap);

    Bundle data = Bundles.of(KEY_IMAGE, url);
    assertThat(
            new DisplayNotification(context, new NotificationParams(data), executor)
                .handleNotification())
        .isTrue();

    Notification n = getSingleNotification();
    assertThat(n.largeIcon).isNull();
    // Cannot easily check the big picture is null, but the large icon check should be sufficient
  }

  @Test
  public void testImage_downloadFail() {
    String url = testImageServer.serveError("/error");

    Bundle data = Bundles.of(KEY_IMAGE, url);
    assertThat(
            new DisplayNotification(context, new NotificationParams(data), executor)
                .handleNotification())
        .isTrue();

    Notification n = getSingleNotification();
    assertThat(n.largeIcon).isNull();
    // Cannot easily check the big picture is null, but the large icon check should be sufficient
  }

  @Test
  public void testNoUi() {
    Bundle data = new Bundle();
    data.putString(KEY_NO_UI, "1");
    assertTrue(
        new DisplayNotification(context, new NotificationParams(data), executor)
            .handleNotification());

    assertEquals(0, shadowOf(notificationManager).size());
  }

  /**
   * Test isAppForeground() returns true when the screen is on and unlocked and the app is in the
   * foreground.
   */
  @Test
  public void testIsAppForeground_foreground() {
    RunningAppProcessInfo process =
        createProcessInfo(
            Process.myPid(), context.getPackageName(), RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
    shadowOf(activityManager).setProcesses(Arrays.asList(process));

    checkAppForeground(true);
  }

  private void checkAppForeground(boolean expectingForeground) {
    boolean consumed =
        new DisplayNotification(context, new NotificationParams(Bundle.EMPTY), executor)
            .handleNotification();
    assertEquals(expectingForeground, !consumed);
  }

  /** Test isAppForeground() returns false if the screen is off or locked. */
  @Test
  public void testIsAppForeground_screenOffOrLocked() {
    shadowOf(keyguardManager).setinRestrictedInputMode(true);

    checkAppForeground(false);
  }

  /** Test isAppForeground() returns false if the list of running app processes is empty. */
  @Test
  public void testIsAppForeground_processesEmpty() {
    shadowOf(activityManager).setProcesses(Collections.<RunningAppProcessInfo>emptyList());

    checkAppForeground(false);
  }

  /** Test isAppForeground() returns false if the app is not in the foreground. */
  @Test
  public void testIsAppForeground_notForeground() {
    RunningAppProcessInfo process =
        createProcessInfo(
            Process.myPid(), context.getPackageName(), RunningAppProcessInfo.IMPORTANCE_SERVICE);
    shadowOf(activityManager).setProcesses(Arrays.asList(process));

    checkAppForeground(false);
  }

  /** Test isAppForeground() works correctly with multiple processes in the list. */
  @Test
  public void testIsAppForeground_multipleProcesses() {
    int pid = Process.myPid();
    RunningAppProcessInfo myProcess =
        createProcessInfo(
            pid, context.getPackageName(), RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
    List<RunningAppProcessInfo> processes =
        Arrays.asList(
            createProcessInfo(pid + 1, "pkg1", RunningAppProcessInfo.IMPORTANCE_FOREGROUND),
            createProcessInfo(pid + 2, "pkg2", RunningAppProcessInfo.IMPORTANCE_BACKGROUND),
            createProcessInfo(pid + 3, "pkg3", RunningAppProcessInfo.IMPORTANCE_SERVICE),
            myProcess,
            createProcessInfo(pid + 4, "pkg4", RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE));
    shadowOf(activityManager).setProcesses(processes);

    checkAppForeground(true);

    // Now change the importance and ensure the result is still correct
    myProcess.importance = RunningAppProcessInfo.IMPORTANCE_BACKGROUND;
    checkAppForeground(false);
  }

  private Notification getSingleNotification() {
    assertEquals(1, shadowOf(notificationManager).size());
    return shadowOf(notificationManager).getAllNotifications().get(0);
  }

  private int getAppIcon() {
    return android.R.drawable.sym_def_app_icon;
    // TODO(morepork) Set a custom app icon and have robolectric pick it up
    // return context.getApplicationInfo().icon;
  }

  static String jsonArray(String... args) {
    return new JSONArray(Arrays.asList(args)).toString();
  }

  private String stringResource(int id, Object... args) {
    String s = context.getResources().getString(id);
    return args.length > 0 ? String.format(s, args) : s;
  }

  private RunningAppProcessInfo createProcessInfo(int pid, String packageName, int importance) {
    RunningAppProcessInfo process =
        new RunningAppProcessInfo(packageName, pid, new String[] {packageName});
    process.importance = importance;
    return process;
  }

  private void setApplicationIcon(String pkgName, int icon) {
    ApplicationInfo appInfo =
        shadowOf(context.getPackageManager())
            .getInternalMutablePackageInfo(pkgName)
            .applicationInfo;

    appInfo.icon = icon;
  }
}
