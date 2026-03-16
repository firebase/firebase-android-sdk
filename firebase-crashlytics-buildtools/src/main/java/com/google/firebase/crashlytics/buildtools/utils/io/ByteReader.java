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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

/**
 * Reads bytes from a source, returning them as Java primitive types.
 * Expands on the DataInput interface by adding the ability to read any number of bytes (up to a maximum) into any
 * Java primitive. It also supports both big-endian and little-endian byte orders and random access through
 * <code>SeekableInputStream</code>.
 */
public class ByteReader implements Closeable {

  private static final int LONG_WIDTH = 8;
  private static final int INT_WIDTH = 4;
  private static final int SHORT_WIDTH = 2;
  private static final int BUFFER_SIZE = 64; // 64-bit buffer for numeric representations.

  private final byte[] _bytes;
  private final ByteBuffer _buffer;

  private final SeekableInputStream _source;

  public ByteReader(SeekableInputStream source) {
    _bytes = new byte[BUFFER_SIZE];
    _buffer = ByteBuffer.allocate(_bytes.length);
    _source = source;
  }

  /**
   * Move to another location in the stream.
   * @param offset the offset in the stream to seek.
   * @throws IOException if there is a problem seeking to the given offset.
   */
  public void seek(long offset) throws IOException {
    _source.seek(offset);
  }

  /**
   * Get the current location in the stream.
   * @return the current location in the stream.
   * @throws IOException if there is a problem determining the current stream location.
   */
  public long getCurrentOffset() throws IOException {
    return _source.getCurrentOffset();
  }

  /**
   * Close this ByteReader along with its underlying stream.
   */
  @Override
  public void close() throws IOException {
    _source.close();
  }

  /**
   * Read a byte from the stream.
   * @return the next <code>byte</code> from the stream.
   * @throws IOException if there is a problem reading from the stream, or the end of the stream is reached.
   */
  public byte readByte() throws IOException {
    int b = _source.read();
    if (b < 0) {
      throw new EOFException();
    }
    return (byte) (b & 0xff);
  }

  /**
   * Read the given number of bytes from the stream into a new byte array.
   * @param numBytes the number of bytes to read.
   * @return a new byte array containing the read bytes.
   * @throws IOException if there is a problem reading from the stream, or the end of the stream is reached.
   */
  public byte[] readBytes(int numBytes) throws IOException {
    byte[] bytes = new byte[numBytes];
    _source.readFully(bytes, 0, bytes.length);
    return bytes;
  }

  /**
   * Read the next <code>numBytes</code> bytes from the stream and return the value as a <code>short</code>.
   * Will read a maximum of 2 bytes (the number of bytes in a <code>short</code>).
   * Uses the byte order set in {@link ByteReader#setByteOrder}.
   *
   * @param numBytes the number of bytes to read from the stream.
   * @return the value of the given set of bytes as a signed <code>short</code>.
   * @throws IOException if a problem occurs reading <code>numBytes</code> from the stream.
   * @throws IllegalArgumentException if more than 2 bytes are requested.
   */
  public short readShort(int numBytes) throws IOException {
    _buffer.put(readNumber(_bytes, numBytes, SHORT_WIDTH, _buffer.order()));

    _buffer.flip();
    short answer = _buffer.getShort();

    _buffer.clear();
    return answer;
  }

  /**
   * Read the next <code>numBytes</code> bytes from the stream and return the value as an <code>int</code>.
   * Will read a maximum of 4 bytes (the number of bytes in an <code>int</code>).
   * Uses the byte order set in {@link ByteReader#setByteOrder}.
   *
   * @param numBytes the number of bytes to read from the stream.
   * @return the value of the given set of bytes as a signed <code>int</code>.
   * @throws IOException if a problem occurs reading <code>numBytes</code> from the stream.
   * @throws IllegalArgumentException if more than 4 bytes are requested.
   */
  public int readInt(int numBytes) throws IOException {
    _buffer.put(readNumber(_bytes, numBytes, INT_WIDTH, _buffer.order()));

    _buffer.flip();
    int answer = _buffer.getInt();

    _buffer.clear();
    return answer;
  }

