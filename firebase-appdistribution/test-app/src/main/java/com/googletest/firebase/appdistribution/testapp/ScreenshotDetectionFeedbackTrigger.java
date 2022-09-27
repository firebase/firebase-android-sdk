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

import android.app.AlertDialog;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import java.util.HashSet;
import java.util.Set;

class ScreenshotDetectionFeedbackTrigger extends ContentObserver {
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

  private final AppCompatActivity activity;
  private final int infoTextResourceId;

  private final ActivityResultLauncher<String> requestPermissionLauncher;

  private Uri currentUri;

  private boolean hasRequestedPermission = false;

  /**
   * Creates a FeedbackTriggers instance for an activity.
   *
   * <p>This must be called during activity initialization.
   *
   * @param activity The host activity
   * @param handler The handler to run {@link #onChange} on, or null if none.
   */
  public ScreenshotDetectionFeedbackTrigger(
      AppCompatActivity activity, int infoTextResourceId, Handler handler) {
    super(handler);
    this.activity = activity;
    this.infoTextResourceId = infoTextResourceId;

    // Register the permissions launcher
    requestPermissionLauncher =
        activity.registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
              if (isGranted) {
                maybeStartFeedbackForScreenshot(currentUri);
              } else {
                Log.i(TAG, "Permission not granted");
                // TODO: Ideally we would show a message indicating the impact of not enabling the
                // permission, but there's no way to know if they've permanently denied the
                // permission, and we don't want to show them a message on every screenshot.
              }
            });
  }

  void registerScreenshotObserver() {
    activity
        .getContentResolver()
        .registerContentObserver(
            Media.EXTERNAL_CONTENT_URI, /* notifyForDescendants= */ true, this);
  }

  void unRegisterScreenshotObserver() {
    activity.getContentResolver().unregisterContentObserver(this);
  }

  @Override
  public void onChange(boolean selfChange, Uri uri) {
    if (!uri.toString().matches(String.format("%s/[0-9]+", Media.EXTERNAL_CONTENT_URI))
        || seenImages.contains(uri)) {
      return;
    }

    if (ContextCompat.checkSelfPermission(activity, PERMISSION_TO_REQUEST) == PERMISSION_GRANTED) {
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

  private void requestReadPermission(Uri uri) {
    if (activity.shouldShowRequestPermissionRationale(PERMISSION_TO_REQUEST)) {
      Log.i(TAG, "Showing customer rationale for requesting permission.");
      new AlertDialog.Builder(activity)
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
      cursor = activity.getContentResolver().query(uri, PROJECTION, null, null, null);
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
}
