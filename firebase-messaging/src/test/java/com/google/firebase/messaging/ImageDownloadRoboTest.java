// Copyright 2020 Google LLC
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
package com.google.firebase.messaging;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.messaging.shadows.ShadowPreconditions;
import com.google.firebase.messaging.testing.TestImageServer;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowPreconditions.class})
public class ImageDownloadRoboTest {

  private static final int TIMEOUT_S = 1; // Not hitting external server

  private static final int MAX_IMAGE_SIZE_BYTES = 1024 * 1024;

  @Rule public TestImageServer testImageServer = new TestImageServer();

  private Executor executor;

  @Before
  public void setUp() throws IOException {
    executor = Executors.newSingleThreadExecutor();
  }

  @Test
  public void nullUrl() {
    assertThat(ImageDownload.create(null)).isNull();
  }

  @Test
  public void emptyUrl() {
    assertThat(ImageDownload.create("")).isNull();
  }

  @Test
  public void malformedUrl() {
    assertThat(ImageDownload.create("not_a_url")).isNull();
  }

  @Test
  public void regularDownload() throws Exception {
    Bitmap servedBitmap =
        TestImageServer.getBitmapFromResource(
            ApplicationProvider.getApplicationContext(), R.drawable.gcm_icon);
    String url = testImageServer.serveBitmap("/gcm_icon", servedBitmap);

    verifyImageDownloadedSuccessfully(url, servedBitmap);
  }

  @Test
  public void contentLength_withinLimit() throws Exception {
    // Note that this sets the content length to the size of the byte array
    byte[] imageData = createFakeImageData(MAX_IMAGE_SIZE_BYTES);
    String url = testImageServer.serveByteArray("/within_limit", imageData);

    verifyImageDownloadedSuccessfully(url, imageData);
  }

  @Test
  public void contentLength_tooLarge() {
    // Note that this sets the content length to the size of the byte array
    byte[] imageData = createFakeImageData(MAX_IMAGE_SIZE_BYTES + 1);
    String url = testImageServer.serveByteArray("/too_large", imageData);

    ImageDownload imageDownload = ImageDownload.create(url);
    imageDownload.start(executor);
    Task<Bitmap> task = imageDownload.getTask();

    ExecutionException exception =
        assertThrows(
            ExecutionException.class, () -> Tasks.await(task, TIMEOUT_S, TimeUnit.SECONDS));
    assertThat(exception).hasCauseThat().isInstanceOf(IOException.class);
  }

  @Test
  public void tooLarge_withoutContentLength() {
    // Note that this sets the content length to the size of the byte array
    byte[] imageData = createFakeImageData(MAX_IMAGE_SIZE_BYTES + 1);
    String url = testImageServer.serveByteArrayWithoutContentLength("/too_large", imageData);

    ImageDownload imageDownload = ImageDownload.create(url);
    imageDownload.start(executor);
    Task<Bitmap> task = imageDownload.getTask();

    ExecutionException exception =
        assertThrows(
            ExecutionException.class, () -> Tasks.await(task, TIMEOUT_S, TimeUnit.SECONDS));
    assertThat(exception).hasCauseThat().isInstanceOf(IOException.class);
  }

  private byte[] createFakeImageData(int size) {
    byte[] data = new byte[size];
    Arrays.fill(data, (byte) 42);
    return data;
  }

  private void verifyImageDownloadedSuccessfully(String url, Bitmap expectedBitmap)
      throws Exception {
    ImageDownload imageDownload = ImageDownload.create(url);
    imageDownload.start(executor);
    Task<Bitmap> task = imageDownload.getTask();
    Bitmap downloadedBitmap = Tasks.await(task, TIMEOUT_S, TimeUnit.SECONDS);

    assertThat(downloadedBitmap.sameAs(expectedBitmap)).isTrue();
  }

  private void verifyImageDownloadedSuccessfully(String url, byte[] expectedImageData)
      throws Exception {
    Bitmap expectedBitmap =
        BitmapFactory.decodeByteArray(
            expectedImageData, /* offset= */ 0, /* length= */ expectedImageData.length);
    verifyImageDownloadedSuccessfully(url, expectedBitmap);
  }
}
