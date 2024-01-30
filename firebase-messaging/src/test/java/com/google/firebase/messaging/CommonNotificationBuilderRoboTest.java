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
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.cloudmessaging.CloudMessagingReceiver.IntentActionKeys;
import com.google.android.gms.cloudmessaging.CloudMessagingReceiver.IntentKeys;
import com.google.firebase.messaging.CommonNotificationBuilder.DisplayNotificationInfo;
import com.google.firebase.messaging.Constants.AnalyticsKeys;
import com.google.firebase.messaging.Constants.MessageNotificationKeys;
import com.google.firebase.messaging.testing.Bundles;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPendingIntent;

@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(RobolectricTestRunner.class)
public class CommonNotificationBuilderRoboTest {
  public static final String FCM_FALLBACK_NOTIFICATION_CHANNEL =
      "fcm_fallback_notification_channel";
  private static final String FCM_FALLBACK_NOTIFICATION_CHANNEL_NO_RESOURCE = "Misc";
  private static final String KEY_PREFIX = "gcm.n.";
  private static final String KEY_TICKER = KEY_PREFIX + "ticker";
  private static final String KEY_VIBRATE_TIMINGS = KEY_PREFIX + "vibrate_timings";
  private static final String KEY_LIGHT_SETTINGS = KEY_PREFIX + "light_settings";
  private static final String KEY_EVENT_TIME = KEY_PREFIX + "event_time";
  private static final String KEY_DEFAULT_SOUND = KEY_PREFIX + "default_sound";
  private static final String KEY_DEFAULT_LIGHT_SETTINGS = KEY_PREFIX + "default_light_settings";
  private static final String KEY_DEFAULT_VIBRATE_TIMINGS = KEY_PREFIX + "default_vibrate_timings";
  private static final String KEY_NOTIFICATION_PRIORITY = KEY_PREFIX + "notification_priority";
  private static final String KEY_NOTIFICATION_COUNT = KEY_PREFIX + "notification_count";
  private static final String KEY_VISIBILITY = KEY_PREFIX + "visibility";
  private static final String NOTIFICATION_PRIORITY_DEFAULT = "0";
  private static final String NOTIFICATION_PRIORITY_NEGATIVE = "-999";
  private static final int DEFAULTS_ALL_OFF = 0;
  private static final String ACTION_RECEIVER = "com.google.android.c2dm.intent.RECEIVE";

  private Context appContext;
  private Context callingContext;

  @Before
  public void setUp() throws Exception {
    appContext = ApplicationProvider.getApplicationContext();

    PackageInfo packageInfo = new PackageInfo();
    packageInfo.packageName = "test.calling.package";
    shadowOf(appContext.getPackageManager()).addPackage(packageInfo);
    callingContext = appContext.createPackageContext("test.calling.package", 0);
  }

  @Test
  public void testShouldUploadMetrics_true() {
    assertThat(
            CommonNotificationBuilder.shouldUploadMetrics(
                new NotificationParams(Bundles.of(AnalyticsTestHelper.ANALYTICS_ENABLED, "1"))))
        .isTrue();
  }

  @Test
  public void testShouldUploadMetrics_empty() {
    assertThat(CommonNotificationBuilder.shouldUploadMetrics(new NotificationParams(new Bundle())))
        .isFalse();
  }

  @Test
  public void testShouldUploadMetrics_singletonEmpty() {
    assertThat(CommonNotificationBuilder.shouldUploadMetrics(new NotificationParams(Bundle.EMPTY)))
        .isFalse();
  }

  @Test
  public void createNotificationInfo_withTicker() {
    String ticker = "I am a ticker text";
    Bundle data = Bundles.of(KEY_TICKER, ticker);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    assertThat(notificationInfo.notificationBuilder.getNotification().tickerText.toString())
        .isEqualTo(ticker);
  }

