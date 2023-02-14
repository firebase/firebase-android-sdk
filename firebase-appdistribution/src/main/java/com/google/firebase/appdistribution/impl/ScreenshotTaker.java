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

package com.google.firebase.appdistribution.impl;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.view.PixelCopy;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Singleton;

/** A class that takes screenshots of the host app. */
@Singleton // All instances store images at the same URI, so there should really only be one
class ScreenshotTaker {

  private static final String TAG = "ScreenshotTaker";

  static final String SCREENSHOT_FILE_NAME =
      "com.google.firebase.appdistribution_feedback_screenshot.png";

  private final Context applicationContext;
  private final FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;
  @Background private final Executor backgroundExecutor;

  @Inject
  ScreenshotTaker(
      Context applicationContext,
      FirebaseAppDistributionLifecycleNotifier lifecycleNotifier,
      @Background Executor backgroundExecutor) {
    this.applicationContext = applicationContext;
    this.lifecycleNotifier = lifecycleNotifier;
    this.backgroundExecutor = backgroundExecutor;
  }

  /**
   * Take a screenshot of the running host app and write it to a file.
   *
   * @return a {@link Task} that will complete with the file URI in app-level storage where the
   *     screenshot was written, or null if no activity could be found to screenshot.
   */
  Task<Uri> takeScreenshot() {
    return captureScreenshot()
        .onSuccessTask(backgroundExecutor, bitmap -> Tasks.forResult(writeToFile(bitmap)));
  }

  @VisibleForTesting
  Task<Bitmap> captureScreenshot() {
    return lifecycleNotifier.applyToNullableForegroundActivityTask(
        // Ignore TakeScreenshotAndStartFeedbackActivity class if it's the current activity
        TakeScreenshotAndStartFeedbackActivity.class,
        activity -> {
          if (activity == null) {
            // TakeScreenshotAndStartFeedbackActivity was the current activity and there was no
            // active previous activity
            return Tasks.forResult(null);
          }
          // We only take the screenshot here because this will be called on the main thread, so we
          // want to do as little work as possible
          try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              return captureScreenshotOreo(activity);
            } else {
              return Tasks.forResult(captureScreenshotLegacy(activity));
            }
          } catch (Exception | OutOfMemoryError e) {
            throw new FirebaseAppDistributionException(
                "Failed to take screenshot", Status.UNKNOWN, e);
          }
        });
  }

  private static Bitmap getBitmapForScreenshot(Activity activity) {
    View view = activity.getWindow().getDecorView().getRootView();
    return Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.RGB_565);
  }

  private static Bitmap captureScreenshotLegacy(Activity activity) {
    Bitmap bitmap = getBitmapForScreenshot(activity);
    Canvas canvas = new Canvas(bitmap);
    activity.getWindow().getDecorView().getRootView().draw(canvas);
    return bitmap;
  }

  @TargetApi(Build.VERSION_CODES.O)
  private static Task<Bitmap> captureScreenshotOreo(Activity activity) {
    Bitmap bitmap = getBitmapForScreenshot(activity);
    TaskCompletionSource<Bitmap> taskCompletionSource = new TaskCompletionSource<>();
    try {
      // PixelCopy can handle Bitmaps with Bitmap.Config.HARDWARE
      PixelCopy.request(
          activity.getWindow(),
          bitmap,
          result -> {
            if (result == PixelCopy.SUCCESS) {
              taskCompletionSource.setResult(bitmap);
            } else {
              taskCompletionSource.setException(
                  new FirebaseAppDistributionException(
                      String.format("PixelCopy request failed: %s", result), Status.UNKNOWN));
            }
          },
          new Handler());
    } catch (IllegalArgumentException e) {
      taskCompletionSource.setException(
          new FirebaseAppDistributionException("Bad PixelCopy request", Status.UNKNOWN, e));
    }
    return taskCompletionSource.getTask();
  }

  @Nullable
  private Uri writeToFile(@Nullable Bitmap bitmap) throws FirebaseAppDistributionException {
    if (bitmap == null) {
      return null;
    }

    // First delete the previous file if it's still there
    deleteScreenshot();

    try (BufferedOutputStream outputStream = openFileOutputStream()) {
      // PNG is a lossless format, the compression factor (100) is ignored
      bitmap.compress(Bitmap.CompressFormat.PNG, /* quality= */ 100, outputStream);
    } catch (IOException e) {
      throw new FirebaseAppDistributionException(
          "Failed to write screenshot to storage", Status.UNKNOWN, e);
    }
    return Uri.fromFile(applicationContext.getFileStreamPath(SCREENSHOT_FILE_NAME));
  }

  @VisibleForTesting
  void deleteScreenshot() {
    applicationContext.deleteFile(SCREENSHOT_FILE_NAME);
  }

  private BufferedOutputStream openFileOutputStream() throws FileNotFoundException {
    return new BufferedOutputStream(
        applicationContext.openFileOutput(SCREENSHOT_FILE_NAME, Context.MODE_PRIVATE));
  }
}
