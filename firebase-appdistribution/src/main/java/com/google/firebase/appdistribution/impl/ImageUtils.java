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
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class ImageUtils {

  private static final String TAG = "ImageUtils";

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
   * @return the image, or null if it could not be decoded
   * @throws IllegalArgumentException if target height or width are less than or equal to zero
   */
  @Nullable
  public static Bitmap readScaledImage(ContentResolver contentResolver, Uri uri, int targetWidth, int targetHeight) {
    if (targetWidth <= 0 || targetHeight <= 0) {
      throw new IllegalArgumentException(
          String.format(
              "Tried to read image with bad dimensions: %dx%d", targetWidth, targetHeight));
    }
    ImageSize imageSize = ImageSize.read(getInputStream(contentResolver, uri));
    LogWrapper.getInstance().i("Read image size: " + imageSize);
    final BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize =
        calculateInSampleSize(imageSize.width(), imageSize.height(), targetWidth, targetHeight);
    return BitmapFactory.decodeStream(getInputStream(contentResolver, uri), /* outPadding= */ null, options);
  }

  private static @Nullable InputStream getInputStream(ContentResolver contentResolver, Uri uri) {
    if (uri == null) {
      LogWrapper.getInstance().i(TAG, "No screenshot URI provided.");
      return null;
    }
    LogWrapper.getInstance().i(TAG, "Trying to read screenshot from URI: " + uri);
    try {
      return contentResolver.openInputStream(uri);
    } catch (FileNotFoundException e) {
      LogWrapper.getInstance().e(TAG, String.format("Could not read screenshot from URI %s", uri), e);
      return null;
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