  @Test
  public void createNotificationInfo_withValidVibrateTimings() {
    String vibrateTimingsInput = "[\"999\",\"1999\",\"2999\"]";
    Bundle data = Bundles.of(KEY_VIBRATE_TIMINGS, vibrateTimingsInput);

    // Act
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    // Assert
    List<Long> vibrateTimingsArray =
        getVibrateTimingsArray(notificationInfo.notificationBuilder.getNotification().vibrate);
    assertThat(vibrateTimingsArray).containsExactly(999L, 1999L, 2999L).inOrder();
  }

  private static List<Long> getVibrateTimingsArray(long[] input) {
    Long[] vibrateTimings = new Long[input.length];

    int i = 0;
    for (long v : input) {
      vibrateTimings[i++] = v;
    }
    return Arrays.asList(vibrateTimings);
  }

  @Test
  public void createNotificationInfo_invalidVibrateTimings() {
    String vibrateTimingsInput = "invalid_vibrate_timings";
    Bundle data = Bundles.of(KEY_VIBRATE_TIMINGS, vibrateTimingsInput);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    assertThat(notificationInfo.notificationBuilder.getNotification().vibrate).isNull();
  }

  @Test
  public void createNotificationInfo_withoutVibrateTimings() {
    String vibrateTimingsInput = null;
    Bundle data = Bundles.of(KEY_VIBRATE_TIMINGS, vibrateTimingsInput);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    assertThat(notificationInfo.notificationBuilder.getNotification().vibrate).isNull();
  }

  @Test
  public void createNotificationInfo_withValidLightSettings() {
    // set up
    String lightSettingsInput = "[\"#DC143C\",\"999\",\"2999\"]";
    int colorExpected = Color.parseColor("#DC143C");
    int lightOnDurationExpected = 999;
    int lightOffDurationExpected = 2999;
    Bundle data = Bundles.of(KEY_LIGHT_SETTINGS, lightSettingsInput);

    // act
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    // assert
    android.app.Notification notification = notificationInfo.notificationBuilder.getNotification();
    assertThat(notification.ledARGB).isEqualTo(colorExpected);
    assertThat(notification.ledOnMS).isEqualTo(lightOnDurationExpected);
    assertThat(notification.ledOffMS).isEqualTo(lightOffDurationExpected);
  }

  @Test
  public void createNotificationInfo_withValidLightSettings_DurationZero() {
    // lightOnTime and lightOffTime being zero is legal
    String lightSettingsInput = "[\"#DC143C\",\"0\",\"0\"]";
    int colorExpected = Color.parseColor("#DC143C");
    int lightOnDurationExpected = 0;
    int lightOffDurationExpected = 0;
    Bundle data = Bundles.of(KEY_LIGHT_SETTINGS, lightSettingsInput);

    // act
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    // verify
    android.app.Notification notification = notificationInfo.notificationBuilder.getNotification();
    assertThat(notification.ledARGB).isEqualTo(colorExpected);
    assertThat(notification.ledOnMS).isEqualTo(lightOnDurationExpected);
    assertThat(notification.ledOffMS).isEqualTo(lightOffDurationExpected);
  }

  @Test
  public void createNotificationInfo_withInvalidLightSettings_ColorMissing() {
    // If LightSettings is set but the color field is unset, proto will default #FF000000
    // (transparent)to the color value.
    String lightSettingsInput = "[\"#FF000000\",\"1000\",\"1000\"]";

    Bundle data = Bundles.of(KEY_LIGHT_SETTINGS, lightSettingsInput);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    // verify. The followings check is a check on NotificationCompat#setLights is not called without
    // injecting NotificationCompat.Builder into the
    // CommonNotificationBuilder#createNotificationInfo method
    android.app.Notification notification = notificationInfo.notificationBuilder.getNotification();
    assertThat(notification.ledARGB).isEqualTo(0);
    assertThat(notification.ledOnMS).isEqualTo(0);
    assertThat(notification.ledOffMS).isEqualTo(0);
  }

