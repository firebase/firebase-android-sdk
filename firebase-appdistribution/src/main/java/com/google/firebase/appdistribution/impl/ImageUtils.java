package com.google.firebase.appdistribution.impl;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;
import java.io.File;

public class ImageUtils {

  @AutoValue
  abstract static class ImageSize {
    abstract int width();

    abstract int height();
  }

  /**
   * Read an image from a file, scaled as small as possible according to the target size.
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
  public static Bitmap readScaledImage(File file, int targetWidth, int targetHeight) {
    if (targetWidth <= 0 || targetHeight <= 0) {
      throw new IllegalArgumentException(
          String.format(
              "Tried to read image with bad dimensions: %dx%d", targetWidth, targetHeight));
    }
    ImageSize imageSize = getImageSize(file);
    final BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize =
        calculateInSampleSize(imageSize.width(), imageSize.height(), targetWidth, targetHeight);
    return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
  }

  private static ImageSize getImageSize(File file) {
    final BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    return new AutoValue_ImageUtils_ImageSize(options.outWidth, options.outHeight);
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
