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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.google.firebase.appdistribution.FirebaseAppDistributionException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowBitmapFactory;

@RunWith(RobolectricTestRunner.class)
public class ImageUtilsTest {
  private static final String TEST_FILENAME = "test.png";
  private static final int TEST_SCREENSHOT_WIDTH = 1080;
  private static final int TEST_SCREENSHOT_HEIGHT = 2280;
  private static final Bitmap TEST_SCREENSHOT =
      Bitmap.createBitmap(TEST_SCREENSHOT_WIDTH, TEST_SCREENSHOT_HEIGHT, Config.RGB_565);

  private ContentResolver contentResolver;

  @Before
  public void setUp() {
    // Makes the shadow behave like the real BitmapFactory, for example returning null for
    // nonexistent files
    ShadowBitmapFactory.setAllowInvalidImageData(false);
    contentResolver = RuntimeEnvironment.getApplication().getContentResolver();
  }

  @Test
  public void readScaledImage_targetIsLessThanHalf_scalesDown() throws IOException, FirebaseAppDistributionException {
    Uri uri = writeBitmapToTmpFile();
    Bitmap result =
        ImageUtils.readScaledImage(
                contentResolver, uri, TEST_SCREENSHOT_WIDTH / 2 - 100, TEST_SCREENSHOT_HEIGHT / 2 - 100);

    assertThat(result.getWidth()).isEqualTo(TEST_SCREENSHOT_WIDTH / 2);
    assertThat(result.getHeight()).isEqualTo(TEST_SCREENSHOT_HEIGHT / 2);
  }

  @Test
  public void readScaledImage_targetExactlyPowerOfTwoSmaller_scalesDown() throws IOException, FirebaseAppDistributionException {
    Uri uri = writeBitmapToTmpFile();
    Bitmap result =
            ImageUtils.readScaledImage(
                    contentResolver, uri, TEST_SCREENSHOT_WIDTH / 4, TEST_SCREENSHOT_HEIGHT / 4);

    assertThat(result.getWidth()).isEqualTo(TEST_SCREENSHOT_WIDTH / 4);
    assertThat(result.getHeight()).isEqualTo(TEST_SCREENSHOT_HEIGHT / 4);
  }

  @Test
  public void readScaledImage_targetWidthIsSmaller_scalesDownToFitHeight() throws IOException, FirebaseAppDistributionException {
    Uri uri = writeBitmapToTmpFile();
    Bitmap result =
            ImageUtils.readScaledImage(
                    contentResolver, uri, TEST_SCREENSHOT_WIDTH / 4 - 100, TEST_SCREENSHOT_HEIGHT / 2 - 100);

    assertThat(result.getWidth()).isEqualTo(TEST_SCREENSHOT_WIDTH / 2);
    assertThat(result.getHeight()).isEqualTo(TEST_SCREENSHOT_HEIGHT / 2);
  }

  @Test
  public void readScaledImage_targetHeightIsSmaller_scalesDownToFitWidth() throws IOException, FirebaseAppDistributionException {
    Uri uri = writeBitmapToTmpFile();
    Bitmap result =
            ImageUtils.readScaledImage(
                    contentResolver, uri, TEST_SCREENSHOT_WIDTH / 2 - 100, TEST_SCREENSHOT_HEIGHT / 4 - 100);

    assertThat(result.getWidth()).isEqualTo(TEST_SCREENSHOT_WIDTH / 2);
    assertThat(result.getHeight()).isEqualTo(TEST_SCREENSHOT_HEIGHT / 2);
  }

  @Test
  public void readScaledImage_targetIsGreaterThanHalf_returnsOriginal() throws IOException, FirebaseAppDistributionException {
    Uri uri = writeBitmapToTmpFile();
    Bitmap result =
            ImageUtils.readScaledImage(
                    contentResolver, uri, TEST_SCREENSHOT_WIDTH - 100, TEST_SCREENSHOT_HEIGHT - 100);

    assertThat(result.getWidth()).isEqualTo(TEST_SCREENSHOT_WIDTH);
    assertThat(result.getHeight()).isEqualTo(TEST_SCREENSHOT_HEIGHT);
  }

  @Test
  public void readScaledImage_targetIsGreater_returnsOriginal() throws IOException, FirebaseAppDistributionException {
    Uri uri = writeBitmapToTmpFile();
    Bitmap result =
            ImageUtils.readScaledImage(
                    contentResolver, uri, TEST_SCREENSHOT_WIDTH * 2, TEST_SCREENSHOT_HEIGHT * 2);

    assertThat(result.getWidth()).isEqualTo(TEST_SCREENSHOT_WIDTH);
    assertThat(result.getHeight()).isEqualTo(TEST_SCREENSHOT_HEIGHT);
  }

  @Test
  public void readScaledImage_zeroDimension_throws() throws IOException {
    Uri uri = writeBitmapToTmpFile();
    assertThrows(IllegalArgumentException.class, () -> ImageUtils.readScaledImage(contentResolver, uri, 500, 0));
  }

  @Test
  public void readScaledImage_doesntExist_throws() throws IOException {
    assertThrows(
        IllegalArgumentException.class,
        () -> ImageUtils.readScaledImage(contentResolver, Uri.fromFile(new File("nonexistent.png")), 500, 0));
  }

  private static Uri writeBitmapToTmpFile() throws IOException {
    // Write bitmap to file
    try (FileOutputStream outputStream =
        ApplicationProvider.getApplicationContext()
            .openFileOutput(TEST_FILENAME, Context.MODE_PRIVATE)) {
      TEST_SCREENSHOT.compress(CompressFormat.PNG, 100, outputStream);
    }
    File file = ApplicationProvider.getApplicationContext().getFileStreamPath(TEST_FILENAME);
    return Uri.fromFile(file);
  }
}
