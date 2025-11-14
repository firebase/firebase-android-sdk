// Protocol Buffers - Google's data interchange format
// Copyright 2008 Google Inc.  All rights reserved.
//
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file or at
// https://developers.google.com/open-source/licenses/bsd

package com.google.firebase.dataconnect.sqlite;

import static java.lang.Character.MAX_SURROGATE;
import static java.lang.Character.MIN_HIGH_SURROGATE;
import static java.lang.Character.MIN_LOW_SURROGATE;
import static java.lang.Character.MIN_SUPPLEMENTARY_CODE_POINT;
import static java.lang.Character.MIN_SURROGATE;
import static java.lang.Character.isSurrogatePair;
import static java.lang.Character.toCodePoint;

import com.google.protobuf.InvalidProtocolBufferException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;

// This entire file was adapted from
// https://github.com/protocolbuffers/protobuf/blob/1bf47797fbe591392269c50cd83e153938450462/java/core/src/main/java/com/google/protobuf/Utf8.java
// with some unnecessary stuff removed.

/**
 * A set of low-level, high-performance static utility methods related to the UTF-8 character
 * encoding. This class has no dependencies outside of the core JDK libraries.
 *
 * <p>There are several variants of UTF-8. The one implemented by this class is the restricted
 * definition of UTF-8 introduced in Unicode 3.1, which mandates the rejection of "overlong" byte
 * sequences as well as rejection of 3-byte surrogate codepoint byte sequences. Note that the UTF-8
 * decoder included in Oracle's JDK has been modified to also reject "overlong" byte sequences, but
 * (as of 2011) still accepts 3-byte surrogate codepoint byte sequences.
 *
 * <p>The byte sequences considered valid by this class are exactly those that can be roundtrip
 * converted to Strings and back to bytes using the UTF-8 charset, without loss:
 *
 * <pre>{@code
 * Arrays.equals(bytes, new String(bytes, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8))
 * }</pre>
 *
 * <p>See the Unicode Standard,</br> Table 3-6. <em>UTF-8 Bit Distribution</em>,</br> Table 3-7.
 * <em>Well Formed UTF-8 Byte Sequences</em>.
 *
 * <p>This class supports decoding of partial byte sequences, so that the bytes in a complete UTF-8
 * byte sequence can be stored in multiple segments. Methods typically return {@link #MALFORMED} if
 * the partial byte sequence is definitely not well-formed; {@link #COMPLETE} if it is well-formed
 * in the absence of additional input; or, if the byte sequence apparently terminated in the middle
 * of a character, an opaque integer "state" value containing enough information to decode the
 * character when passed to a subsequent invocation of a partial decoding method.
 *
 * @author martinrb@google.com (Martin Buchholz)
 */
final class Utf8 {

  /**
   * Maximum number of bytes per Java UTF-16 char in UTF-8.
   *
   * @see java.nio.charset.CharsetEncoder#maxBytesPerChar()
   */
  public static final int MAX_BYTES_PER_CHAR = 3;

  /**
   * A mask used when performing unsafe reads to determine if a long value contains any non-ASCII
   * characters (i.e. any byte >= 0x80).
   */
  private static final long ASCII_MASK_LONG = 0x8080808080808080L;

  /**
   * State value indicating that the byte sequence is well-formed and complete (no further bytes are
   * needed to complete a character).
   */
  static final int COMPLETE = 0;

  /** State value indicating that the byte sequence is definitely not well-formed. */
  static final int MALFORMED = -1;

  // Other state values include the partial bytes of the incomplete
  // character to be decoded in the simplest way: we pack the bytes
  // into the state int in little-endian order.  For example:
  //
  // int state = byte1 ^ (byte2 << 8) ^ (byte3 << 16);
  //
  // Such a state is unpacked thus (note the ~ operation for byte2 to
  // undo byte1's sign-extension bits):
  //
  // int byte1 = (byte) state;
  // int byte2 = (byte) ~(state >> 8);
  // int byte3 = (byte) (state >> 16);
  //
  // We cannot store a zero byte in the state because it would be
  // indistinguishable from the absence of a byte.  But we don't need
  // to, because partial bytes must always be negative.  When building
  // a state, we ensure that byte1 is negative and subsequent bytes
  // are valid trailing bytes.

  /**
   * Returns {@code true} if the given byte array is a well-formed UTF-8 byte sequence.
   *
   * <p>This is a convenience method, equivalent to a call to {@code isValidUtf8(bytes, 0,
   * bytes.length)}.
   */
  public static boolean isValidUtf8(byte[] bytes) {
    return isValidUtf8(bytes, 0, bytes.length);
  }

  /**
   * Returns {@code true} if the given byte array slice is a well-formed UTF-8 byte sequence. The
   * range of bytes to be checked extends from index {@code index}, inclusive, to {@code limit},
   * exclusive.
   */
  public static boolean isValidUtf8(byte[] bytes, int index, int limit) {
    return partialIsValidUtf8(COMPLETE, bytes, index, limit) == COMPLETE;
  }

