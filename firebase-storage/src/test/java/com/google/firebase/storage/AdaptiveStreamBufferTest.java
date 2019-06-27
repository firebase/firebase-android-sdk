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

import static org.junit.Assert.assertArrayEquals;

import android.os.Build;
import com.google.firebase.storage.internal.AdaptiveStreamBuffer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link AdaptiveStreamBuffer}. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.LOLLIPOP_MR1)
public class AdaptiveStreamBufferTest {

  @Rule public RetryRule retryRule = new RetryRule(3);

  @Test
  public void readStream() throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] {1, 2, 3});
    AdaptiveStreamBuffer adaptiveBuffer = new AdaptiveStreamBuffer(inputStream, 3);

    Assert.assertEquals(0, adaptiveBuffer.available());
    Assert.assertEquals(2, adaptiveBuffer.fill(2));
    Assert.assertFalse(adaptiveBuffer.isFinished());
    assertArrayEquals(new byte[] {1, 2}, Arrays.copyOf(adaptiveBuffer.get(), 2));
    Assert.assertEquals(3, adaptiveBuffer.fill(4));
    Assert.assertTrue(adaptiveBuffer.isFinished());
    assertArrayEquals(new byte[] {1, 2, 3}, Arrays.copyOf(adaptiveBuffer.get(), 3));
  }

  @Test
  public void bufferResize() throws IOException {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9});
    AdaptiveStreamBuffer adaptiveBuffer = new AdaptiveStreamBuffer(inputStream, 3);

    Assert.assertEquals(0, adaptiveBuffer.available());
    Assert.assertEquals(9, adaptiveBuffer.fill(10));
    Assert.assertTrue(adaptiveBuffer.isFinished());
    assertArrayEquals(
        new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9}, Arrays.copyOf(adaptiveBuffer.get(), 9));
  }

  @Test
  public void bufferReachedEnd() throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
    AdaptiveStreamBuffer adaptiveBuffer = new AdaptiveStreamBuffer(inputStream, 3);

    Assert.assertEquals(0, adaptiveBuffer.available());
    Assert.assertEquals(5, adaptiveBuffer.fill(9));
    Assert.assertTrue(adaptiveBuffer.isFinished());
    assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, Arrays.copyOf(adaptiveBuffer.get(), 5));
  }

  @Test
  public void advanceCachedBytes() throws IOException {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9});
    AdaptiveStreamBuffer adaptiveBuffer = new AdaptiveStreamBuffer(inputStream, 3);

    Assert.assertEquals(0, adaptiveBuffer.available());
    Assert.assertEquals(9, adaptiveBuffer.fill(10));
    Assert.assertTrue(adaptiveBuffer.isFinished());
    assertArrayEquals(
        new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9}, Arrays.copyOf(adaptiveBuffer.get(), 9));
    Assert.assertEquals(5, adaptiveBuffer.advance(5));
    assertArrayEquals(new byte[] {6, 7, 8, 9}, Arrays.copyOf(adaptiveBuffer.get(), 4));
  }

  @Test
  public void advanceStreamBytes() throws IOException {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9});
    AdaptiveStreamBuffer adaptiveBuffer = new AdaptiveStreamBuffer(inputStream, 3);

    Assert.assertEquals(5, adaptiveBuffer.advance(5));
    Assert.assertEquals(4, adaptiveBuffer.fill(9));
    Assert.assertTrue(adaptiveBuffer.isFinished());
    assertArrayEquals(new byte[] {6, 7, 8, 9}, Arrays.copyOf(adaptiveBuffer.get(), 4));
  }
}
