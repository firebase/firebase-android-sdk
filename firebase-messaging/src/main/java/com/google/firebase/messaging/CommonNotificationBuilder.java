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

import static com.google.firebase.messaging.Constants.TAG;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.messaging.Constants.IntentActionKeys;
import com.google.firebase.messaging.Constants.IntentKeys;
import com.google.firebase.messaging.Constants.MessageNotificationKeys;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Encapsulates the logic of building a display notification from a given Bundle and Context.
 *
 * @hide
 */
public final class CommonNotificationBuilder {
  // Name of the (optional) AndroidManifest metadata setting default notification icon and color.
  public static final String METADATA_DEFAULT_COLOR =
      "com.google.firebase.messaging.default_notification_color";
  public static final String METADATA_DEFAULT_ICON =
      "com.google.firebase.messaging.default_notification_icon";
  public static final String METADATA_DEFAULT_CHANNEL_ID =
      "com.google.firebase.messaging.default_notification_channel_id";
  public static final String FCM_FALLBACK_NOTIFICATION_CHANNEL =
      "fcm_fallback_notification_channel";
  public static final String FCM_FALLBACK_NOTIFICATION_CHANNEL_LABEL =
      "fcm_fallback_notification_channel_label";

  /**
   * Request code used by display notification pending intents.
   *
   * <p>Android only keeps one PendingIntent instance if it thinks multiple pending intents match.
   * Our intents often only differ by the payload which is stored in intent extras. As comparing
   * PendingIntents/Intents does not inspect the payload data, multiple pending intents, such as the
   * ones for click/dismiss will conflict.
   *
   * <p>We also need to avoid conflicts with notifications started by an earlier launch of the app,
   * so use the truncated uptime of when the class was instantiated. The uptime will only overflow
   * every ~50 days, and even then chances of conflict will be rare.
   */
  private static final AtomicInteger requestCodeProvider =
      new AtomicInteger((int) SystemClock.elapsedRealtime());

  // Do not instantiate.
  private CommonNotificationBuilder() {}

  static DisplayNotificationInfo createNotificationInfo(
      Context context, NotificationParams params) {
    Bundle manifestMetadata =
        getManifestMetadata(context.getPackageManager(), context.getPackageName());

    return createNotificationInfo(
        context,
        context.getPackageName(),
        params,
        getOrCreateChannel(context, params.getNotificationChannelId(), manifestMetadata),
        context.getResources(),
        context.getPackageManager(),
        manifestMetadata);
  }

  public static DisplayNotificationInfo createNotificationInfo(
      Context context,
      String pkgName,
      NotificationParams params,
      String channelId,
      Resources appResources,
      PackageManager appPackageManager,
      Bundle manifestMetadata) {

    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);

    String title =
        params.getPossiblyLocalizedString(
            appResources, /* packageName= */ pkgName, /* key= */ MessageNotificationKeys.TITLE);
    if (!TextUtils.isEmpty(title)) {
      builder.setContentTitle(title);
    }

    String body =
        params.getPossiblyLocalizedString(
            appResources, /* packageName = */ pkgName, /* key= */ MessageNotificationKeys.BODY);
    if (!TextUtils.isEmpty(body)) {
      builder.setContentText(body);
      builder.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
    }

    int smallIcon =
        getSmallIcon(
            appPackageManager,
            appResources,
            pkgName,
            params.getString(MessageNotificationKeys.ICON),
            manifestMetadata);
    builder.setSmallIcon(smallIcon);

    Uri sound = getSound(pkgName, params, appResources);
    if (sound != null) {
      builder.setSound(sound);
    }

    builder.setContentIntent(createContentIntent(context, params, pkgName, appPackageManager));

    PendingIntent deleteIntent = createDeleteIntent(context, params);
    if (deleteIntent != null) {
      builder.setDeleteIntent(deleteIntent);
    }

    Integer color =
        getColor(context, params.getString(MessageNotificationKeys.COLOR), manifestMetadata);
    if (color != null) {
      builder.setColor(color);
    }

    // if `sticky` is set, the user desired behavior will override FCM's default `autoCancel` value.
    // if `sticky` is not set, FCM defaults `autoCancel` to true
    boolean sticky = params.getBoolean(MessageNotificationKeys.STICKY);
    builder.setAutoCancel(!sticky);

    boolean localOnly = params.getBoolean(MessageNotificationKeys.LOCAL_ONLY);
    builder.setLocalOnly(localOnly);

    String ticker = params.getString(MessageNotificationKeys.TICKER);
    if (ticker != null) {
      builder.setTicker(ticker);
    }