  /**
   * Tells whether the given byte array slice is a well-formed, malformed, or incomplete UTF-8
   * byte sequence. The range of bytes to be checked extends from index {@code index}, inclusive,
   * to {@code limit}, exclusive.
   *
   * @param state either {@link Utf8#COMPLETE} (if this is the initial decoding operation) or the
   *     value returned from a call to a partial decoding method for the previous bytes
   * @return {@link #MALFORMED} if the partial byte sequence is definitely not well-formed, {@link
   *     #COMPLETE} if it is well-formed (no additional input needed), or if the byte sequence is
   *     "incomplete", i.e. apparently terminated in the middle of a character, an opaque integer
   *     "state" value containing enough information to decode the character when passed to a
   *     subsequent invocation of a partial decoding method.
   */
  public static int partialIsValidUtf8(int state, byte[] bytes, int index, int limit) {
    if (state != COMPLETE) {
      // The previous decoding operation was incomplete (or malformed).
      // We look for a well-formed sequence consisting of bytes from
      // the previous decoding operation (stored in state) together
      // with bytes from the array slice.
      //
      // We expect such "straddler characters" to be rare.

      if (index >= limit) { // No bytes? No progress.
        return state;
      }
      int byte1 = (byte) state;
      // byte1 is never ASCII.
      if (byte1 < (byte) 0xE0) {
        // two-byte form

        // Simultaneously checks for illegal trailing-byte in
        // leading position and overlong 2-byte form.
        if (byte1 < (byte) 0xC2
            // byte2 trailing-byte test
            || bytes[index++] > (byte) 0xBF) {
          return MALFORMED;
        }
      } else if (byte1 < (byte) 0xF0) {
        // three-byte form

        // Get byte2 from saved state or array
        int byte2 = (byte) ~(state >> 8);
        if (byte2 == 0) {
          byte2 = bytes[index++];
          if (index >= limit) {
            return incompleteStateFor(byte1, byte2);
          }
        }
        if (byte2 > (byte) 0xBF
            // overlong? 5 most significant bits must not all be zero
            || (byte1 == (byte) 0xE0 && byte2 < (byte) 0xA0)
            // illegal surrogate codepoint?
            || (byte1 == (byte) 0xED && byte2 >= (byte) 0xA0)
            // byte3 trailing-byte test
            || bytes[index++] > (byte) 0xBF) {
          return MALFORMED;
        }
      } else {
        // four-byte form

        // Get byte2 and byte3 from saved state or array
        int byte2 = (byte) ~(state >> 8);
        int byte3 = 0;
        if (byte2 == 0) {
          byte2 = bytes[index++];
          if (index >= limit) {
            return incompleteStateFor(byte1, byte2);
          }
        } else {
          byte3 = (byte) (state >> 16);
        }
        if (byte3 == 0) {
          byte3 = bytes[index++];
          if (index >= limit) {
            return incompleteStateFor(byte1, byte2, byte3);
          }
        }

        // If we were called with state == MALFORMED, then byte1 is 0xFF,
        // which never occurs in well-formed UTF-8, and so we will return
        // MALFORMED again below.

        if (byte2 > (byte) 0xBF
            // Check that 1 <= plane <= 16.  Tricky optimized form of:
            // if (byte1 > (byte) 0xF4 ||
            //     byte1 == (byte) 0xF0 && byte2 < (byte) 0x90 ||
            //     byte1 == (byte) 0xF4 && byte2 > (byte) 0x8F)
            || (((byte1 << 28) + (byte2 - (byte) 0x90)) >> 30) != 0
            // byte3 trailing-byte test
            || byte3 > (byte) 0xBF
            // byte4 trailing-byte test
            || bytes[index++] > (byte) 0xBF) {
          return MALFORMED;
        }
      }
    }

    return partialIsValidUtf8(bytes, index, limit);
  }

  private static int incompleteStateFor(int byte1) {
    return (byte1 > (byte) 0xF4) ? MALFORMED : byte1;
  }

  private static int incompleteStateFor(int byte1, int byte2) {
    return (byte1 > (byte) 0xF4 || byte2 > (byte) 0xBF) ? MALFORMED : byte1 ^ (byte2 << 8);
  }

  private static int incompleteStateFor(int byte1, int byte2, int byte3) {
    return (byte1 > (byte) 0xF4 || byte2 > (byte) 0xBF || byte3 > (byte) 0xBF)
        ? MALFORMED
        : byte1 ^ (byte2 << 8) ^ (byte3 << 16);
  }

  private static int incompleteStateFor(byte[] bytes, int index, int limit) {
    int byte1 = bytes[index - 1];
    switch (limit - index) {
      case 0:
        return incompleteStateFor(byte1);
      case 1:
        return incompleteStateFor(byte1, bytes[index]);
      case 2:
        return incompleteStateFor(byte1, bytes[index], bytes[index + 1]);
      default:
        throw new AssertionError();
    }
  }

  private static int incompleteStateFor(
      final ByteBuffer buffer, final int byte1, final int index, final int remaining) {
    switch (remaining) {
      case 0:
        return incompleteStateFor(byte1);
      case 1:
        return incompleteStateFor(byte1, buffer.get(index));
      case 2:
        return incompleteStateFor(byte1, buffer.get(index), buffer.get(index + 1));
      default:
        throw new AssertionError();
    }
  }

  /**
   * Returns the number of bytes in the UTF-8-encoded form of {@code sequence}. For a string, this
   * method is equivalent to {@code string.getBytes(UTF_8).length}, but is more efficient in both
   * time and space.
   */
  @Nullable
  public static Integer encodedLength(String string) {
    // Warning to maintainers: this implementation is highly optimized.
    int utf16Length = string.length();
    int utf8Length = utf16Length;
    int i = 0;

    // This loop optimizes for pure ASCII.
    while (i < utf16Length && string.charAt(i) < 0x80) {
      i++;
    }

    // This loop optimizes for chars less than 0x800.
    for (; i < utf16Length; i++) {
      char c = string.charAt(i);
      if (c < 0x800) {
        utf8Length += ((0x7f - c) >>> 31); // branch free!
      } else {
        Integer length = encodedLengthGeneral(string, i);
        if (length == null) {
          return null;
        }
        utf8Length += length;
        break;
      }
    }

    if (utf8Length < utf16Length) {
      // Necessary and sufficient condition for overflow because of maximum 3x expansion
      throw new IllegalArgumentException(
          "UTF-8 length does not fit in int: " + (utf8Length + (1L << 32)));
    }
    return utf8Length;
  }

