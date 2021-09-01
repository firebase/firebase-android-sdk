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

import static com.google.firebase.firestore.index.OrderedCodeWriter.ESCAPE1;
import static com.google.firebase.firestore.index.OrderedCodeWriter.ESCAPE2;
import static com.google.firebase.firestore.index.OrderedCodeWriter.FF_BYTE;
import static com.google.firebase.firestore.index.OrderedCodeWriter.NULL_BYTE;
import static com.google.firebase.firestore.index.OrderedCodeWriter.SEPARATOR;
import static java.lang.Character.MAX_SURROGATE;
import static java.lang.Character.MIN_HIGH_SURROGATE;
import static java.lang.Character.MIN_LOW_SURROGATE;
import static java.lang.Character.MIN_SUPPLEMENTARY_CODE_POINT;
import static java.lang.Character.MIN_SURROGATE;

/**
 * OrderedCodeReader is a minimal-allocation implementation of the reading behavior defined by our
 * backend.
 *
 * <p>This class will throw {@link IllegalArgumentException} on invalid input.
 */
public class OrderedCodeReader {
  // Note: This code is copied from the backend. Code that is not used by Firestore was removed.

  /**
   * This array maps encoding lengths to the header bits that overlap with the payload and need
   * fixing during ReadSignedLongIncreasing.
   */
  private static final long[] LENGTH_TO_MASK = {
    0L,
    0x80L,
    0xc000L,
    0xe00000L,
    0xf0000000L,
    0xf800000000L,
    0xfc0000000000L,
    0xfe000000000000L,
    0xff00000000000000L,
    0x8000000000000000L,
    0L
  };

  // Buffer state
  private final byte[] buffer;
  private int position;

  private final StringBuilder stringBuilder = new StringBuilder();

  /** Constructs an reader for bytes encoded in ordered code format. */
  public OrderedCodeReader(byte[] buffer) {
    this.buffer = buffer;
  }

  /** Reads an UTF-8 encoded string that was written in ascending form. */
  public String readUtf8Ascending() {
    stringBuilder.setLength(0);
    int numBytes = findSeparatorAscending();
    for (int i = 0; i < numBytes; ) {
      byte b = readAscendingByte();
      if (Utf8Bytes.isOneByte(b)) {
        Utf8Bytes.handleOneByte(b, stringBuilder);
        ++i;
      } else if (Utf8Bytes.isTwoBytes(b)) {
        Utf8Bytes.handleTwoBytes(b, readAscendingByte(), stringBuilder);
        i += 2;
      } else if (Utf8Bytes.isThreeBytes(b)) {
        Utf8Bytes.handleThreeBytes(b, readAscendingByte(), readAscendingByte(), stringBuilder);
        i += 3;
      } else {
        Utf8Bytes.handleFourBytes(
            b, readAscendingByte(), readAscendingByte(), readAscendingByte(), stringBuilder);
        i += 4;
      }
    }
    readSeparatorAscending();
    return stringBuilder.toString();
  }

  /** Reads an unsigned long that was encoded in ascending form. */
  public long readUnsignedLongAscending() {
    int len = buffer[position];
    if (len > Long.BYTES) {
      throw invalidOrderedCode();
    }
    ++position;
    int end = position + len;
    long result = 0;
    while (position < end) {
      result <<= 8;
      result |= buffer[position++] & 0xff;
    }
    return result;
  }

  /** Return the position of the current encoder. Intended to be used with {@link #seek(int)}. */
  public int position() {
    return position;
  }

  /** Sets the position of the current encoder. Intended to be used with {@link #position()}. */
  public void seek(int pos) {
    position = pos;
  }

  /** Checks if there are remaining bytes left in buffer. */
  public boolean hasRemainingBytes() {
    return position < buffer.length;
  }

  /**
   * Find the next ascending separator, return the number of decoded bytes until (but not including)
   * the separator.
   */
  private int findSeparatorAscending() {
    int numBytes = 0;
    for (int i = position; i < buffer.length; ++i) {
      if (buffer[i] == ESCAPE1 && buffer[i + 1] == SEPARATOR) {
        return numBytes;
      } else if (buffer[i] == ESCAPE1) {
        ++i;
      } else if (buffer[i] == ESCAPE2) {
        ++i;
      }
      ++numBytes;
    }
    throw new IllegalArgumentException("Invalid encoded byte array");
  }