    Integer notificationPriority = params.getNotificationPriority();
    if (notificationPriority != null) {
      builder.setPriority(notificationPriority);
    }

    Integer visibility = params.getVisibility();
    if (visibility != null) {
      builder.setVisibility(visibility);
    }

    Integer notificationCount = params.getNotificationCount();
    if (notificationCount != null) {
      builder.setNumber(notificationCount);
    }

    Long eventTime = params.getLong(MessageNotificationKeys.EVENT_TIME);
    if (eventTime != null) {
      // for API level >= N, #setShowWhen should be used in conjuction with #setWhen. See:
      // https://developer.android.com/reference/android/app/Notification.Builder#setShowWhen(boolean)
      builder.setShowWhen(true);
      builder.setWhen(eventTime);
    }

    long[] vibrateTimings = params.getVibrateTimings();
    if (vibrateTimings != null) {
      builder.setVibrate(vibrateTimings);
    }

    // set lightSettings if user-defined lightSettings is valid, else skip setting
    int[] lightSettings = params.getLightSettings();
    if (lightSettings != null) {
      builder.setLights(
          /* argb= */ lightSettings[0],
          /* onMs= */ lightSettings[1],
          /* offMs= */ lightSettings[2]);
    }

    builder.setDefaults(getConsolidatedDefaults(params));