  /**
   * @return the encoded length, or null if a long surrogate is encountered.
   */
  @Nullable
  private static Integer encodedLengthGeneral(String string, int start) {
    int utf16Length = string.length();
    int utf8Length = 0;
    for (int i = start; i < utf16Length; i++) {
      char c = string.charAt(i);
      if (c < 0x800) {
        utf8Length += (0x7f - c) >>> 31; // branch free!
      } else {
        utf8Length += 2;
        if (Character.isSurrogate(c)) {
          // Check that we have a well-formed surrogate pair.
          int cp = Character.codePointAt(string, i);
          if (cp < MIN_SUPPLEMENTARY_CODE_POINT) {
            return null; // lone surrogate
          }
          i++;
        }
      }
    }
    return utf8Length;
  }

  static int encode(String in, byte[] out, int offset, int length) {
    return encodeUtf8(in, out, offset, length);
  }

  // End Guava UTF-8 methods.

  /**
   * Determines if the given {@link ByteBuffer} is a valid UTF-8 string.
   *
   * <p>Selects an optimal algorithm based on the type of {@link ByteBuffer} (i.e. heap or direct)
   * and the capabilities of the platform.
   *
   * @param buffer the buffer to check.
   * @see Utf8#isValidUtf8(byte[], int, int)
   */
  static boolean isValidUtf8(ByteBuffer buffer) {
    return isValidUtf8(buffer, buffer.position(), buffer.remaining());
  }

  /**
   * Decodes the given UTF-8 portion of the {@link ByteBuffer} into a {@link String}.
   *
   * @throws InvalidProtocolBufferException if the input is not valid UTF-8.
   */
  public static String decodeUtf8(ByteBuffer buffer, int index, int size)
      throws InvalidProtocolBufferException {
    if (buffer.hasArray()) {
      final int offset = buffer.arrayOffset();
      return decodeUtf8(buffer.array(), offset + index, size);
    } else if (buffer.isDirect()) {
      return decodeUtf8Direct(buffer, index, size);
    }
    return decodeUtf8Default(buffer, index, size);
  }

  /**
   * Decodes the given byte array slice into a {@link String}.
   *
   * @throws InvalidProtocolBufferException if the byte array slice is not valid UTF-8
   */
  public static String decodeUtf8(byte[] bytes, int index, int size)
      throws InvalidProtocolBufferException {
    // Bitwise OR combines the sign bits so any negative value fails the check.
    if ((index | size | bytes.length - index - size) < 0) {
      throw new ArrayIndexOutOfBoundsException(
          String.format("buffer length=%d, index=%d, size=%d", bytes.length, index, size));
    }

    int offset = index;
    final int limit = offset + size;

    // The longest possible resulting String is the same as the number of input bytes, when it is
    // all ASCII. For other cases, this over-allocates and we will truncate in the end.
    char[] resultArr = new char[size];
    int resultPos = 0;

    // Optimize for 100% ASCII (Hotspot loves small simple top-level loops like this).
    // This simple loop stops when we encounter a byte >= 0x80 (i.e. non-ASCII).
    while (offset < limit) {
      byte b = bytes[offset];
      if (!DecodeUtil.isOneByte(b)) {
        break;
      }
      offset++;
      DecodeUtil.handleOneByte(b, resultArr, resultPos++);
    }

    while (offset < limit) {
      byte byte1 = bytes[offset++];
      if (DecodeUtil.isOneByte(byte1)) {
        DecodeUtil.handleOneByte(byte1, resultArr, resultPos++);
        // It's common for there to be multiple ASCII characters in a run mixed in, so add an
        // extra optimized loop to take care of these runs.
        while (offset < limit) {
          byte b = bytes[offset];
          if (!DecodeUtil.isOneByte(b)) {
            break;
          }
          offset++;
          DecodeUtil.handleOneByte(b, resultArr, resultPos++);
        }
      } else if (DecodeUtil.isTwoBytes(byte1)) {
        if (offset >= limit) {
          throw new InvalidProtocolBufferException("Protocol message had invalid UTF-8.");
        }
        DecodeUtil.handleTwoBytes(byte1, /* byte2 */ bytes[offset++], resultArr, resultPos++);
      } else if (DecodeUtil.isThreeBytes(byte1)) {
        if (offset >= limit - 1) {
          throw new InvalidProtocolBufferException("Protocol message had invalid UTF-8.");
        }
        DecodeUtil.handleThreeBytes(
            byte1,
            /* byte2 */ bytes[offset++],
            /* byte3 */ bytes[offset++],
            resultArr,
            resultPos++);
      } else {
        if (offset >= limit - 2) {
          throw new InvalidProtocolBufferException("Protocol message had invalid UTF-8.");
        }
        DecodeUtil.handleFourBytes(
            byte1,
            /* byte2 */ bytes[offset++],
            /* byte3 */ bytes[offset++],
            /* byte4 */ bytes[offset++],
            resultArr,
            resultPos++);
        // 4-byte case requires two chars.
        resultPos++;
      }
    }

    return new String(resultArr, 0, resultPos);
  }

