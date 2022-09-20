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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import com.google.auto.value.AutoValue;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class ImageUtils {

  private static final String TAG = "ImageUtils";
  public static final int MAX_IMAGE_READ_RETRIES = 10;
  public static final int IMAGE_READ_RETRY_SLEEP_MS = 300;

  @AutoValue
  abstract static class ImageSize {
    abstract int width();

    abstract int height();

    static ImageSize read(InputStream inputStream) {
      final BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      BitmapFactory.decodeStream(inputStream, /* outPadding= */ null, options);
      return new AutoValue_ImageUtils_ImageSize(options.outWidth, options.outHeight);
    }
  }

  /**
   * Read an image, scaled as small as possible according to the target size.
   *
   * <p>The returned bitmap will be scaled down, preserving the aspect ratio, by the largest power
   * of 2 that results in the width and height still being larger than the target.
   *
   * <p>Based on https://developer.android.com/topic/performance/graphics/load-bitmap#load-bitmap.
   *
   * @return the image
   * @throws FirebaseAppDistributionException if the image could not be read
   */
  public static Bitmap readScaledImage(
      ContentResolver contentResolver, Uri uri, int targetWidth, int targetHeight)
      throws FirebaseAppDistributionException {
    if (targetWidth <= 0 || targetHeight <= 0) {
      throw new FirebaseAppDistributionException(
          String.format(
              "Tried to read image with bad dimensions: %dx%d", targetWidth, targetHeight),
          Status.UNKNOWN);
    }
    ImageSize imageSize = ImageSize.read(waitForInputStream(contentResolver, uri));
    LogWrapper.getInstance().d("Read screenshot image size: " + imageSize);

    final BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize =
        calculateInSampleSize(imageSize.width(), imageSize.height(), targetWidth, targetHeight);
    // Get a fresh input stream because we've exhausted the last one
    return BitmapFactory.decodeStream(
        waitForInputStream(contentResolver, uri), /* outPadding= */ null, options);
  }

  private static InputStream waitForInputStream(ContentResolver contentResolver, Uri uri)
      throws FirebaseAppDistributionException {
    LogWrapper.getInstance().d(TAG, "Trying to read screenshot from URI: " + uri);
    for (int i = 0; i < MAX_IMAGE_READ_RETRIES; i++) {
      try {
        return getInputStream(contentResolver, uri);
      } catch (IllegalStateException e) {
        Log.d(TAG, "Screenshot at URI is still pending. Sleeping.", e);
        try {
          Thread.sleep(IMAGE_READ_RETRY_SLEEP_MS);
        } catch (InterruptedException ex) {
          throw new FirebaseAppDistributionException(
              "Interrupted while waiting for screenshot to become available", Status.UNKNOWN);
        }
      }
    }
    throw new FirebaseAppDistributionException(
        "Timed out waiting for screenshot to be readable.", Status.UNKNOWN);
  }

  private static InputStream getInputStream(ContentResolver contentResolver, Uri uri)
      throws FirebaseAppDistributionException {
    try {
      return contentResolver.openInputStream(uri);
    } catch (FileNotFoundException e) {
      throw new FirebaseAppDistributionException(
          String.format("Could not read screenshot from URI %s", uri), Status.UNKNOWN, e);
    }
  }

  private static int calculateInSampleSize(
      int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
    int inSampleSize = 1;
    if (sourceHeight > targetHeight || sourceWidth > targetWidth) {
      final int halfHeight = sourceHeight / 2;
      final int halfWidth = sourceWidth / 2;

      // Calculate the largest inSampleSize value that is a power of 2 and keeps both
      // height and width larger than the target height and width
      while ((halfHeight / inSampleSize) >= targetHeight
          && (halfWidth / inSampleSize) >= targetWidth) {
        inSampleSize *= 2;
      }
    }

    return inSampleSize;
  }
}
