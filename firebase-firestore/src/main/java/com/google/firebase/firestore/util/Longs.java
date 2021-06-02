/*
 * Copyright (C) 2008 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.firebase.firestore.util;

import java.util.Arrays;

/**
 * Static utility methods pertaining to {@code long} primitives, that are not already found in
 * either {@link Long} or {@link Arrays}.
 *
 * <p>See the Guava User Guide article on <a
 * href="https://github.com/google/guava/wiki/PrimitivesExplained">primitive utilities</a>.
 *
 * @author Kevin Bourrillion
 * @since 1.0
 */
public final class Longs {
  private Longs() {}

  /**
   * The number of bytes required to represent a primitive {@code long} value.
   *
   * <p><b>Java 8 users:</b> use {@link Long#BYTES} instead.
   */
  public static final int BYTES = Long.SIZE / Byte.SIZE;

  /**
   * Returns a hash code for {@code value}; equal to the result of invoking {@code ((Long)
   * value).hashCode()}.
   *
   * <p>This method always return the value specified by {@link Long#hashCode()} in java, which
   * might be different from {@code ((Long) value).hashCode()} in GWT because {@link
   * Long#hashCode()} in GWT does not obey the JRE contract.
   *
   * <p><b>Java 8 users:</b> use {@link Long#hashCode(long)} instead.
   *
   * @param value a primitive {@code long} value
   * @return a hash code for the value
   */
  public static int hashCode(long value) {
    return (int) (value ^ (value >>> 32));
  }

  /**
   * Compares the two specified {@code long} values. The sign of the value returned is the same as
   * that of {@code ((Long) a).compareTo(b)}.
   *
   * <p><b>Note for Java 7 and later:</b> this method should be treated as deprecated; use the
   * equivalent {@link Long#compare} method instead.
   *
   * @param a the first {@code long} to compare
   * @param b the second {@code long} to compare
   * @return a negative value if {@code a} is less than {@code b}; a positive value if {@code a} is
   *     greater than {@code b}; or zero if they are equal
   * <!-- MOE:begin_intracomment_strip -->
   * @deprecated Use {@link Long#compare} instead.
   * <!-- MOE:end_intracomment_strip -->
   */
  @Deprecated // MOE:strip_line
  public static int compare(long a, long b) {
    return (a < b) ? -1 : ((a > b) ? 1 : 0);
  }

  /**
   * Returns {@code true} if {@code target} is present as an element anywhere in {@code array}.
   *
   * @param array an array of {@code long} values, possibly empty
   * @param target a primitive {@code long} value
   * @return {@code true} if {@code array[i] == target} for some value of {@code i}
   */
  public static boolean contains(long[] array, long target) {
    for (long value : array) {
      if (value == target) {
        return true;
      }
    }
    return false;
  }

  // TODO(kevinb): consider making this public
  private static int indexOf(long[] array, long target, int start, int end) {
    for (int i = start; i < end; i++) {
      if (array[i] == target) {
        return i;
      }
    }
    return -1;
  }

  // TODO(kevinb): consider making this public
  private static int lastIndexOf(long[] array, long target, int start, int end) {
    for (int i = end - 1; i >= start; i--) {
      if (array[i] == target) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns the least value present in {@code array}.
   *
   * @param array a <i>nonempty</i> array of {@code long} values
   * @return the value present in {@code array} that is less than or equal to every other value in
   *     the array
   * @throws IllegalArgumentException if {@code array} is empty
   */
  public static long min(long... array) {
    long min = array[0];
    for (int i = 1; i < array.length; i++) {
      if (array[i] < min) {
        min = array[i];
      }
    }
    return min;
  }

  /**
   * Returns a big-endian representation of {@code value} in an 8-element byte array; equivalent to
   * {@code ByteBuffer.allocate(8).putLong(value).array()}. For example, the input value {@code
   * 0x1213141516171819L} would yield the byte array {@code {0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
   * 0x18, 0x19}}.
   *
   * <p>If you need to convert and concatenate several values (possibly even of different types),
   * use a shared {@link java.nio.ByteBuffer} instance, or use {@link
   * com.google.common.io.ByteStreams#newDataOutput()} to get a growable buffer.
   */
  public static byte[] toByteArray(long value) {
    // Note that this code needs to stay compatible with GWT, which has known
    // bugs when narrowing byte casts of long values occur.
    byte[] result = new byte[8];
    for (int i = 7; i >= 0; i--) {
      result[i] = (byte) (value & 0xffL);
      value >>= 8;
    }
    return result;
  }

  /**
   * Returns the {@code long} value whose big-endian representation is stored in the first 8 bytes
   * of {@code bytes}; equivalent to {@code ByteBuffer.wrap(bytes).getLong()}. For example, the
   * input byte array {@code {0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19}} would yield the
   * {@code long} value {@code 0x1213141516171819L}.
   *
   * <p>Arguably, it's preferable to use {@link java.nio.ByteBuffer}; that library exposes much more
   * flexibility at little cost in readability.
   *
   * @throws IllegalArgumentException if {@code bytes} has fewer than 8 elements
   */
  public static long fromByteArray(byte[] bytes) {
    return fromBytes(
        bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7]);
  }

  /**
   * Returns the {@code long} value whose byte representation is the given 8 bytes, in big-endian
   * order; equivalent to {@code Longs.fromByteArray(new byte[] {b1, b2, b3, b4, b5, b6, b7, b8})}.
   *
   * @since 7.0
   */
  public static long fromBytes(
      byte b1, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8) {
    return (b1 & 0xFFL) << 56
        | (b2 & 0xFFL) << 48
        | (b3 & 0xFFL) << 40
        | (b4 & 0xFFL) << 32
        | (b5 & 0xFFL) << 24
        | (b6 & 0xFFL) << 16
        | (b7 & 0xFFL) << 8
        | (b8 & 0xFFL);
  }
}
