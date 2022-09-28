// Copyright 2022 Google LLC
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

package com.googletest.firebase.appdistribution.testapp;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.READ_MEDIA_IMAGES;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import java.util.HashSet;
import java.util.Set;

class ScreenshotDetectionFeedbackTrigger extends ContentObserver
    implements Application.ActivityLifecycleCallbacks {
  private static final String TAG = "ScreenshotDetectionFeedbackTrigger";
  private static final String PERMISSION_TO_REQUEST =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
          ? READ_MEDIA_IMAGES
          : READ_EXTERNAL_STORAGE;
  private static final boolean SHOULD_CHECK_IF_PENDING = Build.VERSION.SDK_INT >= 29;
  private static final String[] PROJECTION =
      SHOULD_CHECK_IF_PENDING
          ? new String[] {MediaStore.Images.Media.DATA, MediaStore.MediaColumns.IS_PENDING}
          : new String[] {MediaStore.Images.Media.DATA};

  private final Set<Uri> seenImages = new HashSet<>();
  private final int infoTextResourceId;

  private ActivityResultLauncher<String> requestPermissionLauncher;
  private Activity currentActivity;
  private Uri currentUri;
  private boolean isEnabled = false;
  private boolean hasRequestedPermission = false;

  private static ScreenshotDetectionFeedbackTrigger instance;

  /**
   * Initialize the screenshot detection trigger for this application.
   *
   * <p>This should be called during {@link Application#onCreate()}. {@link #enable()} should then
   * be called when you want to actually start detecting screenshots.
   *
   * @param application the {@link Application} object
   * @param infoTextResourceId resource ID of info text to show to testers before giving feedback
   */
  public static void initialize(Application application, int infoTextResourceId) {
    if (instance == null) {
      HandlerThread handlerThread = new HandlerThread("AppDistroFeedbackTrigger");
      handlerThread.start();
      instance =
          new ScreenshotDetectionFeedbackTrigger(
              application, infoTextResourceId, new Handler(handlerThread.getLooper()));
    }
  }

  /**
   * Start listening for screenshots, and start feedback when a new screenshot is detected.
   *
   * @throws IllegalStateException if {@link #initialize} has not been called yet
   */
  public static void enable() {
    if (instance == null) {
      throw new IllegalStateException(
          "You must call initialize() in your Application.onCreate() before enabling screenshot detection");
    }
    if (!instance.isEnabled) {
      instance.isEnabled = true;
      instance.listenForScreenshots();
    }
  }

  /**
   * Stop listening for screenshots.
   *
   * @throws IllegalStateException if {@link #initialize} has not been called yet
   */
  public static void disable() {
    if (instance == null) {
      throw new IllegalStateException(
          "You must call initialize() in your Application.onCreate() before enabling screenshot detection");
    }
    if (instance.isEnabled) {
      instance.isEnabled = false;
      instance.stopListeningForScreenshots();
    }
  }

  private ScreenshotDetectionFeedbackTrigger(
      Application application, int infoTextResourceId, Handler handler) {
    super(handler);
    this.infoTextResourceId = infoTextResourceId;
    application.registerActivityLifecycleCallbacks(this);
  }

  @Override
  public void onChange(boolean selfChange, Uri uri) {
    if (currentActivity == null) {
      Log.w(TAG, "There is no current activity. Ignoring change to external media.");
      return;
    }

    if (!uri.toString().matches(String.format("%s/[0-9]+", Media.EXTERNAL_CONTENT_URI))
        || seenImages.contains(uri)) {
      return;
    }

    if (ContextCompat.checkSelfPermission(currentActivity, PERMISSION_TO_REQUEST)
        == PERMISSION_GRANTED) {
      maybeStartFeedbackForScreenshot(uri);
    } else if (hasRequestedPermission) {
      Log.i(
          TAG,
          "We've already request permission. Not requesting again for the life of the activity.");
    } else {
      // Set an in memory flag so we don't ask them again right away
      hasRequestedPermission = true;
      requestReadPermission(uri);
    }
  }

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    if (activity instanceof ActivityResultCaller) {
      requestPermissionLauncher =
          ((ActivityResultCaller) activity)
              .registerForActivityResult(
                  new ActivityResultContracts.RequestPermission(),
                  isGranted -> {
                    if (!isEnabled) {
                      Log.w(
                          TAG,
                          "Trigger disabled after permission check. Abandoning screenshot detection.");
                    } else if (currentActivity == null) {
                      Log.w(
                          TAG,
                          "There is no current activity after permission check. Abandoning screenshot detection.");
                    } else if (isGranted) {
                      maybeStartFeedbackForScreenshot(currentUri);
                    } else {
                      Log.i(TAG, "Permission not granted");
                      // TODO: Ideally we would show a message indicating the impact of not enabling
                      // the
                      // permission, but there's no way to know if they've permanently denied the
                      // permission, and we don't want to show them a message on every screenshot.
                    }
                  });
    } else {
      Log.w(
          TAG,
          "Not listening for screenshots because this activity can't register for permission request results: "
              + activity);
    }
  }

  @Override
  public void onActivityResumed(@NonNull Activity activity) {
    currentActivity = activity;
    if (isEnabled) {
      listenForScreenshots();
    }
  }

  @Override
  public void onActivityPaused(@NonNull Activity activity) {
    if (isEnabled) {
      stopListeningForScreenshots();
    }
    currentActivity = null;
  }

  private void requestReadPermission(Uri uri) {
    if (currentActivity.shouldShowRequestPermissionRationale(PERMISSION_TO_REQUEST)) {
      Log.i(TAG, "Showing customer rationale for requesting permission.");
      new AlertDialog.Builder(currentActivity)
          .setMessage(
              "Taking a screenshot of the app can initiate feedback to the developer. To enable this feature, allow the app access to device storage.")
          .setPositiveButton(
              "OK",
              (a, b) -> {
                Log.i(TAG, "Launching request for permission.");
                currentUri = uri;
                requestPermissionLauncher.launch(PERMISSION_TO_REQUEST);
              })
          .show();
    } else {
      Log.i(TAG, "Launching request for permission without rationale.");
      currentUri = uri;
      requestPermissionLauncher.launch(PERMISSION_TO_REQUEST);
    }
  }

  private void maybeStartFeedbackForScreenshot(Uri uri) {
    Cursor cursor = null;
    try {
      cursor = currentActivity.getContentResolver().query(uri, PROJECTION, null, null, null);
      if (cursor != null && cursor.moveToFirst()) {
        if (SHOULD_CHECK_IF_PENDING
            && cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_PENDING))
                == 1) {
          Log.i(TAG, "Ignoring pending image: " + uri);
          return;
        }
        seenImages.add(uri);
        String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
        Log.i(TAG, "Path: " + path);
        if (path.toLowerCase().contains("screenshot")) {
          FirebaseAppDistribution.getInstance().startFeedback(infoTextResourceId, uri);
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "Could not determine if media change was due to taking a screenshot", e);
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  private void listenForScreenshots() {
    if (currentActivity != null) {
      currentActivity
          .getContentResolver()
          .registerContentObserver(
              Media.EXTERNAL_CONTENT_URI, /* notifyForDescendants= */ true, this);
    }
  }

  private void stopListeningForScreenshots() {
    if (currentActivity != null) {
      currentActivity.getContentResolver().unregisterContentObserver(this);
    }
  }

  // Other lifecycle methods
  @Override
  public void onActivityDestroyed(@NonNull Activity activity) {}

  @Override
  public void onActivityStarted(@NonNull Activity activity) {}

  @Override
  public void onActivityStopped(@NonNull Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
}