  /**
   * Encodes an input character sequence ({@code in}) to UTF-8 in the target buffer ({@code out}).
   * Upon returning from this method, the {@code out} position will point to the position after
   * the last encoded byte. This method requires paired surrogates, and therefore does not support
   * chunking.
   *
   * <p>Selects an optimal algorithm based on the type of {@link ByteBuffer} (i.e. heap or direct)
   * and the capabilities of the platform.
   *
   * <p>To ensure sufficient space in the output buffer, either call {@link #encodedLength} to
   * compute the exact amount needed, or leave room for {@code Utf8.MAX_BYTES_PER_CHAR *
   * in.length()}, which is the largest possible number of bytes that any input can be encoded to.
   *
   * @param in the source character sequence to be encoded
   * @param out the target buffer
   * @throws ArrayIndexOutOfBoundsException if {@code in} encoded in UTF-8 is longer than {@code
   *     out.remaining()}
   */
  public static void encodeUtf8(String in, ByteBuffer out) {
    if (out.hasArray()) {
      final int offset = out.arrayOffset();
      int endIndex = Utf8.encode(in, out.array(), offset + out.position(), out.remaining());
      out.position(endIndex - offset);
    } else {
      encodeUtf8Internal(in, out);
    }
  }

  /**
   * Counts (approximately) the number of consecutive ASCII characters in the given buffer. The byte
   * order of the {@link ByteBuffer} does not matter, so performance can be improved if native byte
   * order is used (i.e. no byte-swapping in {@link ByteBuffer#getLong(int)}).
   *
   * @param buffer the buffer to be scanned for ASCII chars
   * @param index the starting index of the scan
   * @param limit the limit within buffer for the scan
   * @return the number of ASCII characters found. The stopping position will be at or before the
   *     first non-ASCII byte.
   */
  private static int estimateConsecutiveAscii(ByteBuffer buffer, int index, int limit) {
    int i = index;
    final int lim = limit - 7;
    // This simple loop stops when we encounter a byte >= 0x80 (i.e. non-ASCII).
    // To speed things up further, we're reading longs instead of bytes so we use a mask to
    // determine if any byte in the current long is non-ASCII.
    for (; i < lim && (buffer.getLong(i) & ASCII_MASK_LONG) == 0; i += 8) {}
    return i - index;
  }

  /**
   * Returns {@code true} if the given portion of the {@link ByteBuffer} is a well-formed UTF-8
   * byte sequence. The range of bytes to be checked extends from index {@code index}, inclusive,
   * to {@code limit}, exclusive.
   *
   * <p>This is a convenience method, equivalent to {@code partialIsValidUtf8(bytes, index, limit)
   * == Utf8.COMPLETE}.
   */
  public static boolean isValidUtf8(ByteBuffer buffer, int index, int limit) {
    return partialIsValidUtf8(COMPLETE, buffer, index, limit) == COMPLETE;
  }

  /**
   * Indicates whether or not the given buffer contains a valid UTF-8 string.
   *
   * @param buffer the buffer to check.
   * @return {@code true} if the given buffer contains a valid UTF-8 string.
   */
  public static int partialIsValidUtf8(
      final int state, final ByteBuffer buffer, int index, final int limit) {
    if (buffer.hasArray()) {
      final int offset = buffer.arrayOffset();
      return partialIsValidUtf8(state, buffer.array(), offset + index, offset + limit);
    } else if (buffer.isDirect()) {
      return partialIsValidUtf8Direct(state, buffer, index, limit);
    }
    return partialIsValidUtf8Default(state, buffer, index, limit);
  }

  /** Performs validation for direct {@link ByteBuffer} instances. */
  private static int partialIsValidUtf8Direct(int state, ByteBuffer buffer, int index, int limit) {
    // For safe processing, we have to use the ByteBuffer API.
    return partialIsValidUtf8Default(state, buffer, index, limit);
  }

  /**
   * Performs validation for {@link ByteBuffer} instances using the {@link ByteBuffer} API rather
   * than potentially faster approaches. This first completes validation for the current character
   * (provided by {@code state}) and then finishes validation for the sequence.
   */
  private static int partialIsValidUtf8Default(
      final int state, final ByteBuffer buffer, int index, final int limit) {
    if (state != COMPLETE) {
      // The previous decoding operation was incomplete (or malformed).
      // We look for a well-formed sequence consisting of bytes from
      // the previous decoding operation (stored in state) together
      // with bytes from the array slice.
      //
      // We expect such "straddler characters" to be rare.

      if (index >= limit) { // No bytes? No progress.
        return state;
      }

      byte byte1 = (byte) state;
      // byte1 is never ASCII.
      if (byte1 < (byte) 0xE0) {
        // two-byte form

        // Simultaneously checks for illegal trailing-byte in
        // leading position and overlong 2-byte form.
        if (byte1 < (byte) 0xC2
            // byte2 trailing-byte test
            || buffer.get(index++) > (byte) 0xBF) {
          return MALFORMED;
        }
      } else if (byte1 < (byte) 0xF0) {
        // three-byte form

        // Get byte2 from saved state or array
        byte byte2 = (byte) ~(state >> 8);
        if (byte2 == 0) {
          byte2 = buffer.get(index++);
          if (index >= limit) {
            return incompleteStateFor(byte1, byte2);
          }
        }
        if (byte2 > (byte) 0xBF
            // overlong? 5 most significant bits must not all be zero
            || (byte1 == (byte) 0xE0 && byte2 < (byte) 0xA0)
            // illegal surrogate codepoint?
            || (byte1 == (byte) 0xED && byte2 >= (byte) 0xA0)
            // byte3 trailing-byte test
            || buffer.get(index++) > (byte) 0xBF) {
          return MALFORMED;
        }
      } else {
        // four-byte form

        // Get byte2 and byte3 from saved state or array
        byte byte2 = (byte) ~(state >> 8);
        byte byte3 = 0;
        if (byte2 == 0) {
          byte2 = buffer.get(index++);
          if (index >= limit) {
            return incompleteStateFor(byte1, byte2);
          }
        } else {
          byte3 = (byte) (state >> 16);
        }
        if (byte3 == 0) {
          byte3 = buffer.get(index++);
          if (index >= limit) {
            return incompleteStateFor(byte1, byte2, byte3);
          }
        }

        // If we were called with state == MALFORMED, then byte1 is 0xFF,
        // which never occurs in well-formed UTF-8, and so we will return
        // MALFORMED again below.

        if (byte2 > (byte) 0xBF
            // Check that 1 <= plane <= 16.  Tricky optimized form of:
            // if (byte1 > (byte) 0xF4 ||
            //     byte1 == (byte) 0xF0 && byte2 < (byte) 0x90 ||
            //     byte1 == (byte) 0xF4 && byte2 > (byte) 0x8F)
            || (((byte1 << 28) + (byte2 - (byte) 0x90)) >> 30) != 0
            // byte3 trailing-byte test
            || byte3 > (byte) 0xBF
            // byte4 trailing-byte test
            || buffer.get(index++) > (byte) 0xBF) {
          return MALFORMED;
        }
      }
    }

    // Finish validation for the sequence.
    return partialIsValidUtf8(buffer, index, limit);
  }

