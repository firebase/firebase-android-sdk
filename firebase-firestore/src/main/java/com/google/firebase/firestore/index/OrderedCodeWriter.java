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

package com.google.firebase.firestore.index;

import static java.lang.Character.MAX_SURROGATE;
import static java.lang.Character.MIN_SURROGATE;

import com.google.protobuf.ByteString;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * OrderedCodeWriter is a minimal-allocation implementation of the writing behavior defined by the
 * backend.
 */
public class OrderedCodeWriter {
  // Note: This code is copied from the backend. Code that is not used by Firestore was removed.

  public static final byte ESCAPE1 = 0x00;
  public static final byte NULL_BYTE = (byte) 0xff; // Combined with ESCAPE1
  public static final byte SEPARATOR = 0x01; // Combined with ESCAPE1

  public static final byte ESCAPE2 = (byte) 0xff;
  public static final byte INFINITY = (byte) 0xff; // Combined with ESCAPE2
  public static final byte FF_BYTE = 0x00; // Combined with ESCAPE2

  /** These constants are taken from the backend. */
  public static final long DOUBLE_SIGN_MASK = 0x8000000000000000L;

  public static final long DOUBLE_ALL_BITS = 0xFFFFFFFFFFFFFFFFL;
  /**
   * The default size of the buffer. This is arbitrary, but likely larger than most index values so
   * that less copies of the underlying buffer will be made. For large values, a single copy will
   * made to double the buffer length.
   */
  private static final int DEFAULT_BUFFER_SIZE = 1024;

  /**
   * This array maps encoding length to header bits in the first two bytes for SignedNumAscending
   * encoding.
   */
  private static final byte[][] LENGTH_TO_HEADER_BITS = {
    {0, 0},
    {(byte) 0x80, 0},
    {(byte) 0xc0, 0},
    {(byte) 0xe0, 0},
    {(byte) 0xf0, 0},
    {(byte) 0xf8, 0},
    {(byte) 0xfc, 0},
    {(byte) 0xfe, 0},
    {(byte) 0xff, 0},
    {(byte) 0xff, (byte) 0x80},
    {(byte) 0xff, (byte) 0xc0}
  };

  private byte[] buffer;
  private int position = 0;

  /** Creates a new writer with a default initial buffer size. */
  public OrderedCodeWriter() {
    buffer = new byte[DEFAULT_BUFFER_SIZE];
  }

  public void writeBytesAscending(ByteString value) {
    for (int i = 0; i < value.size(); ++i) {
      writeByteAscending(value.byteAt(i));
    }
    writeSeparatorAscending();
  }

  public void writeBytesDescending(ByteString value) {
    for (int i = 0; i < value.size(); ++i) {
      writeByteDescending(value.byteAt(i));
    }
    writeSeparatorDescending();
  }

  /**
   * Writes utf8 bytes into this byte sequence, ascending.
   *
   * <p>This is a more efficient version of writeBytesAscending(str.getBytes(UTF_8));
   */
  public void writeUtf8Ascending(CharSequence sequence) {
    int utf16Length = sequence.length();
    for (int i = 0; i < utf16Length; i++) {
      char c = sequence.charAt(i);
      if (c < 0x80) {
        writeByteAscending((byte) c);
      } else if (c < 0x800) {
        writeByteAscending((byte) ((0xF << 6) | (c >>> 6)));
        writeByteAscending((byte) (0x80 | (0x3F & c)));
      } else if ((c < MIN_SURROGATE || MAX_SURROGATE < c)) {
        writeByteAscending((byte) ((0xF << 5) | (c >>> 12)));
        writeByteAscending((byte) (0x80 | (0x3F & (c >>> 6))));
        writeByteAscending((byte) (0x80 | (0x3F & c)));
      } else {
        final int codePoint = Character.codePointAt(sequence, i);
        ++i; // Skip the low surrogate.
        writeByteAscending((byte) ((0xF << 4) | (codePoint >>> 18)));
        writeByteAscending((byte) (0x80 | (0x3F & (codePoint >>> 12))));
        writeByteAscending((byte) (0x80 | (0x3F & (codePoint >>> 6))));
        writeByteAscending((byte) (0x80 | (0x3F & codePoint)));
      }
    }
    writeSeparatorAscending();
  }