  /**
   * Read the next <code>numBytes</code> bytes from the stream and return the value as a <code>long</code>.
   * Will read a maximum of 8 bytes (the number of bytes in a <code>long</code>).
   * Uses the byte order set in {@link ByteReader#setByteOrder}.
   *
   * @param numBytes the number of bytes to read from the stream.
   * @return the value of the given set of bytes as a signed <code>long</code>.
   * @throws IOException if a problem occurs reading <code>numBytes</code> from the stream.
   * @throws IllegalArgumentException if more than 8 bytes are requested.
   */
  public long readLong(int numBytes) throws IOException {
    _buffer.put(readNumber(_bytes, numBytes, LONG_WIDTH, _buffer.order()));

    _buffer.flip();
    long answer = _buffer.getLong();

    _buffer.clear();
    return answer;
  }

  /**
   * Read the next set of bytes from the stream as a null-terminated string.
   * @param charset the character set to use for interpreting the bytes.
   * @return a String containing the interpreted value of the next set of null-terminated bytes.
   * @throws IOException if there is a problem reading from the stream,
   *                     or if the end of the stream is reached before a null-terminator.
   */
  public String readNullTerminatedString(Charset charset) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    for (int b = _source.read(); b != 0; b = _source.read()) {
      if (b < 0) {
        throw new EOFException();
      }
      bos.write(b);
    }

    return new String(bos.toByteArray(), charset);
  }

  /**
   * Read the next set of bytes from the stream as an unsiged LEB128 value.
   * @return an int containing the decoded unsigned LEB128 value of the next set of bytes.
   * @throws IOException if there is a problem reading from the stream.
   */
  public int readULEB128() throws IOException {
    int value = 0;
    int shift = 0;
    while (true) {
      byte b = readByte();
      value |= ((b & 0x7f) << shift);
      if ((b & 0x80) == 0x00) {
        break;
      }
      shift += 7;
    }
    return value;
  }

  /**
   * Read the next set of bytes from the stream as a signed LEB128 value.
   * @return an int containing the decoded signed LEB128 value of the next set of bytes.
   * @throws IOException if there is a problem reading from the stream.
   */
  public int readSLEB128() throws IOException {
    int value = 0;
    int shift = 0;
    int size = 0;
    byte b;
    do {
      b = readByte();
      size++;
      value |= ((b & 0x7f) << shift);
      shift += 7;
    } while ((b & 0x80) != 0x00);

    if ((shift < (size * 8)) && ((b & 0x40) != 0x00)) {
      // Sign extend
      value |= -(1 << shift);
    }

    return value;
  }

  /**
   * Set the byte order for interpreting numeric values from underlying bytes in the stream.
   * @param order the byte order by which to interpret numeric values returned by <code>read</code> methods.
   */
  public void setByteOrder(ByteOrder order) {
    _buffer.order(order);
  }

  /**
   * Get the byte order this ByteReader is using to interpret numeric values from underlying bytes in the stream.
   * @return the ByteOrder currently being used by this ByteReader.
   */
  public ByteOrder getByteOrder() {
    return _buffer.order();
  }

  private byte[] readNumber(byte[] buffer, int numBytes, int width, ByteOrder order)
      throws IOException {
    if (numBytes > width) {
      throw new IllegalArgumentException(
          String.format(
              "Requested number of bytes (%d) was greater than available bytes (%d).",
              numBytes, width));
    }
    _source.readFully(buffer, 0, numBytes);
    return padBytes(buffer, numBytes, width, order);
  }

  private byte[] padBytes(byte[] data, int dataLen, int totalWidth, ByteOrder order) {
    byte[] padded = new byte[totalWidth];
    int dest = (order == ByteOrder.BIG_ENDIAN) ? totalWidth - dataLen : 0;
    System.arraycopy(data, 0, padded, dest, dataLen);
    return padded;
  }
}