  /**
   * Performs validation for {@link ByteBuffer} instances using the {@link ByteBuffer} API rather
   * than potentially faster approaches.
   */
  public static int partialIsValidUtf8(final ByteBuffer buffer, int index, final int limit) {
    index += estimateConsecutiveAscii(buffer, index, limit);

    for (; ; ) {
      // Optimize for interior runs of ASCII bytes.
      // TODO: Consider checking 8 bytes at a time after some threshold?
      // Maybe after seeing a few in a row that are ASCII, go back to fast mode?
      int byte1;
      do {
        if (index >= limit) {
          return COMPLETE;
        }
      } while ((byte1 = buffer.get(index++)) >= 0);

      // If we're here byte1 is not ASCII. Only need to handle 2-4 byte forms.
      if (byte1 < (byte) 0xE0) {
        // Two-byte form (110xxxxx 10xxxxxx)
        if (index >= limit) {
          // Incomplete sequence
          return byte1;
        }

        // Simultaneously checks for illegal trailing-byte in
        // leading position and overlong 2-byte form.
        if (byte1 < (byte) 0xC2 || buffer.get(index) > (byte) 0xBF) {
          return MALFORMED;
        }
        index++;
      } else if (byte1 < (byte) 0xF0) {
        // Three-byte form (1110xxxx 10xxxxxx 10xxxxxx)
        if (index >= limit - 1) {
          // Incomplete sequence
          return incompleteStateFor(buffer, byte1, index, limit - index);
        }

        byte byte2 = buffer.get(index++);
        if (byte2 > (byte) 0xBF
            // overlong? 5 most significant bits must not all be zero
            || (byte1 == (byte) 0xE0 && byte2 < (byte) 0xA0)
            // check for illegal surrogate codepoints
            || (byte1 == (byte) 0xED && byte2 >= (byte) 0xA0)
            // byte3 trailing-byte test
            || buffer.get(index) > (byte) 0xBF) {
          return MALFORMED;
        }
        index++;
      } else {
        // Four-byte form (1110xxxx 10xxxxxx 10xxxxxx 10xxxxxx)
        if (index >= limit - 2) {
          // Incomplete sequence
          return incompleteStateFor(buffer, byte1, index, limit - index);
        }

        // TODO: Consider using getInt() to improve performance.
        int byte2 = buffer.get(index++);
        if (byte2 > (byte) 0xBF
            // Check that 1 <= plane <= 16.  Tricky optimized form of:
            // if (byte1 > (byte) 0xF4 ||
            //     byte1 == (byte) 0xF0 && byte2 < (byte) 0x90 ||
            //     byte1 == (byte) 0xF4 && byte2 > (byte) 0x8F)
            || (((byte1 << 28) + (byte2 - (byte) 0x90)) >> 30) != 0
            // byte3 trailing-byte test
            || buffer.get(index++) > (byte) 0xBF
            // byte4 trailing-byte test
            || buffer.get(index++) > (byte) 0xBF) {
          return MALFORMED;
        }
      }
    }
  }

  /** Decodes direct {@link ByteBuffer} instances into {@link String}. */
  private static String decodeUtf8Direct(ByteBuffer buffer, int index, int size)
      throws InvalidProtocolBufferException {
    // For safe processing, we have to use the ByteBufferAPI.
    return decodeUtf8Default(buffer, index, size);
  }

