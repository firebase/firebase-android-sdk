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
import static org.robolectric.Shadows.shadowOf;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowBitmapFactory;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
public class ImageUtilsTest {
  private static final int TEST_SCREENSHOT_WIDTH = 1080;
  private static final int TEST_SCREENSHOT_HEIGHT = 2280;
  private static final Bitmap TEST_SCREENSHOT =
      Bitmap.createBitmap(TEST_SCREENSHOT_WIDTH, TEST_SCREENSHOT_HEIGHT, Config.RGB_565);
  private static final Uri TEST_SCREENSHOT_URI = Uri.parse("file:///path/to/screenshot.png");

  private ContentResolver contentResolver;
  private ShadowContentResolver shadowContentResolver;

  @Before
  public void setUp() throws IOException {
    // Makes the shadow behave like the real BitmapFactory, for example returning null for
    // nonexistent files
    ShadowBitmapFactory.setAllowInvalidImageData(false);
    contentResolver = RuntimeEnvironment.getApplication().getContentResolver();
    shadowContentResolver = shadowOf(contentResolver);
    shadowContentResolver.registerInputStream(
        TEST_SCREENSHOT_URI, new ByteArrayInputStream(writeBitmapToByteArray()));
  }

  @Test
  public void readScaledImage_targetIsLessThanHalf_scalesDown() throws IOException {
    Bitmap result =
        ImageUtils.readScaledImage(
            contentResolver,
            TEST_SCREENSHOT_URI,
            TEST_SCREENSHOT_WIDTH / 2 - 100,
            TEST_SCREENSHOT_HEIGHT / 2 - 100);

    assertThat(result.getWidth()).isEqualTo(TEST_SCREENSHOT_WIDTH / 2);
    assertThat(result.getHeight()).isEqualTo(TEST_SCREENSHOT_HEIGHT / 2);
  }

  @Test
  public void readScaledImage_targetExactlyPowerOfTwoSmaller_scalesDown() throws IOException {
    Bitmap result =
        ImageUtils.readScaledImage(
            contentResolver,
            TEST_SCREENSHOT_URI,
            TEST_SCREENSHOT_WIDTH / 4,
            TEST_SCREENSHOT_HEIGHT / 4);

    assertThat(result.getWidth()).isEqualTo(TEST_SCREENSHOT_WIDTH / 4);
    assertThat(result.getHeight()).isEqualTo(TEST_SCREENSHOT_HEIGHT / 4);
  }

  @Test
  public void readScaledImage_targetWidthIsSmaller_scalesDownToFitHeight() throws IOException {
    Bitmap result =
        ImageUtils.readScaledImage(
            contentResolver,
            TEST_SCREENSHOT_URI,
            TEST_SCREENSHOT_WIDTH / 4 - 100,
            TEST_SCREENSHOT_HEIGHT / 2 - 100);

    assertThat(result.getWidth()).isEqualTo(TEST_SCREENSHOT_WIDTH / 2);
    assertThat(result.getHeight()).isEqualTo(TEST_SCREENSHOT_HEIGHT / 2);
  }

  @Test
  public void readScaledImage_targetHeightIsSmaller_scalesDownToFitWidth() throws IOException {
    Bitmap result =
        ImageUtils.readScaledImage(
            contentResolver,
            TEST_SCREENSHOT_URI,
            TEST_SCREENSHOT_WIDTH / 2 - 100,
            TEST_SCREENSHOT_HEIGHT / 4 - 100);

    assertThat(result.getWidth()).isEqualTo(TEST_SCREENSHOT_WIDTH / 2);
    assertThat(result.getHeight()).isEqualTo(TEST_SCREENSHOT_HEIGHT / 2);
  }

  @Test
  public void readScaledImage_targetIsGreaterThanHalf_returnsOriginal() throws IOException {
    Bitmap result =
        ImageUtils.readScaledImage(
            contentResolver,
            TEST_SCREENSHOT_URI,
            TEST_SCREENSHOT_WIDTH - 100,
            TEST_SCREENSHOT_HEIGHT - 100);

    assertThat(result.getWidth()).isEqualTo(TEST_SCREENSHOT_WIDTH);
    assertThat(result.getHeight()).isEqualTo(TEST_SCREENSHOT_HEIGHT);
  }

  @Test
  public void readScaledImage_targetIsGreater_returnsOriginal() throws IOException {
    Bitmap result =
        ImageUtils.readScaledImage(
            contentResolver,
            TEST_SCREENSHOT_URI,
            TEST_SCREENSHOT_WIDTH * 2,
            TEST_SCREENSHOT_HEIGHT * 2);

    assertThat(result.getWidth()).isEqualTo(TEST_SCREENSHOT_WIDTH);
    assertThat(result.getHeight()).isEqualTo(TEST_SCREENSHOT_HEIGHT);
  }

  @Test
  public void readScaledImage_negativeHeight_scalesProportionally() throws IOException {
    Bitmap result =
        ImageUtils.readScaledImage(
            contentResolver, TEST_SCREENSHOT_URI, TEST_SCREENSHOT_WIDTH / 4, -1);

    assertThat(result.getWidth()).isEqualTo(TEST_SCREENSHOT_WIDTH / 4);
    assertThat(result.getHeight()).isEqualTo(TEST_SCREENSHOT_HEIGHT / 4);
  }

  @Test
  public void readScaledImage_negativeWidth_scalesProportionally() throws IOException {
    Bitmap result =
        ImageUtils.readScaledImage(
            contentResolver, TEST_SCREENSHOT_URI, -1, TEST_SCREENSHOT_HEIGHT / 4);

    assertThat(result.getWidth()).isEqualTo(TEST_SCREENSHOT_WIDTH / 4);
    assertThat(result.getHeight()).isEqualTo(TEST_SCREENSHOT_HEIGHT / 4);
  }

  @Test
  public void readScaledImage_negativeDimensions_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ImageUtils.readScaledImage(contentResolver, TEST_SCREENSHOT_URI, -1, -1));
  }

  @Test
  public void readScaledImage_doesntExist_throws() throws IOException {
    assertThrows(
        IOException.class,
        () ->
            ImageUtils.readScaledImage(
                contentResolver,
                Uri.fromFile(new File("nonexistent.png")),
                TEST_SCREENSHOT_WIDTH / 4,
                TEST_SCREENSHOT_HEIGHT / 4));
  }

  private static byte[] writeBitmapToByteArray() throws IOException {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      TEST_SCREENSHOT.compress(CompressFormat.PNG, 100, outputStream);
      return outputStream.toByteArray();
    }
  }
}