  /**
   * Writes utf8 bytes into this byte sequence, descending.
   *
   * <p>This is a more efficient version of writeBytesDescending(str.getBytes(UTF_8));
   */
  public void writeUtf8Descending(CharSequence sequence) {
    int utf16Length = sequence.length();
    for (int i = 0; i < utf16Length; i++) {
      char c = sequence.charAt(i);
      if (c < 0x80) {
        writeByteDescending((byte) c);
      } else if (c < 0x800) {
        writeByteDescending((byte) ((0xF << 6) | (c >>> 6)));
        writeByteDescending((byte) (0x80 | (0x3F & c)));
      } else if ((c < MIN_SURROGATE || MAX_SURROGATE < c)) {
        writeByteDescending((byte) ((0xF << 5) | (c >>> 12)));
        writeByteDescending((byte) (0x80 | (0x3F & (c >>> 6))));
        writeByteDescending((byte) (0x80 | (0x3F & c)));
      } else {
        final int codePoint = Character.codePointAt(sequence, i);
        ++i; // Skip the low surrogate.
        writeByteDescending((byte) ((0xF << 4) | (codePoint >>> 18)));
        writeByteDescending((byte) (0x80 | (0x3F & (codePoint >>> 12))));
        writeByteDescending((byte) (0x80 | (0x3F & (codePoint >>> 6))));
        writeByteDescending((byte) (0x80 | (0x3F & codePoint)));
      }
    }
    writeSeparatorDescending();
  }

  /** Writes an unsigned long in the ordered code format, ascending. */
  public void writeUnsignedLongAscending(long value) {
    // Values are encoded with a single byte length prefix, followed
    // by the actual value in big-endian format with leading 0 bytes
    // dropped.
    int len = unsignedNumLength(value);
    ensureAvailable(1 + len);
    buffer[position++] = (byte) len; // Write the length
    for (int i = position + len - 1; i >= position; --i) {
      buffer[i] = (byte) (value & 0xff);
      value >>>= 8;
    }
    position += len;
  }

  /** Writes an unsigned long in the ordered code format, descending. */
  public void writeUnsignedLongDescending(long value) {
    // Values are encoded with a complemented single byte length prefix,
    // followed by the complement of the actual value in big-endian format with
    // leading 0xff bytes dropped.
    int len = unsignedNumLength(value);
    ensureAvailable(1 + len);
    buffer[position++] = (byte) ~len; // Write the length
    for (int i = position + len - 1; i >= position; --i) {
      buffer[i] = (byte) ~(value & 0xff);
      value >>>= 8;
    }
    position += len;
  }

  /** Writes a signed long in the ordered code format, ascending. */
  public void writeSignedLongAscending(long value) {
    long val = value < 0 ? ~value : value;
    if (val < 64) { // Fast path for encoding length == 1
      ensureAvailable(1);
      buffer[position++] = ((byte) (LENGTH_TO_HEADER_BITS[1][0] ^ value));
      return;
    }
    int len = signedNumLength(val);
    ensureAvailable(len);
    // We should handle all encoding length == 1 above.
    if (len < 2) {
      throw new AssertionError(
          String.format("Invalid length (%d) returned by signedNumLength", len));
    }
    byte signByte = value < 0 ? (byte) 0xff : 0;
    int startIndex = position;
    // Sign extend longs that are encoded as 9 or 10 bytes.
    if (len == 10) {
      startIndex += 2;
      buffer[position] = signByte;
      buffer[position + 1] = signByte;
    } else if (len == 9) {
      startIndex += 1;
      buffer[position] = signByte;
    }
    // Encode the long big-endian
    long x = value;
    for (int i = len - 1 + position; i >= startIndex; --i) {
      buffer[i] = (byte) (x & 0xffL);
      x >>= 8;
    }
    // Encode the length in header bits.
    buffer[position] ^= LENGTH_TO_HEADER_BITS[len][0];
    buffer[position + 1] ^= LENGTH_TO_HEADER_BITS[len][1];
    position += len;
  }

  /** Writes a signed long in the ordered code format, descending. */
  public void writeSignedLongDescending(long value) {
    writeSignedLongAscending(~value);
  }

  public void writeDoubleAscending(double val) {
    // This particular encoding has the following properties:
    // The order matches the IEEE 754 floating-point comparison results with the
    // following exceptions:
    //   -0.0 < 0.0
    //   all non-NaN < NaN
    //   NaN = NaN
    long v = Double.doubleToLongBits(val);
    v ^= (v < 0) ? DOUBLE_ALL_BITS : DOUBLE_SIGN_MASK;
    writeUnsignedLongAscending(v);
  }