  /**
   * Decodes {@link ByteBuffer} instances using the {@link ByteBuffer} API rather than potentially
   * faster approaches.
   */
  private static String decodeUtf8Default(ByteBuffer buffer, int index, int size)
      throws InvalidProtocolBufferException {
    // Bitwise OR combines the sign bits so any negative value fails the check.
    if ((index | size | buffer.limit() - index - size) < 0) {
      throw new ArrayIndexOutOfBoundsException(
          String.format("buffer limit=%d, index=%d, limit=%d", buffer.limit(), index, size));
    }

    int offset = index;
    int limit = offset + size;

    // The longest possible resulting String is the same as the number of input bytes, when it is
    // all ASCII. For other cases, this over-allocates and we will truncate in the end.
    char[] resultArr = new char[size];
    int resultPos = 0;

    // Optimize for 100% ASCII (Hotspot loves small simple top-level loops like this).
    // This simple loop stops when we encounter a byte >= 0x80 (i.e. non-ASCII).
    while (offset < limit) {
      byte b = buffer.get(offset);
      if (!DecodeUtil.isOneByte(b)) {
        break;
      }
      offset++;
      DecodeUtil.handleOneByte(b, resultArr, resultPos++);
    }

    while (offset < limit) {
      byte byte1 = buffer.get(offset++);
      if (DecodeUtil.isOneByte(byte1)) {
        DecodeUtil.handleOneByte(byte1, resultArr, resultPos++);
        // It's common for there to be multiple ASCII characters in a run mixed in, so add an
        // extra optimized loop to take care of these runs.
        while (offset < limit) {
          byte b = buffer.get(offset);
          if (!DecodeUtil.isOneByte(b)) {
            break;
          }
          offset++;
          DecodeUtil.handleOneByte(b, resultArr, resultPos++);
        }
      } else if (DecodeUtil.isTwoBytes(byte1)) {
        if (offset >= limit) {
          throw new InvalidProtocolBufferException("Protocol message had invalid UTF-8.");
        }
        DecodeUtil.handleTwoBytes(byte1, /* byte2 */ buffer.get(offset++), resultArr, resultPos++);
      } else if (DecodeUtil.isThreeBytes(byte1)) {
        if (offset >= limit - 1) {
          throw new InvalidProtocolBufferException("Protocol message had invalid UTF-8.");
        }
        DecodeUtil.handleThreeBytes(
            byte1,
            /* byte2 */ buffer.get(offset++),
            /* byte3 */ buffer.get(offset++),
            resultArr,
            resultPos++);
      } else {
        if (offset >= limit - 2) {
          throw new InvalidProtocolBufferException("Protocol message had invalid UTF-8.");
        }
        DecodeUtil.handleFourBytes(
            byte1,
            /* byte2 */ buffer.get(offset++),
            /* byte3 */ buffer.get(offset++),
            /* byte4 */ buffer.get(offset++),
            resultArr,
            resultPos++);
        // 4-byte case requires two chars.
        resultPos++;
      }
    }

    return new String(resultArr, 0, resultPos);
  }

  private static void encodeUtf8Naive(String in, ByteBuffer out) {
    final byte[] bytes = in.getBytes(StandardCharsets.UTF_8);
    try {
      out.put(bytes);
    } catch (BufferOverflowException unused) {
      throw new ArrayIndexOutOfBoundsException(
          "Not enough space in output buffer to encode UTF-8 string");
    }
  }

  /**
   * Encodes an input character sequence ({@code in}) to UTF-8 in the target array ({@code out}).
   * For a string, this method is functionally identical to
   *
   * <pre>{@code
   * byte[] a = string.getBytes(UTF_8);
   * System.arraycopy(a, 0, bytes, offset, a.length);
   * return offset + a.length;
   * }</pre>
   *
   * but may be implemented differently for efficiency purposes.
   *
   * <p>Matching {@code String.getBytes(UTF_8)} this replaces unpaired surrogates with a
   * replacement character.
   *
   * <p>To ensure sufficient space in the output buffer, either call {@link #encodedLength} to
   * compute the exact amount needed, or leave room for {@code Utf8.MAX_BYTES_PER_CHAR *
   * sequence.length()}, which is the largest possible number of bytes that any input can be
   * encoded to.
   *
   * @param in the input character sequence to be encoded
   * @param out the target array
   * @param offset the starting offset in {@code bytes} to start writing at
   * @param length the length of the {@code bytes}, starting from {@code offset}
   * @return the new offset, equivalent to {@code offset + Utf8.encodedLength(sequence)}, or a
   * negative value if there was insufficient space in the output array or if a lone surrogate
   * was encountered.
   */
  public static int encodeUtf8(String in, byte[] out, int offset, int length) {
    int utf16Length = in.length();
    int j = offset;
    int i = 0;
    int limit = offset + length;
    // Designed to take advantage of
    // https://wiki.openjdk.java.net/display/HotSpotInternals/RangeCheckElimination
    for (char c; i < utf16Length && i + j < limit && (c = in.charAt(i)) < 0x80; i++) {
      out[j + i] = (byte) c;
    }
    if (i == utf16Length) {
      return j + utf16Length;
    }
    j += i;
    for (char c; i < utf16Length; i++) {
      c = in.charAt(i);
      if (c < 0x80 && j < limit) {
        out[j++] = (byte) c;
      } else if (c < 0x800 && j <= limit - 2) { // 11 bits, two UTF-8 bytes
        out[j++] = (byte) ((0xF << 6) | (c >>> 6));
        out[j++] = (byte) (0x80 | (0x3F & c));
      } else if ((c < Character.MIN_SURROGATE || Character.MAX_SURROGATE < c) && j <= limit - 3) {
        // Maximum single-char code point is 0xFFFF, 16 bits, three UTF-8 bytes
        out[j++] = (byte) ((0xF << 5) | (c >>> 12));
        out[j++] = (byte) (0x80 | (0x3F & (c >>> 6)));
        out[j++] = (byte) (0x80 | (0x3F & c));
      } else if (j <= limit - 4) {
        // Minimum code point represented by a surrogate pair is 0x10000, 17 bits,
        // four UTF-8 bytes
        final char low;
        if (i + 1 == in.length() || !Character.isSurrogatePair(c, (low = in.charAt(++i)))) {
          return -1;
        }
        int codePoint = Character.toCodePoint(c, low);
        out[j++] = (byte) ((0xF << 4) | (codePoint >>> 18));
        out[j++] = (byte) (0x80 | (0x3F & (codePoint >>> 12)));
        out[j++] = (byte) (0x80 | (0x3F & (codePoint >>> 6)));
        out[j++] = (byte) (0x80 | (0x3F & codePoint));
      } else {
        return -1;
      }
    }
    return j;
  }

