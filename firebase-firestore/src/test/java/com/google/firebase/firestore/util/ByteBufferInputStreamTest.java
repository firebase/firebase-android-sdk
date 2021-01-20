// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ByteBufferInputStreamTest {

  @Test
  public void canReadSingleByte() {
    ByteBuffer source = ByteBuffer.wrap(new byte[] {1, 2, 3});
    ByteBufferInputStream inputStream = new ByteBufferInputStream(source);

    assertEquals(1, inputStream.read());
    assertEquals(2, inputStream.read());
    assertEquals(3, inputStream.read());
  }

  @Test
  public void canReadMultipleBytes() throws IOException {
    ByteBuffer source = ByteBuffer.wrap(new byte[] {1, 2, 3});
    ByteBufferInputStream inputStream = new ByteBufferInputStream(source);

    byte[] destination = new byte[3];
    int bytesRead = inputStream.read(destination);

    assertEquals(3, bytesRead);
    assertArrayEquals(new byte[] {1, 2, 3}, destination);
  }

  @Test
  public void testHandlesEndOfStream() throws IOException {
    ByteBuffer source = ByteBuffer.wrap(new byte[] {1});
    ByteBufferInputStream inputStream = new ByteBufferInputStream(source);

    byte[] destination = new byte[2];
    int bytesRead = inputStream.read(destination);

    assertEquals(1, bytesRead);
    assertArrayEquals(new byte[] {1, 0}, destination);

    assertEquals(-1, inputStream.read());
  }

  @Test
  public void testHandlesNegativeBytes() throws IOException {
    ByteBuffer source = ByteBuffer.wrap(new byte[] {-1});
    ByteBufferInputStream inputStream = new ByteBufferInputStream(source);

    int read = inputStream.read();
    // The integer value for byte -1 is 255, as the last three bits are set for -1.
    assertEquals(0xFF, read);
    assertEquals(-1, (byte) read);
  }
}
