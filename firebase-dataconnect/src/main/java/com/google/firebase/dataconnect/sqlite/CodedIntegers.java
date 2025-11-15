// Protocol Buffers - Google's data interchange format
// Copyright 2008 Google Inc.  All rights reserved.
//
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file or at
// https://developers.google.com/open-source/licenses/bsd

// This entire file was adapted from
// https://github.com/protocolbuffers/protobuf/blob/1bf47797fbe591392269c50cd83e153938450462/java/core/src/main/java/com/google/protobuf/CodedOutputStream.java
// with some unnecessary stuff removed.

package com.google.firebase.dataconnect.sqlite;

import java.nio.ByteBuffer;

final class CodedIntegers {

  private CodedIntegers() {}

  /**
   * Computes and returns the number of bytes that would be needed by {@link #putUInt32},
   * which is between 1 and 5. Smaller values consume fewer bytes.
   */
  public static int computeUInt32Size(int value) {
    /*
    This code is ported from the C++ varint implementation.
    Implementation notes:

    To calculate varint size, we want to count the number of 7 bit chunks required. Rather than using
    division by 7 to accomplish this, we use multiplication by 9/64. This has a number of important
    properties:
     * It's roughly 1/7.111111. This makes the 0 bits set case have the same value as the 7 bits set
       case, so offsetting by 1 gives us the correct value we want for integers up to 448 bits.
     * Multiplying by 9 is special. x * 9 = x << 3 + x, and so this multiplication can be done by a
       single shifted add on arm (add w0, w0, w0, lsl #3), or a single lea instruction
       (leal (%rax,%rax,8), %eax)) on x86.
     * Dividing by 64 is a 6 bit right shift.

    An explicit non-sign-extended right shift is used instead of the more obvious '/ 64' because
    that actually produces worse code on android arm64 at time of authoring because of sign
    extension. Rather than
        lsr w0, w0, #6
    It would emit:
        add w16, w0, #0x3f (63)
        cmp w0, #0x0 (0)
        csel w0, w16, w0, lt
        asr w0, w0, #6

    Summarized:
    floor(((Integer.SIZE - clz) / 7.1111) + 1
    ((Integer.SIZE - clz) * 9) / 64 + 1
    (((Integer.SIZE - clz) * 9) >>> 6) + 1
    ((Integer.SIZE - clz) * 9 + (1 << 6)) >>> 6
    (Integer.SIZE * 9 + (1 << 6) - clz * 9) >>> 6
    (352 - clz * 9) >>> 6
    on arm:
    (352 - clz - (clz << 3)) >>> 6
    on x86:
    (352 - lea(clz, clz, 8)) >>> 6

    If you make changes here, please validate their compiled output on different architectures and
    runtimes.
    */
    int clz = Integer.numberOfLeadingZeros(value);
    return ((Integer.SIZE * 9 + (1 << 6)) - (clz * 9)) >>> 6;
  }

  public static void putUInt32(int value, ByteBuffer byteBuffer) {
    byte[] buffer = byteBuffer.array();
    int offset = byteBuffer.arrayOffset();
    int position = byteBuffer.position();
    while (true) {
      position++;

      if ((value & ~0x7F) == 0) {
        buffer[offset + position] = (byte) value;
        byteBuffer.position(position);
        break;
      }

      buffer[offset + position] = (byte) (value | 0x80);
      value >>>= 7;
    }
  }

  /**
   * Computes and returns the number of bytes that would be needed by {@link #putSInt32},
   * which is between 1 and 5. Smaller absolute values consume fewer bytes. For example, numbers
   * between -128 and 127 consume only 1 byte, but {@link Integer#MAX_VALUE} consumes 5 bytes.
   */
  public static int computeSInt32Size(int value) {
    return computeUInt32Size(encodeZigZag32(value));
  }

  public static void putSInt32(int value, ByteBuffer byteBuffer) {
    putUInt32(encodeZigZag32(value), byteBuffer);
  }

  /**
   * Computes and returns the number of bytes that would be needed by {@link #putUInt64},
   * which is between 1 and 10. Smaller values consume fewer bytes.
   */
  public static int computeUInt64Size(long value) {
    int clz = Long.numberOfLeadingZeros(value);
    // See computeUInt32Size for explanation
    return ((Long.SIZE * 9 + (1 << 6)) - (clz * 9)) >>> 6;
  }

  public static void putUInt64(long value, ByteBuffer byteBuffer) {
    byte[] buffer = byteBuffer.array();
    int offset = byteBuffer.arrayOffset();
    int position = byteBuffer.position();
    while (true) {
      position++;

      if ((value & ~0x7FL) == 0) {
        buffer[offset + position] = (byte) value;
        byteBuffer.position(position);
        break;
      }

      buffer[offset + position] = (byte) ((int) value | 0x80);
      value >>>= 7;
    }
  }

  /**
   * Computes and returns the number of bytes that would be needed by {@link #putSInt64},
   * which is between 1 and 10. Smaller absolute values consume fewer bytes. For example, numbers
   * between -128 and 127 consume only 1 byte, but {@link Long#MAX_VALUE} consumes 10 bytes.
   */
  public static int computeSInt64Size(final long value) {
    return computeUInt64Size(encodeZigZag64(value));
  }

  public static void putSInt64(final long value, ByteBuffer byteBuffer) {
    putUInt64(encodeZigZag64(value), byteBuffer);
  }

  /**
   * Encode a ZigZag-encoded 32-bit value. ZigZag encodes signed integers into values that can be
   * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
   * to be varint encoded, thus always taking 10 bytes on the wire.)
   *
   * @param n A signed 32-bit integer.
   * @return An unsigned 32-bit integer, stored in a signed int because Java has no explicit
   *     unsigned support.
   */
  public static int encodeZigZag32(final int n) {
    // Note:  the right-shift must be arithmetic
    return (n << 1) ^ (n >> 31);
  }

  /**
   * Encode a ZigZag-encoded 64-bit value. ZigZag encodes signed integers into values that can be
   * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
   * to be varint encoded, thus always taking 10 bytes on the wire.)
   *
   * @param n A signed 64-bit integer.
   * @return An unsigned 64-bit integer, stored in a signed int because Java has no explicit
   *     unsigned support.
   */
  public static long encodeZigZag64(final long n) {
    // Note:  the right-shift must be arithmetic
    return (n << 1) ^ (n >> 63);
  }
}