  /** Encodes the input character sequence to a direct {@link ByteBuffer} instance. */
  private static void encodeUtf8Internal(String in, ByteBuffer out) {
    final int inLength = in.length();
    int outIx = out.position();
    int inIx = 0;

    // Since ByteBuffer.putXXX() already checks boundaries for us, no need to explicitly check
    // access. Assume the buffer is big enough and let it handle the out of bounds exception
    // if it occurs.
    try {
      // Designed to take advantage of
      // https://wiki.openjdk.java.net/display/HotSpotInternals/RangeCheckElimination
      for (char c; inIx < inLength && (c = in.charAt(inIx)) < 0x80; ++inIx) {
        out.put(outIx + inIx, (byte) c);
      }
      if (inIx == inLength) {
        // Successfully encoded the entire string.
        out.position(outIx + inIx);
        return;
      }

      outIx += inIx;
      for (char c; inIx < inLength; ++inIx, ++outIx) {
        c = in.charAt(inIx);
        if (c < 0x80) {
          // One byte (0xxx xxxx)
          out.put(outIx, (byte) c);
        } else if (c < 0x800) {
          // Two bytes (110x xxxx 10xx xxxx)

          // Benchmarks show put performs better than putShort here (for HotSpot).
          out.put(outIx++, (byte) (0xC0 | (c >>> 6)));
          out.put(outIx, (byte) (0x80 | (0x3F & c)));
        } else if (c < MIN_SURROGATE || MAX_SURROGATE < c) {
          // Three bytes (1110 xxxx 10xx xxxx 10xx xxxx)
          // Maximum single-char code point is 0xFFFF, 16 bits.

          // Benchmarks show put performs better than putShort here (for HotSpot).
          out.put(outIx++, (byte) (0xE0 | (c >>> 12)));
          out.put(outIx++, (byte) (0x80 | (0x3F & (c >>> 6))));
          out.put(outIx, (byte) (0x80 | (0x3F & c)));
        } else {
          // Four bytes (1111 xxxx 10xx xxxx 10xx xxxx 10xx xxxx)

          // Minimum code point represented by a surrogate pair is 0x10000, 17 bits, four UTF-8
          // bytes
          final char low;
          if (inIx + 1 == inLength || !isSurrogatePair(c, (low = in.charAt(++inIx)))) {
            // Unpaired surrogate, fall back to the naive encoder that will do replacement
            // characters.
            encodeUtf8Naive(in, out);
            return;
          }
          // TODO: Consider using putInt() to improve performance.
          int codePoint = toCodePoint(c, low);
          out.put(outIx++, (byte) ((0xF << 4) | (codePoint >>> 18)));
          out.put(outIx++, (byte) (0x80 | (0x3F & (codePoint >>> 12))));
          out.put(outIx++, (byte) (0x80 | (0x3F & (codePoint >>> 6))));
          out.put(outIx, (byte) (0x80 | (0x3F & codePoint)));
        }
      }

      // Successfully encoded the entire string.
      out.position(outIx);
    } catch (IndexOutOfBoundsException unused) {
      // TODO: Consider making the API throw IndexOutOfBoundsException instead.
      throw new ArrayIndexOutOfBoundsException(
          "Not enough space in output buffer to encode UTF-8 string");
    }
  }

  public static int partialIsValidUtf8(byte[] bytes, int index, int limit) {
    // Optimize for 100% ASCII (Hotspot loves small simple top-level loops like this).
    // This simple loop stops when we encounter a byte >= 0x80 (i.e. non-ASCII).
    while (index < limit && bytes[index] >= 0) {
      index++;
    }

    return (index >= limit) ? COMPLETE : partialIsValidUtf8NonAscii(bytes, index, limit);
  }

  private static int partialIsValidUtf8NonAscii(byte[] bytes, int index, int limit) {
    for (; ; ) {
      int byte1;
      int byte2;

      // Optimize for interior runs of ASCII bytes.
      do {
        if (index >= limit) {
          return COMPLETE;
        }
      } while ((byte1 = bytes[index++]) >= 0);

      if (byte1 < (byte) 0xE0) {
        // two-byte form

        if (index >= limit) {
          // Incomplete sequence
          return byte1;
        }

        // Simultaneously checks for illegal trailing-byte in
        // leading position and overlong 2-byte form.
        if (byte1 < (byte) 0xC2 || bytes[index++] > (byte) 0xBF) {
          return MALFORMED;
        }
      } else if (byte1 < (byte) 0xF0) {
        // three-byte form

        if (index >= limit - 1) { // incomplete sequence
          return incompleteStateFor(bytes, index, limit);
        }
        if ((byte2 = bytes[index++]) > (byte) 0xBF
            // overlong? 5 most significant bits must not all be zero
            || (byte1 == (byte) 0xE0 && byte2 < (byte) 0xA0)
            // check for illegal surrogate codepoints
            || (byte1 == (byte) 0xED && byte2 >= (byte) 0xA0)
            // byte3 trailing-byte test
            || bytes[index++] > (byte) 0xBF) {
          return MALFORMED;
        }
      } else {
        // four-byte form

        if (index >= limit - 2) { // incomplete sequence
          return incompleteStateFor(bytes, index, limit);
        }
        if ((byte2 = bytes[index++]) > (byte) 0xBF
            // Check that 1 <= plane <= 16.  Tricky optimized form of:
            // if (byte1 > (byte) 0xF4 ||
            //     byte1 == (byte) 0xF0 && byte2 < (byte) 0x90 ||
            //     byte1 == (byte) 0xF4 && byte2 > (byte) 0x8F)
            || (((byte1 << 28) + (byte2 - (byte) 0x90)) >> 30) != 0
            // byte3 trailing-byte test
            || bytes[index++] > (byte) 0xBF
            // byte4 trailing-byte test
            || bytes[index++] > (byte) 0xBF) {
          return MALFORMED;
        }
      }
    }
  }

