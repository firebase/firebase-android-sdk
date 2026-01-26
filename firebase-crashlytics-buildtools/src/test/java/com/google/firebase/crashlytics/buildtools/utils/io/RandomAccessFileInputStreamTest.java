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

package com.google.firebase.crashlytics.buildtools.utils.io;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class RandomAccessFileInputStreamTest {

  private File _testFile;
  private long _fileSize;
  private SeekableInputStream _streamUnderTest;

  @Before
  public void setUp() throws Exception {
    // random_access_test_file is a 50KB binary file made up of bytes whose values are their offset
    // % 256
    _testFile =
        new File(
            this.getClass().getClassLoader().getResource("random_access_test_file.bin").toURI());
    _fileSize = _testFile.length();
    _streamUnderTest = new RandomAccessFileInputStream(_testFile);
  }

  private int getExpectedIntValueForOffset(long offset) {
    return (int) (offset % 256);
  }

  private byte getExpectedByteValueForOffset(long offset) {
    return (byte) (getExpectedIntValueForOffset(offset) & 0xff);
  }

  @Test
  public void testRead() throws Exception {
    Assert.assertEquals(0, _streamUnderTest.read());
    Assert.assertEquals(1, _streamUnderTest.getCurrentOffset());
    Assert.assertEquals(1, _streamUnderTest.read());
    Assert.assertEquals(2, _streamUnderTest.getCurrentOffset());
  }

  @Test
  public void testRead_returnsNegativeOneOnReadPastEndOfFile() throws Exception {
    _streamUnderTest.seek(_fileSize - 1);
    Assert.assertNotEquals(
        -1, _streamUnderTest.read()); // Read last byte of file, should not be -1;
    Assert.assertEquals(-1, _streamUnderTest.read()); // Read past end of file.
    _streamUnderTest.seek(_fileSize * 2);
    Assert.assertEquals(-1, _streamUnderTest.read()); // Read far outside the file.
  }

  @Test
  public void testSeek() throws Exception {
    // Seek into the file and check the value.
    long offset = 2456;
    _streamUnderTest.seek(offset);
    Assert.assertEquals(offset, _streamUnderTest.getCurrentOffset());
    Assert.assertEquals(getExpectedIntValueForOffset(offset), _streamUnderTest.read());
    Assert.assertEquals(offset + 1, _streamUnderTest.getCurrentOffset());

    offset = 3000; // Seek forward a small amount
    _streamUnderTest.seek(offset);
    Assert.assertEquals(offset, _streamUnderTest.getCurrentOffset());
    Assert.assertEquals(getExpectedIntValueForOffset(offset), _streamUnderTest.read());
    Assert.assertEquals(offset + 1, _streamUnderTest.getCurrentOffset());

    offset = 1337; // Seek backward
    _streamUnderTest.seek(offset);
    Assert.assertEquals(offset, _streamUnderTest.getCurrentOffset());
    Assert.assertEquals(getExpectedIntValueForOffset(offset), _streamUnderTest.read());
    Assert.assertEquals(offset + 1, _streamUnderTest.getCurrentOffset());

    offset = 39485; // Seek far forward
    _streamUnderTest.seek(offset);
    Assert.assertEquals(offset, _streamUnderTest.getCurrentOffset());
    Assert.assertEquals(getExpectedIntValueForOffset(offset), _streamUnderTest.read());
    Assert.assertEquals(offset + 1, _streamUnderTest.getCurrentOffset());

    offset = 8; // Seek far backward
    _streamUnderTest.seek(offset);
    Assert.assertEquals(offset, _streamUnderTest.getCurrentOffset());
    Assert.assertEquals(getExpectedIntValueForOffset(offset), _streamUnderTest.read());
    Assert.assertEquals(offset + 1, _streamUnderTest.getCurrentOffset());
  }

  @Test(expected = IOException.class)
  public void testSeek_throwsIOExceptionWhenNegativeValue() throws Exception {
    _streamUnderTest.seek(-8);
  }

  @Test
  public void testReadArray() throws Exception {
    byte[] expected = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

    byte[] buffer = new byte[10];
    Assert.assertEquals(expected.length, _streamUnderTest.read(buffer));

    Assert.assertArrayEquals(expected, buffer);
    Assert.assertEquals(expected.length, _streamUnderTest.getCurrentOffset());
  }

  @Test
  public void testReadArray_returnsRemainingNearEndOfFile() throws IOException {
    byte[] buffer = new byte[10];
    int expectedBytes = buffer.length >> 1;
    _streamUnderTest.seek(_fileSize - expectedBytes);
    Assert.assertEquals(expectedBytes, _streamUnderTest.read(buffer));
  }

  @Test
  public void testReadArray_returnsNegativeOneOnReadPastEndOfFile() throws Exception {
    byte[] buffer = new byte[10];
    _streamUnderTest.seek(_fileSize - buffer.length);
    Assert.assertNotEquals(
        -1, _streamUnderTest.read(buffer)); // Read through last byte of file, should not be -1;
    Assert.assertEquals(-1, _streamUnderTest.read(buffer)); // Read past end of file.
    _streamUnderTest.seek(_fileSize * 2);
    Assert.assertEquals(-1, _streamUnderTest.read(buffer)); // Read far outside the file.
  }

  @Test
  public void testReadsAndOffsetsAfterNonBufferedRead() throws Exception {
    byte[] bigBuffer = new byte[12000]; // Larger than 8KB buffer size, will not buffer.
    long expectedOffsetAfterRead = bigBuffer.length + _streamUnderTest.getCurrentOffset();
    _streamUnderTest.read(bigBuffer);
    Assert.assertEquals(expectedOffsetAfterRead, _streamUnderTest.getCurrentOffset());
    byte[] smallBuffer = new byte[3];
    Assert.assertEquals(smallBuffer.length, _streamUnderTest.read(smallBuffer));
    Assert.assertEquals(
        expectedOffsetAfterRead + smallBuffer.length, _streamUnderTest.getCurrentOffset());
  }

  @Test
  public void testReadsAndOffsetsAtEndOfFileAfterNonBufferedRead() throws Exception {
    byte[] bigBuffer = new byte[12000];
    _streamUnderTest.seek(_fileSize - bigBuffer.length - 400);
    long expectedOffsetAfterRead = bigBuffer.length + _streamUnderTest.getCurrentOffset();
    Assert.assertEquals(bigBuffer.length, _streamUnderTest.read(bigBuffer));
    Assert.assertEquals(expectedOffsetAfterRead, _streamUnderTest.getCurrentOffset());
    _streamUnderTest.read(); // Initialize buffering and move one byte ahead.
    Assert.assertEquals(399, _streamUnderTest.read(bigBuffer));
    Assert.assertEquals(_fileSize, _streamUnderTest.getCurrentOffset());
  }

  @Test
  public void testReadFully() throws Exception {
    byte[] buffer = new byte[412];
    int readOffset = 8;
    _streamUnderTest.seek(readOffset);
    int endIndex = buffer.length - 1;
    _streamUnderTest.readFully(buffer, 0, buffer.length);

    Assert.assertEquals(buffer[0], getExpectedByteValueForOffset(readOffset));
    Assert.assertEquals(buffer[endIndex], getExpectedByteValueForOffset(endIndex + readOffset));
  }

  @Test(expected = EOFException.class)
  public void testReadFully_throwsEofExceptionWhenReadPastEndOfFile() throws Exception {
    byte[] buffer = new byte[10];
    _streamUnderTest.seek(_fileSize - (buffer.length >> 1));
    _streamUnderTest.readFully(buffer, 0, buffer.length);
  }

  @Test
  public void testSkip() throws Exception {
    Assert.assertEquals(10, _streamUnderTest.skip(10));
    Assert.assertEquals(_streamUnderTest.getCurrentOffset(), 10);
    Assert.assertEquals(
        _streamUnderTest.read(),
        getExpectedIntValueForOffset(10)); // Advances the index by 1 after read.

    Assert.assertEquals(10, _streamUnderTest.skip(10));
    Assert.assertEquals(_streamUnderTest.getCurrentOffset(), 21);
    Assert.assertEquals(_streamUnderTest.read(), getExpectedIntValueForOffset(21));

    Assert.assertEquals(
        9000, _streamUnderTest.skip(9000)); // Bigger than 8KB buffer, causes buffer flush.
    Assert.assertEquals(_streamUnderTest.getCurrentOffset(), 9022);
    Assert.assertEquals(_streamUnderTest.read(), getExpectedIntValueForOffset(9022));
  }

  @Test
  public void testSkip_willNotGoPastEndOfFile() throws Exception {
    _streamUnderTest.seek(_fileSize - 2);
    Assert.assertEquals(2, _streamUnderTest.skip(2));
    Assert.assertEquals(_fileSize, _streamUnderTest.getCurrentOffset());
    Assert.assertEquals(0, _streamUnderTest.skip(2));
    Assert.assertEquals(_fileSize, _streamUnderTest.getCurrentOffset());
  }

  @Test(expected = IOException.class)
  public void testClose_throwsIOExceptionOnRead() throws Exception {
    _streamUnderTest.close();
    _streamUnderTest.read();
  }

  @Test(expected = IOException.class)
  public void testClose_throwsIOExceptionOnReadArray() throws Exception {
    _streamUnderTest.close();
    _streamUnderTest.read(new byte[8]);
  }

  @Test(expected = IOException.class)
  public void testClose_throwsIOExceptionOnReadArrayWithOffset() throws Exception {
    _streamUnderTest.close();
    _streamUnderTest.read(new byte[8], 2, 4);
  }

  @Test(expected = IOException.class)
  public void testClose_throwsIOExceptionOnReadFully() throws Exception {
    _streamUnderTest.close();
    _streamUnderTest.readFully(new byte[8], 2, 4);
  }

  @Test(expected = IOException.class)
  public void testClose_throwsIOExceptionOnSeek() throws Exception {
    _streamUnderTest.close();
    _streamUnderTest.seek(8);
  }

  @Test(expected = IOException.class)
  public void testClose_throwsIOExceptionOnSkip() throws Exception {
    _streamUnderTest.close();
    _streamUnderTest.skip(8);
  }

  @Test(expected = IOException.class)
  public void testClose_throwsIOExceptionOnGetCurrentOffset() throws Exception {
    _streamUnderTest.close();
    _streamUnderTest.getCurrentOffset();
  }
}
