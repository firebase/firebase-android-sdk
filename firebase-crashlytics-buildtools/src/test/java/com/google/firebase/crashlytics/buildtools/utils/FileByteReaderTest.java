/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.utils;

import com.google.firebase.crashlytics.buildtools.TestUtils;
import com.google.firebase.crashlytics.buildtools.utils.io.ByteReader;
import com.google.firebase.crashlytics.buildtools.utils.io.RandomAccessFileInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class FileByteReaderTest {

  private static final File WORKING_DIR = new File(TestUtils.TEST_OUTPUT_DIR, "ElfReaderTests");

  private static final String BIG_ENDIAN_PATH = "bigendian";
  private static final String LITTLE_ENDIAN_PATH = "littleendian";

  private static final byte EXPECTED_BYTE = 99;
  private static final short EXPECTED_SHORT = 32104;
  private static final int EXPECTED_INT = 2010405020;
  private static final long EXPECTED_LONG = 8675309867530986753L;
  private static final String EXPECTED_STRING = "I am a String!";

  private byte[] _expectedStringBytes;

  @Before
  public void setUp() throws IOException {
    TestUtils.prepareTestDirectory();
    FileUtils.verifyDirectory(WORKING_DIR);

    byte[] beBytes =
        ByteBuffer.allocate(15)
            .order(ByteOrder.BIG_ENDIAN)
            .put(EXPECTED_BYTE)
            .putShort(EXPECTED_SHORT)
            .putInt(EXPECTED_INT)
            .putLong(EXPECTED_LONG)
            .array();

    byte[] leBytes =
        ByteBuffer.allocate(15)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(EXPECTED_BYTE)
            .putShort(EXPECTED_SHORT)
            .putInt(EXPECTED_INT)
            .putLong(EXPECTED_LONG)
            .array();

    _expectedStringBytes = EXPECTED_STRING.getBytes(Charsets.UTF_8);

    // Write little-endian file.
    byte[] allBytes =
        Arrays.copyOf(leBytes, leBytes.length + _expectedStringBytes.length + 1); // Null byte
    System.arraycopy(
        _expectedStringBytes, 0, allBytes, leBytes.length, _expectedStringBytes.length);
    writeFile(LITTLE_ENDIAN_PATH, allBytes);

    // Write big-endian file.
    allBytes =
        Arrays.copyOf(beBytes, beBytes.length + _expectedStringBytes.length + 1); // Null byte
    System.arraycopy(
        _expectedStringBytes, 0, allBytes, beBytes.length, _expectedStringBytes.length);
    writeFile(BIG_ENDIAN_PATH, allBytes);
  }

  @After
  public void tearDown() {
    for (File f : WORKING_DIR.listFiles()) {
      f.delete();
    }
    WORKING_DIR.delete();
  }

  private void writeFile(String fileName, byte[] bytes) throws IOException {
    File file = new File(WORKING_DIR, fileName);
    FileOutputStream fileOs = null;
    try {
      fileOs = new FileOutputStream(file);
      fileOs.write(bytes);
    } finally {
      IOUtils.closeQuietly(fileOs);
    }
  }

  @Test
  public void testReadBigEndian() throws IOException {
    ByteReader reader = null;
    try {
      reader =
          new ByteReader(new RandomAccessFileInputStream(new File(WORKING_DIR, BIG_ENDIAN_PATH)));
      reader.setByteOrder(ByteOrder.BIG_ENDIAN);

      Assert.assertEquals(EXPECTED_BYTE, reader.readByte());
      Assert.assertEquals(EXPECTED_SHORT, reader.readShort(2));
      Assert.assertEquals(EXPECTED_INT, reader.readInt(4));
      Assert.assertEquals(EXPECTED_LONG, reader.readLong(8));
      Assert.assertEquals(EXPECTED_STRING, reader.readNullTerminatedString(Charsets.UTF_8));
    } finally {
      IOUtils.closeQuietly(reader);
    }
  }

  @Test
  public void testReadLittleEndian() throws IOException {
    ByteReader reader = null;
    try {
      reader =
          new ByteReader(
              new RandomAccessFileInputStream(new File(WORKING_DIR, LITTLE_ENDIAN_PATH)));
      reader.setByteOrder(ByteOrder.LITTLE_ENDIAN);

      Assert.assertEquals(EXPECTED_BYTE, reader.readByte());
      Assert.assertEquals(EXPECTED_SHORT, reader.readShort(2));
      Assert.assertEquals(EXPECTED_INT, reader.readInt(4));
      Assert.assertEquals(EXPECTED_LONG, reader.readLong(8));
      Assert.assertEquals(EXPECTED_STRING, reader.readNullTerminatedString(Charsets.UTF_8));
    } finally {
      IOUtils.closeQuietly(reader);
    }
  }

  @Test
  public void testSeekAndOffset() throws IOException {
    ByteReader reader = null;
    try {
      reader =
          new ByteReader(
              new RandomAccessFileInputStream(new File(WORKING_DIR, LITTLE_ENDIAN_PATH)));
      reader.setByteOrder(ByteOrder.LITTLE_ENDIAN);

      Assert.assertEquals(0, reader.getCurrentOffset());
      reader.seek(3);
      Assert.assertEquals(3, reader.getCurrentOffset());
      Assert.assertEquals(EXPECTED_INT, reader.readInt(4));
    } finally {
      IOUtils.closeQuietly(reader);
    }
  }

  @Test
  public void testReadPartialBigEndian() throws IOException {
    ByteReader reader = null;
    try {
      reader =
          new ByteReader(new RandomAccessFileInputStream(new File(WORKING_DIR, BIG_ENDIAN_PATH)));
      reader.setByteOrder(ByteOrder.BIG_ENDIAN);

      Assert.assertEquals(EXPECTED_BYTE, reader.readShort(1)); // Read only 1 byte in as a short.

      Assert.assertEquals(EXPECTED_SHORT, reader.readInt(2)); // Read only 2 bytes in as an int.

      Assert.assertEquals(EXPECTED_INT, reader.readLong(4)); // Read only 4 bytes in as a long.
    } finally {
      IOUtils.closeQuietly(reader);
    }
  }

  @Test
  public void testReadPartialLittleEndian() throws IOException {
    ByteReader reader = null;
    try {
      reader =
          new ByteReader(
              new RandomAccessFileInputStream(new File(WORKING_DIR, LITTLE_ENDIAN_PATH)));
      reader.setByteOrder(ByteOrder.LITTLE_ENDIAN);

      Assert.assertEquals(EXPECTED_BYTE, reader.readShort(1)); // Read only 1 byte in as a short.

      Assert.assertEquals(EXPECTED_SHORT, reader.readInt(2)); // Read only 2 bytes in as an int.

      Assert.assertEquals(EXPECTED_INT, reader.readLong(4)); // Read only 4 bytes in as a long.
    } finally {
      IOUtils.closeQuietly(reader);
    }
  }

  @Test
  public void testReadInvalidNumberOfBytes() throws IOException {
    ByteReader reader = null;
    try {
      reader =
          new ByteReader(
              new RandomAccessFileInputStream(new File(WORKING_DIR, LITTLE_ENDIAN_PATH)));
      reader.setByteOrder(ByteOrder.LITTLE_ENDIAN);

      try {
        reader.readShort(3);
        Assert.fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException iae) {
        Assert.assertNotNull(iae.getMessage());
      }

      try {
        reader.readInt(5);
        Assert.fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException iae) {
        Assert.assertNotNull(iae.getMessage());
      }

      try {
        reader.readLong(9);
        Assert.fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException iae) {
        Assert.assertNotNull(iae.getMessage());
      }

    } finally {
      IOUtils.closeQuietly(reader);
    }
  }
}
