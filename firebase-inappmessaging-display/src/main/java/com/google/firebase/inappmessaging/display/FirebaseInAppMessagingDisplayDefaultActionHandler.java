package com.google.firebase.inappmessaging.display;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabsIntent;
import com.google.firebase.inappmessaging.display.internal.Logging;
import com.google.firebase.inappmessaging.model.Action;
import java.util.List;

/**
 * The default handler of message actions. It opens a Custom Tab with the link if possible or tries to find an Activity
 * that can handle the action URL
 */
public class FirebaseInAppMessagingDisplayDefaultActionHandler implements FirebaseInAppMessagingDisplayActionHandler {
  @Override
  public void handleAction(@NonNull Activity activity, @NonNull Action action) {
    Uri uri = Uri.parse(action.getActionUrl());
    if (ishttpOrHttpsUri(uri) && supportsCustomTabs(activity)) {
      // If we can launch a chrome view, try that.
      CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
      Intent intent = customTabsIntent.intent;
      intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      customTabsIntent.launchUrl(activity, uri);
    } else {
      // If we can't launch a chrome view try to launch anything that can handle a URL.
      Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
      ResolveInfo info = activity.getPackageManager().resolveActivity(browserIntent, 0);
      browserIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
      browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      if (info != null) {
        activity.startActivity(browserIntent);
      } else {
        // If the device can't resolve a url then log, but don't crash.
        Logging.loge("Device cannot resolve intent for: " + Intent.ACTION_VIEW);
      }
    }
  }

  private boolean supportsCustomTabs(Activity activity) {
    Intent customTabIntent = new Intent("android.support.customtabs.action.CustomTabsService");
    customTabIntent.setPackage("com.android.chrome");
    List<ResolveInfo> resolveInfos =
        activity.getPackageManager().queryIntentServices(customTabIntent, 0);
    return !resolveInfos.isEmpty();
  }

  private boolean ishttpOrHttpsUri(Uri uri) {
    if (uri == null) {
      return false;
    }
    String scheme = uri.getScheme();
    return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
  }
}