  @Test
  public void createNotificationInfo_withDefaults_allTrue() {
    String defaultSound = "true";
    String defaultVibrateTimings = "true";
    String defaultLightSettings = "true";

    // meaning all flags on
    int lightSettingsFlagExpected =
        Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE;

    Bundle data = new Bundle();
    data.putString(KEY_DEFAULT_SOUND, defaultSound);
    data.putString(KEY_DEFAULT_VIBRATE_TIMINGS, defaultVibrateTimings);
    data.putString(KEY_DEFAULT_LIGHT_SETTINGS, defaultLightSettings);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    assertThat(notificationInfo.notificationBuilder.getNotification().defaults)
        .isEqualTo(lightSettingsFlagExpected);
  }

  @Test
  public void createNotificationInfo_withDefaults_allFalse() {
    String defaultSound = "false";
    String defaultVibrateTimings = "false";
    String defaultLightSettings = "false";

    int lightSettingsFlagExpected = 0b000; // binary = 000, meaning all flags off

    Bundle data = new Bundle();
    data.putString(KEY_DEFAULT_SOUND, defaultSound);
    data.putString(KEY_DEFAULT_VIBRATE_TIMINGS, defaultVibrateTimings);
    data.putString(KEY_DEFAULT_LIGHT_SETTINGS, defaultLightSettings);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    assertThat(notificationInfo.notificationBuilder.getNotification().defaults)
        .isEqualTo(lightSettingsFlagExpected);
  }

  @Test
  public void createNotificationInfo_withDefaults_onlyOneTrue() {
    // There will be three cases to test in total but we only test one here for simplicity
    String defaultSound = "false";
    String defaultVibrateTimings = "true";
    String defaultLightSettings = "false";

    // only vibrateTimings flag on
    int lightSettingsFlagExpected = Notification.DEFAULT_VIBRATE;

    Bundle data = new Bundle();
    data.putString(KEY_DEFAULT_SOUND, defaultSound);
    data.putString(KEY_DEFAULT_VIBRATE_TIMINGS, defaultVibrateTimings);
    data.putString(KEY_DEFAULT_LIGHT_SETTINGS, defaultLightSettings);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    assertThat(notificationInfo.notificationBuilder.getNotification().defaults)
        .isEqualTo(lightSettingsFlagExpected);
  }

  @Test
  public void createNotificationInfo_withInvalidDefaults() {
    // Shouldn't happen because the are parsed from type bool proto but still test to ensure
    String defaultSound = "invalid";
    String defaultVibrateTimings = "123";
    String defaultLightSettings = "false";

    // only vibrateTimings flag on
    int lightSettingsFlagExpected = DEFAULTS_ALL_OFF;

    Bundle data = new Bundle();
    data.putString(KEY_DEFAULT_SOUND, defaultSound);
    data.putString(KEY_DEFAULT_VIBRATE_TIMINGS, defaultVibrateTimings);
    data.putString(KEY_DEFAULT_LIGHT_SETTINGS, defaultLightSettings);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    assertThat(notificationInfo.notificationBuilder.getNotification().defaults)
        .isEqualTo(lightSettingsFlagExpected);
  }

