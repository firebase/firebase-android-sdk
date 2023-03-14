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

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import java.util.concurrent.Executor;
import javax.inject.Inject;

/** Sends tester feedback to the Tester API. */
class FeedbackSender {
  static final String CONTENT_TYPE_JPEG = "image/jpeg";
  static final String CONTENT_TYPE_PNG = "image/png";

  private static final String TAG = "FeedbackSender";
  private static final String FILE_EXTENSION_JPG = ".jpg";
  private static final String FILE_EXTENSION_JPEG = ".jpeg";
  private static final String FILE_EXTENSION_PNG = ".png";
  private static final String DEFAULT_FILENAME = "screenshot.png";

  private final ContentResolver contentResolver;
  private final FirebaseAppDistributionTesterApiClient testerApiClient;
  @Lightweight private final Executor lightweightExecutor;

  @Inject
  FeedbackSender(
      ContentResolver contentResolver,
      FirebaseAppDistributionTesterApiClient testerApiClient,
      @Lightweight Executor lightweightExecutor) {
    this.contentResolver = contentResolver;
    this.testerApiClient = testerApiClient;
    this.lightweightExecutor = lightweightExecutor;
  }

  /** Send feedback text and optionally a screenshot to the Tester API for the given release. */
  Task<Void> sendFeedback(
      String releaseName,
      String feedbackText,
      @Nullable Uri screenshotUri,
      FeedbackTrigger trigger) {
    return testerApiClient
        .createFeedback(releaseName, feedbackText)
        .onSuccessTask(
            lightweightExecutor, feedbackName -> attachScreenshot(feedbackName, screenshotUri))
        .onSuccessTask(
            lightweightExecutor,
            feedbackName -> testerApiClient.commitFeedback(feedbackName, trigger));
  }

  private Task<String> attachScreenshot(String feedbackName, @Nullable Uri screenshotUri) {
    if (screenshotUri == null) {
      return Tasks.forResult(feedbackName);
    }
    if (!screenshotUri.getScheme().equals("file") && !screenshotUri.getScheme().equals("content")) {
      return Tasks.forException(
          new FirebaseAppDistributionException(
              String.format(
                  "Screenshot URI '%s' was not a content or file URI. Not starting feedback.",
                  screenshotUri),
              Status.UNKNOWN));
    }
    return testerApiClient.attachScreenshot(
        feedbackName,
        screenshotUri,
        getScreenshotFilename(screenshotUri),
        getContentType(screenshotUri));
  }

  private String getContentType(Uri screenshotUri) {
    String contentType = null;
    if (screenshotUri.getScheme().equals("file")) {
      contentType = getContentTypeForFilename(screenshotUri.getLastPathSegment());
    } else if (screenshotUri.getScheme().equals("content")) {
      contentType = contentResolver.getType(screenshotUri);
    }
    if (contentType == null) {
      LogWrapper.w(
          TAG,
          String.format("Could not get content type for URI %s. Assuming PNG.", screenshotUri));
      return CONTENT_TYPE_PNG;
    }
    return contentType;
  }

  private String getScreenshotFilename(Uri screenshotUri) {
    if (screenshotUri.getScheme().equals("file")) {
      return screenshotUri.getLastPathSegment();
    } else {
      return getContentFilename(screenshotUri);
    }
  }

  private String getContentFilename(Uri contentUri) {
    try (Cursor returnCursor =
        contentResolver.query(
            contentUri,
            new String[] {OpenableColumns.DISPLAY_NAME},
            /* selection= */ null,
            /* projectionArgs= */ null,
            /* sortOrder= */ null)) {
      if (returnCursor == null) {
        LogWrapper.w(
            TAG,
            String.format(
                "Unable to get filename from URI '%s', using default '%s'",
                contentUri, DEFAULT_FILENAME));
        return DEFAULT_FILENAME;
      }
      int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
      returnCursor.moveToFirst();
      String name = returnCursor.getString(nameIndex);
      return name;
    } catch (Exception e) {
      LogWrapper.e(
          TAG,
          String.format(
              "Error getting filename from URI '%s', using default '%s'",
              contentUri, DEFAULT_FILENAME),
          e);
      return DEFAULT_FILENAME;
    }
  }

  @Nullable
  private static String getContentTypeForFilename(String filename) {
    if (filename.endsWith(FILE_EXTENSION_JPG) || filename.endsWith(FILE_EXTENSION_JPEG)) {
      return CONTENT_TYPE_JPEG;
    } else if (filename.endsWith(FILE_EXTENSION_PNG)) {
      return CONTENT_TYPE_PNG;
    } else {
      return null;
    }
  }
}