  /**
   * Utility methods for decoding bytes into {@link String}. Callers are responsible for extracting
   * bytes (possibly using Unsafe methods), and checking remaining bytes. All other UTF-8 validity
   * checks and codepoint conversion happen in this class.
   */
  private static class DecodeUtil {

    /** Returns whether this is a single-byte codepoint (i.e., ASCII) with the form '0XXXXXXX'. */
    static boolean isOneByte(byte b) {
      return b >= 0;
    }

    /**
     * Returns whether this is a two-byte codepoint with the form '10XXXXXX' iff
     * {@link #isOneByte(byte)} is false. This method works in the limited use in
     * this class where this method is only called when {@link #isOneByte(byte)} has already
     * returned false. It is not suitable for general or public use.
     */
    static boolean isTwoBytes(byte b) {
      return b < (byte) 0xE0;
    }

    /**
     * Returns whether this is a three-byte codepoint with the form '110XXXXX' iff
     * {@link #isOneByte(byte)} and {@link #isTwoBytes(byte)} are false.
     * This method works in the limited use in
     * this class where this method is only called when {@link #isOneByte(byte)} an
     * {@link #isTwoBytes(byte)} have already returned false. It is not suitable for general
     * or public use.
     */
    static boolean isThreeBytes(byte b) {
      return b < (byte) 0xF0;
    }

    static void handleOneByte(byte byte1, char[] resultArr, int resultPos) {
      resultArr[resultPos] = (char) byte1;
    }

    static void handleTwoBytes(byte byte1, byte byte2, char[] resultArr, int resultPos)
        throws InvalidProtocolBufferException {
      // Simultaneously checks for illegal trailing-byte in leading position (<= '11000000') and
      // overlong 2-byte, '11000001'.
      if (byte1 < (byte) 0xC2 || isNotTrailingByte(byte2)) {
        throw new InvalidProtocolBufferException("Protocol message had invalid UTF-8.");
      }
      resultArr[resultPos] = (char) (((byte1 & 0x1F) << 6) | trailingByteValue(byte2));
    }

    static void handleThreeBytes(
        byte byte1, byte byte2, byte byte3, char[] resultArr, int resultPos)
        throws InvalidProtocolBufferException {
      if (isNotTrailingByte(byte2)
          // overlong? 5 most significant bits must not all be zero
          || (byte1 == (byte) 0xE0 && byte2 < (byte) 0xA0)
          // check for illegal surrogate codepoints
          || (byte1 == (byte) 0xED && byte2 >= (byte) 0xA0)
          || isNotTrailingByte(byte3)) {
        throw new InvalidProtocolBufferException("Protocol message had invalid UTF-8.");
      }
      resultArr[resultPos] =
          (char)
              (((byte1 & 0x0F) << 12) | (trailingByteValue(byte2) << 6) | trailingByteValue(byte3));
    }

    static void handleFourBytes(
        byte byte1, byte byte2, byte byte3, byte byte4, char[] resultArr, int resultPos)
        throws InvalidProtocolBufferException {
      if (isNotTrailingByte(byte2)
          // Check that 1 <= plane <= 16.  Tricky optimized form of:
          //   valid 4-byte leading byte?
          // if (byte1 > (byte) 0xF4 ||
          //   overlong? 4 most significant bits must not all be zero
          //     byte1 == (byte) 0xF0 && byte2 < (byte) 0x90 ||
          //   codepoint larger than the highest code point (U+10FFFF)?
          //     byte1 == (byte) 0xF4 && byte2 > (byte) 0x8F)
          || (((byte1 << 28) + (byte2 - (byte) 0x90)) >> 30) != 0
          || isNotTrailingByte(byte3)
          || isNotTrailingByte(byte4)) {
        throw new InvalidProtocolBufferException("Protocol message had invalid UTF-8.");
      }
      int codepoint =
          ((byte1 & 0x07) << 18)
              | (trailingByteValue(byte2) << 12)
              | (trailingByteValue(byte3) << 6)
              | trailingByteValue(byte4);
      resultArr[resultPos] = DecodeUtil.highSurrogate(codepoint);
      resultArr[resultPos + 1] = DecodeUtil.lowSurrogate(codepoint);
    }

    /** Returns whether the byte is not a valid continuation of the form '10XXXXXX'. */
    static boolean isNotTrailingByte(byte b) {
      return b > (byte) 0xBF;
    }

    /** Returns the actual value of the trailing byte (removes the prefix '10') for composition. */
    static int trailingByteValue(byte b) {
      return b & 0x3F;
    }

    static char highSurrogate(int codePoint) {
      return (char)
          ((MIN_HIGH_SURROGATE - (MIN_SUPPLEMENTARY_CODE_POINT >>> 10)) + (codePoint >>> 10));
    }

    static char lowSurrogate(int codePoint) {
      return (char) (MIN_LOW_SURROGATE + (codePoint & 0x3ff));
    }
  }

  private Utf8() {}
}