    return new DisplayNotificationInfo(builder, getTag(params), /* id= */ 0);
  }

  private static int getConsolidatedDefaults(NotificationParams params) {
    int result = 0; // all flags off.

    if (params.getBoolean(MessageNotificationKeys.DEFAULT_SOUND)) {
      result |= Notification.DEFAULT_SOUND;
    }

    if (params.getBoolean(MessageNotificationKeys.DEFAULT_VIBRATE_TIMINGS)) {
      result |= Notification.DEFAULT_VIBRATE;
    }

    if (params.getBoolean(MessageNotificationKeys.DEFAULT_LIGHT_SETTINGS)) {
      result |= Notification.DEFAULT_LIGHTS;
    }

    return result;
  }

  /**
   * API 26 contains a bug that causes the System UI process to crashloop (leading the device to
   * trigger a factory resets!) if the notification icon is an adaptive icon with a gradient. More
   * info: b/69969749
   */
  @TargetApi(VERSION_CODES.O)
  private static boolean isValidIcon(Resources resources, int resId) {
    // if the fix (ag/2468399) is ever backported to API 26, take SECURITY_PATCH into account.
    if (Build.VERSION.SDK_INT != VERSION_CODES.O) {
      return true;
    }

    try {
      Drawable icon = resources.getDrawable(resId, /* theme= */ null);
      if (icon instanceof AdaptiveIconDrawable) {
        // Adaptive icons without gradients don't cause the crash loop issue but those aren't easy
        // to detect. Thus we reject all adaptive icons.
        // Moreover, an adaptive icon as a notification icon doesn't make sense and won't render
        // properly anyway. (b/69965470#comment10)
        Log.e(TAG, "Adaptive icons cannot be used in notifications. Ignoring icon id: " + resId);
        return false;
      } else {
        return true;
      }
    } catch (Resources.NotFoundException ex) {
      Log.e(TAG, "Couldn't find resource " + resId + ", treating it as an invalid icon");
      return false;
    }
  }

  private static int getSmallIcon(
      PackageManager packageManager,
      Resources resources,
      String pkgName,
      String resourceKey,
      Bundle manifestMetadata) {
    if (!TextUtils.isEmpty(resourceKey)) {

      // if the message contains a specific icon name, try to find it in the resources.
      int iconId = resources.getIdentifier(resourceKey, "drawable", pkgName);

      if (iconId != 0 && isValidIcon(resources, iconId)) {
        return iconId;
      }

      // Also try the mipmap resources if not found in drawable
      iconId = resources.getIdentifier(resourceKey, "mipmap", pkgName);

      if (iconId != 0 && isValidIcon(resources, iconId)) {
        return iconId;
      }

      Log.w(
          TAG, "Icon resource " + resourceKey + " not found. Notification will use default icon.");
    }

    int iconId = manifestMetadata.getInt(METADATA_DEFAULT_ICON, 0);

    if (iconId == 0 || !isValidIcon(resources, iconId)) {
      // No icon found so far. Falling back to default App icon (launcher icon).
      try {
        /* flags= */ iconId = packageManager.getApplicationInfo(pkgName, 0).icon;
      } catch (PackageManager.NameNotFoundException e) {
        Log.w(TAG, "Couldn't get own application info: " + e);
      }
    }

    if (iconId == 0 || !isValidIcon(resources, iconId)) {
      // Wow, app doesn't have a launcher icon. Falling back on icon-placeholder used by the OS.
      iconId = android.R.drawable.sym_def_app_icon;
    }

    return iconId;
  }

  private static Integer getColor(Context context, String color, Bundle manifestMetadata) {
    // Android < Lollipop doesn't have notification color
    if (Build.VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
      return null;
    }

    if (!TextUtils.isEmpty(color)) {
      try {
        return Color.parseColor(color);
      } catch (IllegalArgumentException e) {
        Log.w(TAG, "Color is invalid: " + color + ". Notification will use default color.");
      }
    }

    int colorResourceId = manifestMetadata.getInt(METADATA_DEFAULT_COLOR, 0);
    if (colorResourceId != 0) {
      try {
        return ContextCompat.getColor(context, colorResourceId);
      } catch (Resources.NotFoundException e) {
        Log.w(TAG, "Cannot find the color resource referenced in AndroidManifest.");
      }
    }

    // no color provided by the developer. do not set it.
    return null;
  }

  private static Uri getSound(String pkgName, NotificationParams params, Resources resources) {
    String soundName = params.getSoundResourceName();
    if (TextUtils.isEmpty(soundName)) {
      return null;
    }

    // NOTE: Check in the server for the values that are actually allowed. As in October 2015, only:
    // null or "default"

    // If the sound is not "default", check the app resources for a sound with the same name.
    if (!"default".equals(soundName)) {
      // Check if the provided sound is the name of a valid resource, if so return the URI.
      int soundId = resources.getIdentifier(soundName, "raw", pkgName);
      if (soundId != 0) {
        return Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + pkgName + "/raw/" + soundName);
      }
    }

    // The user request "default" or the resource lookup failed. Play default.
    return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
  }

  @Nullable
  private static PendingIntent createContentIntent(
      Context context, NotificationParams params, String pkgName, PackageManager pm) {
    // If a link is specified use it, otherwise open the app. Notifications should never have
    // both click_action and click_link, but may have neither.
    Intent intent = createTargetIntent(pkgName, params, pm);
    if (intent == null) {
      return null; // Error already logged, just return null
    }

    // Clear top means that if the app is in the background, instead of reopening the currently
    // running activity, it will launch the activity specified in the intent. Given this comes
    // from a notification this is the expected behaviour.
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

    // Populate the Intent with any extras that don't begin with the reserved prefixes (google.c.
    // and google.n.).
    intent.putExtras(params.paramsWithReservedKeysRemoved());

    PendingIntent contentIntent =
        PendingIntent.getActivity(
            context, generatePendingIntentRequestCode(), intent, PendingIntent.FLAG_ONE_SHOT);

    // We need to check metric options against the messageData bundle because we stripped the metric
    // options from the clientVisibleData version
    if (shouldUploadMetrics(params)) {
      contentIntent = wrapContentIntent(context, params, contentIntent);
    }
    return contentIntent;
  }

  private static Intent createTargetIntent(
      String pkgName, NotificationParams params, PackageManager pm) {
    String action = params.getString(MessageNotificationKeys.CLICK_ACTION);
    if (!TextUtils.isEmpty(action)) {
      // Use the specified action
      Intent intent = new Intent(action);
      intent.setPackage(pkgName);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      return intent;
    }

    Uri link = params.getLink();
    if (link != null) {
      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.setPackage(pkgName);
      intent.setData(link);
      return intent;
    }

    // Query the package manager for the best launch intent for the app
    Intent intent = pm.getLaunchIntentForPackage(pkgName);
    if (intent == null) {
      Log.w(TAG, "No activity found to launch app");
    }
    return intent;
  }

  private static Bundle getManifestMetadata(PackageManager pm, String packageName) {
    try {
      ApplicationInfo info = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
      if (info != null && info.metaData != null) {
        return info.metaData;
      }
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, "Couldn't get own application info: " + e);
    }

    return Bundle.EMPTY;
  }

  @TargetApi(VERSION_CODES.O)
  private static String getOrCreateChannel(
      Context context, String msgChannel, Bundle manifestMetadata) {
    if (Build.VERSION.SDK_INT < VERSION_CODES.O) {
      return null;
    }

    // Android < O doesn't have the notification channel API used below, and if the app isn't
    // targeting O don't set a channel to be safe.
    try {
      if (context
              .getPackageManager()
              .getApplicationInfo(context.getPackageName(), /* flags= */ 0)
              .targetSdkVersion
          < VERSION_CODES.O) {
        return null;
      }
    } catch (NameNotFoundException e) {
      // shouldn't happen
      return null;
    }

    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);

    if (!TextUtils.isEmpty(msgChannel)) {
      if (notificationManager.getNotificationChannel(msgChannel) != null) {
        return msgChannel;
      } else {
        Log.w(
            TAG,
            "Notification Channel requested ("
                + msgChannel
                + ") has not been created by the app."
                + " Manifest configuration, or default, value will be used.");
      }
    }

    String manifestChannel = manifestMetadata.getString(METADATA_DEFAULT_CHANNEL_ID);
    if (!TextUtils.isEmpty(manifestChannel)) {
      if (notificationManager.getNotificationChannel(manifestChannel) != null) {
        return manifestChannel;
      } else {
        Log.w(
            TAG,
            "Notification Channel set in AndroidManifest.xml has not been"
                + " created by the app. Default value will be used.");
      }
    } else {
      Log.w(
          TAG,
          "Missing Default Notification Channel metadata in AndroidManifest."
              + " Default value will be used.");
    }

    // Create the default channel if it has not been created yet.
    if (notificationManager.getNotificationChannel(FCM_FALLBACK_NOTIFICATION_CHANNEL) == null) {
      int channelLabelResourceId =
          context
              .getResources()
              .getIdentifier(
                  FCM_FALLBACK_NOTIFICATION_CHANNEL_LABEL, "string", context.getPackageName());

      notificationManager.createNotificationChannel(
          new NotificationChannel(
              // channel id
              FCM_FALLBACK_NOTIFICATION_CHANNEL,
              // user visible name of the channel
              context.getString(channelLabelResourceId),
              // shows everywhere, makes noise, but does not visually intrude.
              NotificationManager.IMPORTANCE_DEFAULT));
    }

    return FCM_FALLBACK_NOTIFICATION_CHANNEL;
  }

  /**
   * Generate a unique(ish) request code for a PendingIntent.
   *
   * <p>See docs on {@link #requestCodeProvider} for more information.
   */
  private static int generatePendingIntentRequestCode() {
    return requestCodeProvider.incrementAndGet();
  }

  private static PendingIntent wrapContentIntent(
      Context context, NotificationParams params, PendingIntent pi) {
    // Need to send analytics, so wrap the activity intent in a PendingIntent that starts the
    // FirebaseMessagingService. The service will launch the activity and send the analytics.
    Intent openIntent =
        new Intent(IntentActionKeys.NOTIFICATION_OPEN)
            .putExtras(params.paramsForAnalyticsIntent())
            .putExtra(IntentKeys.PENDING_INTENT, pi);

    return createMessagingPendingIntent(context, openIntent);
  }

  @Nullable
  private static PendingIntent createDeleteIntent(Context context, NotificationParams params) {
    if (!shouldUploadMetrics(params)) {
      return null;
    }

    Intent dismissIntent =
        new Intent(IntentActionKeys.NOTIFICATION_DISMISS)
            .putExtras(params.paramsForAnalyticsIntent());

    return createMessagingPendingIntent(context, dismissIntent);
  }

  /** Create a PendingIntent to start the app's messaging service via FirebaseInstanceIdReceiver */
  private static PendingIntent createMessagingPendingIntent(Context context, Intent intent) {
    return PendingIntent.getBroadcast(
        context,
        generatePendingIntentRequestCode(),
        new Intent(IntentActionKeys.MESSAGING_EVENT)
            .setComponent(
                new ComponentName(context, "com.google.firebase.iid.FirebaseInstanceIdReceiver"))
            .putExtra(IntentKeys.WRAPPED_INTENT, intent),
        PendingIntent.FLAG_ONE_SHOT);
  }

  /** Check whether we should upload metrics data. */
  static boolean shouldUploadMetrics(@NonNull NotificationParams params) {
    return params.getBoolean(Constants.AnalyticsKeys.ENABLED);
  }

  private static String getTag(NotificationParams params) {
    String tag = params.getString(MessageNotificationKeys.TAG);
    if (!TextUtils.isEmpty(tag)) {
      return tag;
    }

    // No tag set - use a unique custom tag to avoid replacing any of the app's other
    // notifications.
    return "FCM-Notification:" + SystemClock.uptimeMillis();
  }

  /**
   * Encapsulates the information required to display a notification.
   *
   * @hide
   */
  public static class DisplayNotificationInfo {

    public final NotificationCompat.Builder notificationBuilder;
    public final String tag;
    public final int id;

    DisplayNotificationInfo(NotificationCompat.Builder notificationBuilder, String tag, int id) {
      this.notificationBuilder = notificationBuilder;
      this.tag = tag;
      this.id = id;
    }
  }
}