  /** Reads an ascending separator value. */
  public void readSeparatorAscending() {
    if (buffer[position] == ESCAPE1 && buffer[position + 1] == SEPARATOR) {
      position += 2;
      return;
    }
    throw invalidOrderedCode();
  }

  private byte readAscendingByte() {
    byte b = buffer[position++];
    if (b == ESCAPE1) {
      b = buffer[position++];
      // Separator is not valid to read here.
      if (b != NULL_BYTE) {
        throw invalidOrderedCode();
      }
      return ESCAPE1;
    } else if (b == ESCAPE2) {
      b = buffer[position++];
      // Infinity is not valid to read here.
      if (b != FF_BYTE) {
        throw invalidOrderedCode();
      }
      return ESCAPE2;
    } else {
      return b;
    }
  }

  private static IllegalArgumentException invalidOrderedCode() {
    return new IllegalArgumentException("invalid ordered code bytes");
  }

  /** Utility methods for decoding bytes into {@link StringBuilder}. */
  private static class Utf8Bytes {

    /** Returns whether this is a single-byte codepoint (i.e., ASCII) with the form '0XXXXXXX'. */
    private static boolean isOneByte(byte b) {
      return b >= 0;
    }

    /**
     * Returns whether this is a two-byte codepoint with the form '10XXXXXX' iff {@link
     * #isOneByte(byte)} is false.
     */
    private static boolean isTwoBytes(byte b) {
      return b < (byte) 0xE0;
    }

    /**
     * Returns whether this is a three-byte codepoint with the form '110XXXXX' iff {@link
     * #isTwoBytes(byte)} is false.
     */
    private static boolean isThreeBytes(byte b) {
      return b < (byte) 0xF0;
    }

    private static void handleOneByte(byte byte1, StringBuilder builder) {
      builder.append((char) byte1);
    }

    private static void handleTwoBytes(byte byte1, byte byte2, StringBuilder builder) {
      char c = (char) (((byte1 & 0x1F) << 6) | trailingByteValue(byte2));
      if (c < '\u0080') {
        throw new IllegalArgumentException("invalid utf8");
      }
      builder.append(c);
    }

    public static boolean isSurrogate(char ch) {
      return ch >= MIN_SURROGATE && ch < (MAX_SURROGATE + 1);
    }

    public static boolean isBmpCodePoint(int codePoint) {
      return codePoint >>> 16 == 0;
    }

    public static char highSurrogate(int codePoint) {
      return (char)
          ((codePoint >>> 10) + (MIN_HIGH_SURROGATE - (MIN_SUPPLEMENTARY_CODE_POINT >>> 10)));
    }

    public static char lowSurrogate(int codePoint) {
      return (char) ((codePoint & 0x3ff) + MIN_LOW_SURROGATE);
    }

    private static void handleThreeBytes(
        byte byte1, byte byte2, byte byte3, StringBuilder builder) {
      char c =
          (char)
              (((byte1 & 0x0F) << 12) | (trailingByteValue(byte2) << 6) | trailingByteValue(byte3));
      if (c < 0x800 || isSurrogate(c)) {
        throw new IllegalArgumentException("invalid utf8");
      }
      builder.append(c);
    }

    private static void handleFourBytes(
        byte byte1, byte byte2, byte byte3, byte byte4, StringBuilder builder) {
      int codepoint =
          ((byte1 & 0x07) << 18)
              | (trailingByteValue(byte2) << 12)
              | (trailingByteValue(byte3) << 6)
              | trailingByteValue(byte4);
      if (isBmpCodePoint(codepoint)) {
        throw new IllegalArgumentException("invalid utf8");
      }
      builder.append(highSurrogate(codepoint));
      builder.append(lowSurrogate(codepoint));
    }

    /** Returns the actual value of the trailing byte (removes the prefix '10') for composition. */
    private static int trailingByteValue(byte b) {
      // Verify that the byte is of the form '10XXXXXX'.  (Type byte is signed.)
      if (b > (byte) 0xBF) {
        throw new IllegalArgumentException("invalid utf8");
      }
      return b & 0x3F;
    }
  }
}