  public void writeDoubleDescending(double val) {
    // See note in #writeDoubleAscending
    long v = Double.doubleToLongBits(val);
    v ^= (v < 0) ? DOUBLE_ALL_BITS : DOUBLE_SIGN_MASK;
    writeUnsignedLongDescending(v);
  }

  /**
   * Writes the "infinity" byte sequence that sorts after all other byte sequences written in
   * ascending order.
   */
  public void writeInfinityAscending() {
    writeEscapedByteAscending(ESCAPE2);
    writeEscapedByteAscending(INFINITY);
  }

  /**
   * Writes the "infinity" byte sequence that sorts before all other byte sequences written in
   * descending order.
   */
  public void writeInfinityDescending() {
    writeEscapedByteDescending(ESCAPE2);
    writeEscapedByteDescending(INFINITY);
  }

  /** Resets the buffer such that it is the same as when it was newly constructed. */
  public void reset() {
    position = 0;
  }

  /** Makes a copy of the encoded bytes in this buffer. */
  public byte[] encodedBytes() {
    return Arrays.copyOf(buffer, position);
  }

  /**
   * Writes a single byte ascending to the buffer, doing proper escaping as described in {@link
   * OrderedCodeConstants}.
   */
  private void writeByteAscending(byte b) {
    if (b == ESCAPE1) {
      writeEscapedByteAscending(ESCAPE1);
      writeEscapedByteAscending(NULL_BYTE);
    } else if (b == ESCAPE2) {
      writeEscapedByteAscending(ESCAPE2);
      writeEscapedByteAscending(FF_BYTE);
    } else {
      writeEscapedByteAscending(b);
    }
  }

  /**
   * Writes a single byte descending to the buffer, doing proper escaping as described in {@link
   * OrderedCodeConstants}.
   */
  private void writeByteDescending(byte b) {
    if (b == ESCAPE1) {
      writeEscapedByteDescending(ESCAPE1);
      writeEscapedByteDescending(NULL_BYTE);
    } else if (b == ESCAPE2) {
      writeEscapedByteDescending(ESCAPE2);
      writeEscapedByteDescending(FF_BYTE);
    } else {
      writeEscapedByteDescending(b);
    }
  }

  private void writeSeparatorAscending() {
    writeEscapedByteAscending(ESCAPE1);
    writeEscapedByteAscending(SEPARATOR);
  }

  private void writeSeparatorDescending() {
    writeEscapedByteDescending(ESCAPE1);
    writeEscapedByteDescending(SEPARATOR);
  }

  private void writeEscapedByteAscending(byte b) {
    ensureAvailable(1);
    buffer[position++] = b;
  }

  private void writeEscapedByteDescending(byte b) {
    ensureAvailable(1);
    buffer[position++] = (byte) ~b;
  }

  private void ensureAvailable(int bytes) {
    int minCapacity = bytes + position;
    if (minCapacity <= buffer.length) {
      return;
    }
    // Try doubling.
    int newLength = buffer.length * 2;
    // Still not big enough? Just allocate the right size.
    if (newLength < minCapacity) {
      newLength = minCapacity;
    }
    // Create the new buffer.
    buffer = Arrays.copyOf(buffer, newLength);
  }

  private int signedNumLength(long n) {
    // Handle negative numbers.
    if (n < 0) {
      n = ~n;
    }
    // Compute the number of bits.
    int numBits = Long.SIZE - Long.numberOfLeadingZeros(n);
    // Add a bit for sign.
    ++numBits;
    // Compute the number of encoded bytes.
    int bitsPerEncodedByte = 7;
    return IntMath.divide(numBits, bitsPerEncodedByte, RoundingMode.UP);
  }

  private int unsignedNumLength(long value) {
    // This is just the number of bytes for the unsigned representation of the number.
    int numBits = Long.SIZE - Long.numberOfLeadingZeros(value);
    return IntMath.divide(numBits, Byte.SIZE, RoundingMode.UP);
  }

  public void seed(byte[] encodedBytes) {
    ensureAvailable(encodedBytes.length);
    for (byte b : encodedBytes) buffer[position++] = b;
  }
}
