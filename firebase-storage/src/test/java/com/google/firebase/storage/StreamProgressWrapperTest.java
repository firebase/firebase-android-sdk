// Copyright 2018 Google LLC
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

package com.google.firebase.storage;

import static com.google.firebase.common.testutil.Assert.assertThrows;

import android.os.Build;
import com.google.firebase.storage.StreamDownloadTask.StreamProgressWrapper;
import com.google.firebase.storage.network.MockInputStreamHelper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link StreamProgressWrapper}. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.LOLLIPOP_MR1)
@SuppressWarnings("ResultOfMethodCallIgnored")
public class StreamProgressWrapperTest {

  @Rule public RetryRule retryRule = new RetryRule(3);
  private static final byte[] SMALL_DATASET = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};
  private static final byte[] LARGE_DATASET =
      new byte[(int) (StreamDownloadTask.PREFERRED_CHUNK_SIZE * 4.1)];

  @BeforeClass
  public static void beforeClass() {
    for (int i = 0; i < LARGE_DATASET.length; ++i) {
      LARGE_DATASET[i] = (byte) (Math.random() * Byte.MAX_VALUE);
    }
  }

  @Test
  public void noExceptionSmallDataset() throws Exception {
    InputStream inputStream =
        new StreamProgressWrapper(() -> new ByteArrayInputStream(SMALL_DATASET), null);

    byte[] buffer = new byte[3];

    Assert.assertEquals(9, inputStream.available());
    Assert.assertEquals(3, inputStream.read(buffer));
    Assert.assertArrayEquals(new byte[] {0, 1, 2}, buffer);
    Assert.assertEquals(6, inputStream.available());
    Assert.assertEquals(3, inputStream.skip(3));
    Assert.assertEquals(3, inputStream.available());
    Assert.assertEquals(6, inputStream.read());
    Assert.assertEquals(2, inputStream.available());
    Assert.assertEquals(2, inputStream.read(buffer));
    Assert.assertArrayEquals(new byte[] {7, 8, 2}, buffer);
    Assert.assertEquals(0, inputStream.available());
    inputStream.close();

    try {
      inputStream.read();
      Assert.fail("Input stream is closed");
    } catch (IOException ignore) {
      // success
    }
  }

  @Test
  public void multipleExceptionsSmallDataset() throws Exception {
    final MockInputStreamHelper inputStreamHelper = new MockInputStreamHelper(SMALL_DATASET);
    inputStreamHelper.injectExceptionAt(4);
    inputStreamHelper.injectExceptionAt(8);

    InputStream inputStream = new StreamProgressWrapper(() -> inputStreamHelper, null);

    byte[] buffer = new byte[3];

    Assert.assertEquals(9, inputStream.available());
    Assert.assertEquals(3, inputStream.read(buffer));
    Assert.assertArrayEquals(new byte[] {0, 1, 2}, buffer);
    Assert.assertEquals(6, inputStream.available());
    Assert.assertEquals(3, inputStream.skip(3));
    Assert.assertEquals(3, inputStream.available());
    Assert.assertEquals(6, inputStream.read());
    Assert.assertEquals(2, inputStream.available());
    Assert.assertEquals(2, inputStream.read(buffer));
    Assert.assertArrayEquals(new byte[] {7, 8, 2}, buffer);
    Assert.assertEquals(0, inputStream.available());
    inputStream.close();

    try {
      inputStream.read();
      Assert.fail("Input stream is closed");
    } catch (IOException ignore) {
      // success
    }
  }

  @Test
  public void exceptionOnStart() throws Exception {
    InputStream inputStream =
        new StreamProgressWrapper(
            () -> {
              throw new IllegalStateException("Oh no");
            },
            null);

    assertThrows(IOException.class, inputStream::available);
  }

  @Test
  public void emptyDownload() throws Exception {
    InputStream inputStream =
        new StreamProgressWrapper(() -> new ByteArrayInputStream(new byte[] {}), null);

    byte[] buffer = new byte[10];

    Assert.assertEquals(-1, inputStream.read(buffer));
    Assert.assertEquals(-1, inputStream.read(buffer));
  }

  @Test
  public void noProgressThrowsException() throws Exception {
    final List<InputStream> streams = new ArrayList<>();
    streams.add(new MockInputStreamHelper(SMALL_DATASET).injectExceptionAt(2));
    streams.add(new MockInputStreamHelper(SMALL_DATASET).injectExceptionAt(0));

    InputStream inputStream = new StreamProgressWrapper(() -> streams.remove(0), null);

    byte[] buffer = new byte[10];
    Assert.assertEquals(0, inputStream.read());

    try {
      Assert.assertEquals(1, inputStream.read(buffer));
      inputStream.read();
      Assert.fail("We made no progress on second stream");
    } catch (Exception ignore) {
      // success
    }
  }

  @Test
  public void multipleExceptionsReadAll() throws Exception {
    MockInputStreamHelper inputStreamHelper = new MockInputStreamHelper(LARGE_DATASET);
    inputStreamHelper.injectExceptionAt((int) StreamDownloadTask.PREFERRED_CHUNK_SIZE + 1);
    inputStreamHelper.injectExceptionAt((int) StreamDownloadTask.PREFERRED_CHUNK_SIZE * 2 + 1);

    InputStream inputStream = new StreamProgressWrapper(() -> inputStreamHelper, null);

    byte[] buffer = new byte[LARGE_DATASET.length];

    Assert.assertEquals(LARGE_DATASET.length, inputStream.available());
    Assert.assertEquals(LARGE_DATASET.length, inputStream.read(buffer));
    Assert.assertArrayEquals(LARGE_DATASET, buffer);
  }

  @Test
  public void multipleExceptionsRandomReads() throws Exception {
    MockInputStreamHelper inputStreamHelper = new MockInputStreamHelper(LARGE_DATASET);
    inputStreamHelper.injectExceptionAt(1);
    inputStreamHelper.injectExceptionAt(5);
    inputStreamHelper.injectExceptionAt(7);
    inputStreamHelper.injectExceptionAt(9);
    inputStreamHelper.injectExceptionAt(20);
    inputStreamHelper.injectExceptionAt((int) StreamDownloadTask.PREFERRED_CHUNK_SIZE - 1);
    inputStreamHelper.injectExceptionAt((int) StreamDownloadTask.PREFERRED_CHUNK_SIZE);
    inputStreamHelper.injectExceptionAt((int) StreamDownloadTask.PREFERRED_CHUNK_SIZE + 1);

    InputStream inputStream = new StreamProgressWrapper(() -> inputStreamHelper, null);

    ByteBuffer outputBuffer = ByteBuffer.allocate(LARGE_DATASET.length);

    int expectedSizeDiffers = 0;

    while (true) {
      byte[] buffer = new byte[50];
      int blockSize = (int) (Math.random() * buffer.length);
      int read = inputStream.read(buffer, 0, blockSize);

      if (read == -1) {
        break;
      }

      if (blockSize != read) {
        ++expectedSizeDiffers;
      }

      outputBuffer.put(buffer, 0, read);
    }

    Assert.assertTrue("Only the last read can have different size", expectedSizeDiffers <= 1);
    Assert.assertArrayEquals(LARGE_DATASET, outputBuffer.array());
  }

  @Test
  public void randomSkips() throws Exception {
    MockInputStreamHelper inputStreamHelper = new MockInputStreamHelper(LARGE_DATASET);

    int[] skips =
        new int[] {
          1,
          5,
          7,
          9,
          20,
          (int) StreamDownloadTask.PREFERRED_CHUNK_SIZE - 1,
          (int) StreamDownloadTask.PREFERRED_CHUNK_SIZE,
          (int) StreamDownloadTask.PREFERRED_CHUNK_SIZE + 1
        };

    InputStream inputStream = new StreamProgressWrapper(() -> inputStreamHelper, null);

    Assert.assertFalse(inputStream.markSupported());
    inputStream.mark(0); // no-op

    for (int skip : skips) {
      Assert.assertEquals(skip, inputStream.skip(skip));
    }
  }
}
