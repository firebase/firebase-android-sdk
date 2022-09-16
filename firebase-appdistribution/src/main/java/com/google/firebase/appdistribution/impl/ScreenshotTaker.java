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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.view.View;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** A class that takes screenshots of the host app. */
class ScreenshotTaker {

  static final String SCREENSHOT_FILE_NAME =
      "com.google.firebase.appdistribution_feedback_screenshot.png";

  private final FirebaseApp firebaseApp;
  private final FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;
  private final Executor taskExecutor;

  ScreenshotTaker(
      FirebaseApp firebaseApp, FirebaseAppDistributionLifecycleNotifier lifecycleNotifier) {
    this(firebaseApp, lifecycleNotifier, Executors.newSingleThreadExecutor());
  }

  @VisibleForTesting
  ScreenshotTaker(
      FirebaseApp firebaseApp,
      FirebaseAppDistributionLifecycleNotifier lifecycleNotifier,
      Executor taskExecutor) {
    this.firebaseApp = firebaseApp;
    this.lifecycleNotifier = lifecycleNotifier;
    this.taskExecutor = taskExecutor;
  }

  /**
   * Take a screenshot of the running host app and write it to a file.
   *
   * @return a {@link Task} that will complete with the file URI in app-level storage where the
   *     screenshot was written.
   */
  Task<Uri> takeScreenshot() {
    return deleteScreenshot()
        .onSuccessTask(unused -> captureScreenshot())
        .onSuccessTask(this::writeToFile);
  }

  /** Deletes any previously taken screenshot. */
  Task<Void> deleteScreenshot() {
    return TaskUtils.runAsyncInTask(
        taskExecutor,
        () -> {
          // throw new IllegalStateException("We got this far");
          firebaseApp.getApplicationContext().deleteFile(SCREENSHOT_FILE_NAME);
          return null;
        });
  }

  @VisibleForTesting
  Task<Bitmap> captureScreenshot() {
    return lifecycleNotifier.applyToForegroundActivity(
        activity -> {
          // We only take the screenshot here because this will be called on the main thread, so we
          // want to do as little work as possible
          try {
            View view = activity.getWindow().getDecorView().getRootView();
            Bitmap bitmap =
                Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
            return bitmap;
          } catch (Exception | OutOfMemoryError e) {
            throw new FirebaseAppDistributionException(
                "Failed to take screenshot", Status.UNKNOWN, e);
          }
        });
  }

  private Task<Uri> writeToFile(Bitmap bitmap) {
    return TaskUtils.runAsyncInTask(
        taskExecutor,
        () -> {
          try (FileOutputStream outputStream = openFileOutputStream()) {
            // PNG is a lossless format, the compression factor (100) is ignored
            bitmap.compress(Bitmap.CompressFormat.PNG, /* quality= */ 100, outputStream);
          } catch (IOException e) {
            throw new FirebaseAppDistributionException(
                "Failed to write screenshot to storage", Status.UNKNOWN, e);
          }
          return Uri.fromFile(firebaseApp.getApplicationContext()
                  .getFileStreamPath(SCREENSHOT_FILE_NAME));
        });
  }

  private FileOutputStream openFileOutputStream() throws FileNotFoundException {
    return firebaseApp
        .getApplicationContext()
        .openFileOutput(SCREENSHOT_FILE_NAME, Context.MODE_PRIVATE);
  }
}