  @Test
  public void createNotificationInfo_withValidEventTime() {
    String eventTime = "765807132"; // epoch timestamp
    int eventTimeExpected = 765807132;

    Bundle data = Bundles.of(KEY_EVENT_TIME, eventTime);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    assertThat(notificationInfo.notificationBuilder.getNotification().when)
        .isEqualTo(eventTimeExpected);

    // This is an indirect way to examine if showWhen is set by the SDK. The reason is that there
    // seems to be no way to access showWhen from the notification object nor the notification
    // builder object. Specifically, there is no getter for the private variable of mShowWhen in the
    // notificationInfo.notificationBuilder object; In addition, showWhen is not a variable of
    // notificationInfo.notificationBuilder.getNotification().
    NotificationCompat.Builder notificationBuilderShowWhenFalse =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data))
            .notificationBuilder
            .setShowWhen(false);
    assertThat(notificationInfo.notificationBuilder).isNotEqualTo(notificationBuilderShowWhenFalse);
  }

  @Test
  public void createNotificationInfo_withInvalidEventTime() {
    long startTime = System.currentTimeMillis();
    String eventTime = "invalid_event_time";
    Bundle data = Bundles.of(KEY_EVENT_TIME, eventTime);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    assertThat(notificationInfo.notificationBuilder.getNotification().when)
        .isGreaterThan(startTime);
    assertThat(notificationInfo.notificationBuilder.getNotification().when)
        .isLessThan(System.currentTimeMillis());
  }

  @Test
  public void createNotificationInfo_withValidNotificationPriority() {
    int notificationPriorityExpected = Integer.parseInt(NOTIFICATION_PRIORITY_DEFAULT);
    Bundle data = Bundles.of(KEY_NOTIFICATION_PRIORITY, NOTIFICATION_PRIORITY_DEFAULT);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    assertThat(notificationInfo.notificationBuilder.getNotification().priority)
        .isEqualTo(notificationPriorityExpected);
  }

  @Test
  public void
      createNotificationInfo_withInvalidNotificationPriority_outOfBoundNotificationPriority() {
    Bundle data = Bundles.of(KEY_NOTIFICATION_PRIORITY, NOTIFICATION_PRIORITY_NEGATIVE);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    assertThat(notificationInfo.notificationBuilder.getNotification().priority).isEqualTo(0);
  }

  @Test
  public void createNotificationInfo_withInvalidNotificationPriority() {
    String invalidNotificationCount = "abc";
    Bundle data = Bundles.of(KEY_NOTIFICATION_PRIORITY, invalidNotificationCount);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    // never set
    assertThat(notificationInfo.notificationBuilder.getNotification().priority).isEqualTo(0);
  }

  @Test
  public void createNotificationInfo_withValidNotificationCount() {
    // set up
    String validNotificationCount = "996";
    Bundle data = Bundles.of(KEY_NOTIFICATION_COUNT, validNotificationCount);
    int expectedNotificationCount = Integer.parseInt(validNotificationCount);

    // act
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    // assert
    assertThat(notificationInfo.notificationBuilder.getNotification().number)
        .isEqualTo(expectedNotificationCount);
  }

  @Test
  public void createNotificationInfo_withInvalidNotificationCount() {
    String validNotificationCount = "abc";
    Bundle data = Bundles.of(KEY_NOTIFICATION_COUNT, validNotificationCount);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    // verify never set
    assertThat(notificationInfo.notificationBuilder.getNotification().number).isEqualTo(0);
  }

  @Test
  public void createNotificationInfo_withInvalidNotificationCount_negativeNotificationCount() {
    String negativeNotificationCount = "-2";
    Bundle data = Bundles.of(KEY_NOTIFICATION_COUNT, negativeNotificationCount);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    // verify never set
    assertThat(notificationInfo.notificationBuilder.getNotification().number).isEqualTo(0);
  }

  @Test
  public void createNotificationInfo_withValidVisibility() {
    // VISIBILITY_PUBLIC, see:
    // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.html#visibility_public
    String validVisibility = "0";
    Bundle data = Bundles.of(KEY_VISIBILITY, validVisibility);
    int expectedVisibility = 0;

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    // verify
    assertThat(NotificationCompat.getVisibility(notificationInfo.notificationBuilder.build()))
        .isEqualTo(expectedVisibility);
  }

  @Test
  public void createNotificationInfo_withInvalidVisibility() {
    // set up
    String invalidVisibility = "a";
    Bundle data = Bundles.of(KEY_VISIBILITY, invalidVisibility);

    // act
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    // verify never set
    assertThat(NotificationCompat.getVisibility(notificationInfo.notificationBuilder.build()))
        .isEqualTo(0);
  }

  @Test
  public void createNotificationInfo_withInvalidVisibility_outOfBoundVisibility() {
    // set up
    String invalidVisibility = "123";
    Bundle data = Bundles.of(KEY_VISIBILITY, invalidVisibility);

    // act
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(appContext, new NotificationParams(data));

    // verify never set
    assertThat(NotificationCompat.getVisibility(notificationInfo.notificationBuilder.build()))
        .isEqualTo(0);
  }

  @Test
  @Config(minSdk = VERSION_CODES.O, maxSdk = 28) // channel ID is O+
  public void staticCreateNotificationInfo_respectsChannelId() throws Exception {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(Bundle.EMPTY),
            "channelId",
            Bundle.EMPTY);

    assertThat(notificationInfo.notificationBuilder.build().getChannelId()).isEqualTo("channelId");
  }

  @Test
  @Config(minSdk = VERSION_CODES.O, maxSdk = 28) // channel ID is O+
  public void staticCreateNotificationInfo_resourceFileHasNoDefaultChannel() throws Exception {
    setTargetSdkVersion(appContext, VERSION_CODES.O);

    assertThat(
            CommonNotificationBuilder.getOrCreateChannel(
                appContext, "non_exsisting_channel", new Bundle()))
        .isEqualTo(FCM_FALLBACK_NOTIFICATION_CHANNEL);
  }

  static void setTargetSdkVersion(Context context, int version) throws Exception {
    PackageInfo packageInfo =
        context
            .getPackageManager()
            .getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
    ApplicationInfo appInfo = packageInfo.applicationInfo;

    appInfo.targetSdkVersion = version;

    shadowOf(context.getPackageManager()).installPackage(packageInfo);
  }

  @Test
  public void staticCreateNotificationInfo_handlesNoArgLocalizedTitle() {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(
                Bundles.of(
                    MessageNotificationKeys.TITLE + MessageNotificationKeys.TEXT_RESOURCE_SUFFIX,
                    "fcm_no_args")),
            "channelId",
            Bundle.EMPTY);

    // http://google3/javatests/com/google/android/gmscore/integ/tests_res/res/values/strings.xml?l=25-28&rcl=127925113
    // "String with no arguments"
    assertThat(shadowOf(notificationInfo.notificationBuilder.build()).getContentTitle().toString())
        .isEqualTo("String with no arguments");
  }

  @Test
  public void staticCreateNotificationInfo_handlesLocalizedTitleWithArgs() {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(
                Bundles.of(
                    MessageNotificationKeys.TITLE + MessageNotificationKeys.TEXT_RESOURCE_SUFFIX,
                    "fcm_2_args",
                    MessageNotificationKeys.TITLE + MessageNotificationKeys.TEXT_ARGS_SUFFIX,
                    "[\"one\", \"two\"]")),
            "channelId",
            Bundle.EMPTY);

    // http://google3/javatests/com/google/android/gmscore/integ/tests_res/res/values/strings.xml?l=25-28&rcl=127925113
    // "First: %1$s and second %2$s"
    assertThat(shadowOf(notificationInfo.notificationBuilder.build()).getContentTitle().toString())
        .isEqualTo("First: one and second two");
  }

  @Test
  public void staticCreateNotificationInfo_handlesNoArgLocalizedBody() {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(
                Bundles.of(
                    MessageNotificationKeys.BODY + MessageNotificationKeys.TEXT_RESOURCE_SUFFIX,
                    "fcm_no_args")),
            "channelId",
            Bundle.EMPTY);

    // http://google3/javatests/com/google/android/gmscore/integ/tests_res/res/values/strings.xml?l=25-28&rcl=127925113
    // "String with no arguments"
    assertThat(shadowOf(notificationInfo.notificationBuilder.build()).getContentText().toString())
        .isEqualTo("String with no arguments");
  }

  @Test
  public void staticCreateNotificationInfo_handlesLocalizedBodyWithArgs() {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(
                Bundles.of(
                    MessageNotificationKeys.BODY + MessageNotificationKeys.TEXT_RESOURCE_SUFFIX,
                    "fcm_2_args",
                    MessageNotificationKeys.BODY + MessageNotificationKeys.TEXT_ARGS_SUFFIX,
                    "[\"one\", \"two\"]")),
            "channelId",
            Bundle.EMPTY);

    // http://google3/javatests/com/google/android/gmscore/integ/tests_res/res/values/strings.xml?l=25-28&rcl=127925113
    // "First: %1$s and second %2$s"
    assertThat(shadowOf(notificationInfo.notificationBuilder.build()).getContentText().toString())
        .isEqualTo("First: one and second two");
  }

  @Test
  public void staticCreateNotificationInfo_smallIconSpecified() {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(Bundles.of(MessageNotificationKeys.ICON, "gcm_icon")),
            "channelId",
            Bundle.EMPTY);

    assertThat(notificationInfo.notificationBuilder.build().icon)
        .isEqualTo(com.google.firebase.messaging.test.R.drawable.gcm_icon);
  }

  @Test
  public void staticCreateNotificationInfo_smallIconSpecifiedInMetadata() {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(Bundle.EMPTY),
            "channelId",
            Bundles.of(
                CommonNotificationBuilder.METADATA_DEFAULT_ICON,
                com.google.firebase.messaging.test.R.drawable.gcm_icon));

    assertThat(notificationInfo.notificationBuilder.build().icon)
        .isEqualTo(com.google.firebase.messaging.test.R.drawable.gcm_icon);
  }

  @Test
  public void staticCreateNotificationInfo_noIconSpecifiedShouldUseAppIcon() {
    PackageInfo packageInfo = new PackageInfo();
    packageInfo.packageName = appContext.getPackageName();
    packageInfo.applicationInfo = new ApplicationInfo();
    packageInfo.applicationInfo.packageName = appContext.getPackageName();
    packageInfo.applicationInfo.icon = com.google.firebase.messaging.test.R.drawable.gcm_icon2;

    shadowOf(appContext.getPackageManager()).installPackage(packageInfo);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(Bundle.EMPTY),
            "channelId",
            Bundle.EMPTY);

    assertThat(notificationInfo.notificationBuilder.build().icon)
        .isEqualTo(packageInfo.applicationInfo.icon);
  }

  @Test
  public void staticCreateNotificationInfo_noAppIconShouldUseDefaultSystemIcon() {
    PackageInfo packageInfo = new PackageInfo();
    packageInfo.packageName = appContext.getPackageName();
    packageInfo.applicationInfo = new ApplicationInfo();
    packageInfo.applicationInfo.packageName = appContext.getPackageName();
    packageInfo.applicationInfo.icon = 0; // Bad app icon!

    shadowOf(appContext.getPackageManager()).installPackage(packageInfo);

    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(Bundle.EMPTY),
            "channelId",
            Bundle.EMPTY);

    assertThat(notificationInfo.notificationBuilder.build().icon)
        .isEqualTo(android.R.drawable.sym_def_app_icon);
  }

  @Test
  public void staticCreateNotificationInfo_defaultSoundSpecified_sound1Key() {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(Bundles.of(MessageNotificationKeys.SOUND, "default")),
            "channelId",
            Bundle.EMPTY);

    assertThat(notificationInfo.notificationBuilder.build().sound)
        .isEqualTo(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
  }

  @Test
  public void staticCreateNotificationInfo_defaultSoundSpecified_sound2Key() {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(Bundles.of(MessageNotificationKeys.SOUND_2, "default")),
            "channelId",
            Bundle.EMPTY);

    assertThat(notificationInfo.notificationBuilder.build().sound)
        .isEqualTo(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
  }

  @Test
  public void staticCreateNotificationInfo_noSoundSpecified() {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(Bundle.EMPTY),
            "channelId",
            Bundle.EMPTY);

    assertThat(notificationInfo.notificationBuilder.build().sound).isNull();
  }

  @Test
  public void createNotificationInfo_noContentIntentSpecified() {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(Bundle.EMPTY),
            "channelId",
            Bundle.EMPTY);

    assertThat(notificationInfo.notificationBuilder.build().contentIntent).isNull();
  }

  @Test
  public void createNotificationInfo_clickAction() {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(
                Bundles.of(MessageNotificationKeys.CLICK_ACTION, "click.action")),
            "channelId",
            Bundle.EMPTY);

    ShadowPendingIntent contentPendingIntent =
        shadowOf(notificationInfo.notificationBuilder.build().contentIntent);
    assertThat(contentPendingIntent.isActivityIntent()).isTrue();
    assertThat(contentPendingIntent.getSavedContext()).isEqualTo(callingContext);
    Intent contentIntent = contentPendingIntent.getSavedIntent();
    assertThat(contentIntent.getPackage()).isEqualTo(appContext.getPackageName());
    assertThat(contentIntent.getAction()).isEqualTo("click.action");
  }

  @Test
  public void createNotificationInfo_link() {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(Bundles.of(MessageNotificationKeys.LINK, "link")),
            "channelId",
            Bundle.EMPTY);

    ShadowPendingIntent contentPendingIntent =
        shadowOf(notificationInfo.notificationBuilder.build().contentIntent);
    assertThat(contentPendingIntent.isActivityIntent()).isTrue();
    assertThat(contentPendingIntent.getSavedContext()).isEqualTo(callingContext);
    Intent contentIntent = contentPendingIntent.getSavedIntent();
    assertThat(contentIntent.getPackage()).isEqualTo(appContext.getPackageName());
    assertThat(contentIntent.getDataString()).isEqualTo("link");
  }

  @Test
  public void createNotificationInfo_androidLink() {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(
                Bundles.of(
                    MessageNotificationKeys.LINK,
                    "link",
                    MessageNotificationKeys.LINK_ANDROID,
                    "androidLink")),
            "channelId",
            Bundle.EMPTY);

    ShadowPendingIntent contentPendingIntent =
        shadowOf(notificationInfo.notificationBuilder.build().contentIntent);
    assertThat(contentPendingIntent.isActivityIntent()).isTrue();
    assertThat(contentPendingIntent.getSavedContext()).isEqualTo(callingContext);
    Intent contentIntent = contentPendingIntent.getSavedIntent();
    assertThat(contentIntent.getPackage()).isEqualTo(appContext.getPackageName());
    assertThat(contentIntent.getDataString()).isEqualTo("androidLink");
  }

  @Test
  public void createNotificationInfo_deleteIntentWithoutAnalytics() {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(Bundle.EMPTY),
            "channelId",
            Bundle.EMPTY);

    assertThat(notificationInfo.notificationBuilder.build().deleteIntent).isNull();
  }

  @Test
  public void createNotificationInfo_deleteIntentWithAnalytics() {
    DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(
            callingContext,
            appContext,
            new NotificationParams(Bundles.of(AnalyticsKeys.ENABLED, "1")),
            "channelId",
            Bundle.EMPTY);

    ShadowPendingIntent deletePendingIntent =
        shadowOf(notificationInfo.notificationBuilder.build().deleteIntent);
    assertThat(deletePendingIntent.isBroadcastIntent()).isTrue();
    assertThat(deletePendingIntent.getSavedContext()).isEqualTo(callingContext);
    Intent deleteIntent = deletePendingIntent.getSavedIntent();
    assertThat(deleteIntent.getPackage()).isEqualTo(appContext.getPackageName());
    assertThat(deleteIntent.getAction()).isEqualTo(ACTION_RECEIVER);
    Intent dismissIntent = deleteIntent.getParcelableExtra(IntentKeys.WRAPPED_INTENT);
    assertThat(dismissIntent).isNotNull();
    assertThat(dismissIntent.getAction()).isEqualTo(IntentActionKeys.NOTIFICATION_DISMISS);
  }
}
