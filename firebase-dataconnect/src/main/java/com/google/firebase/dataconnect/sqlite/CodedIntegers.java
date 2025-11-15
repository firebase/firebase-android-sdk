// Protocol Buffers - Google's data interchange format
// Copyright 2008 Google Inc.  All rights reserved.
//
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file or at
// https://developers.google.com/open-source/licenses/bsd

// This entire file was adapted from
// https://github.com/protocolbuffers/protobuf/blob/1bf47797fbe591392269c50cd83e153938450462/java/core/src/main/java/com/google/protobuf/CodedOutputStream.java
// and
// https://github.com/protocolbuffers/protobuf/blob/1bf47797fbe591392269c50cd83e153938450462/java/core/src/main/java/com/google/protobuf/CodedInputStream.java

package com.google.firebase.dataconnect.sqlite;

import java.nio.ByteBuffer;

final class CodedIntegers {

  private CodedIntegers() {}

  public static final int MAX_VARINT32_SIZE = 5;
  public static final int MAX_VARINT64_SIZE = 10;

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
    int pos = byteBuffer.position() + offset;
    while (true) {
      if ((value & ~0x7F) == 0) {
        buffer[pos++] = (byte) value;
        break;
      }
      buffer[pos++] = (byte) (value | 0x80);
      value >>>= 7;
    }
    byteBuffer.position(pos - offset);
  }

  public static int getUInt32(ByteBuffer byteBuffer) {
    return getRawVarint32(byteBuffer);
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

  public static int getSInt32(ByteBuffer byteBuffer) {
    return decodeZigZag32(getRawVarint32(byteBuffer));
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
    int pos = byteBuffer.position() + offset;
    while (true) {
      if ((value & ~0x7FL) == 0) {
        buffer[pos++] = (byte) value;
        break;
      }
      buffer[pos++] = (byte) ((int) value | 0x80);
      value >>>= 7;
    }
    byteBuffer.position(pos);
  }

  public static long getUInt64(ByteBuffer byteBuffer) {
    return getRawVarint64(byteBuffer);
  }

  /**
   * Computes and returns the number of bytes that would be needed by {@link #putSInt64},
   * which is between 1 and 10. Smaller absolute values consume fewer bytes. For example, numbers
   * between -128 and 127 consume only 1 byte, but {@link Long#MAX_VALUE} consumes 10 bytes.
   */
  public static int computeSInt64Size(long value) {
    return computeUInt64Size(encodeZigZag64(value));
  }

  public static void putSInt64(long value, ByteBuffer byteBuffer) {
    putUInt64(encodeZigZag64(value), byteBuffer);
  }

  public static long getSInt64(ByteBuffer byteBuffer) {
    return decodeZigZag64(getUInt64(byteBuffer));
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
   * Decode a ZigZag-encoded 32-bit value. ZigZag encodes signed integers into values that can be
   * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
   * to be varint encoded, thus always taking 10 bytes on the wire.)
   *
   * @param n An unsigned 32-bit integer, stored in a signed int because Java has no explicit
   *     unsigned support.
   * @return A signed 32-bit integer.
   */
  public static int decodeZigZag32(final int n) {
    return (n >>> 1) ^ -(n & 1);
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
  public static long encodeZigZag64(long n) {
    // Note:  the right-shift must be arithmetic
    return (n << 1) ^ (n >> 63);
  }

  /**
   * Decode a ZigZag-encoded 64-bit value. ZigZag encodes signed integers into values that can be
   * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
   * to be varint encoded, thus always taking 10 bytes on the wire.)
   *
   * @param n An unsigned 64-bit integer, stored in a signed int because Java has no explicit
   *     unsigned support.
   * @return A signed 64-bit integer.
   */
  public static long decodeZigZag64(long n) {
    return (n >>> 1) ^ -(n & 1);
  }

  private static int getRawVarint32(ByteBuffer byteBuffer) {
    final byte[] buffer = byteBuffer.array();
    final int offset = byteBuffer.arrayOffset();
    final int pos = offset + byteBuffer.position();
    final int limit = offset + byteBuffer.limit();

    // See implementation notes for readRawVarint64
    fastpath:
    {
      int tempPos = pos;

      if (limit == tempPos) {
        break fastpath;
      }

      int x;
      if ((x = buffer[tempPos++]) >= 0) {
        byteBuffer.position(tempPos - offset);
        return x;
      } else if (limit - tempPos < 9) {
        break fastpath;
      } else if ((x ^= (buffer[tempPos++] << 7)) < 0) {
        x ^= (~0 << 7);
      } else if ((x ^= (buffer[tempPos++] << 14)) >= 0) {
        x ^= (~0 << 7) ^ (~0 << 14);
      } else if ((x ^= (buffer[tempPos++] << 21)) < 0) {
        x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
      } else {
        int y = buffer[tempPos++];
        x ^= y << 28;
        x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
        if (y < 0
            && buffer[tempPos++] < 0
            && buffer[tempPos++] < 0
            && buffer[tempPos++] < 0
            && buffer[tempPos++] < 0
            && buffer[tempPos++] < 0) {
          break fastpath; // Will throw malformedVarint()
        }
      }
      byteBuffer.position(tempPos - offset);
      return x;
    }
    return (int) getRawVarint64SlowPath(byteBuffer);
  }

  private static long getRawVarint64(ByteBuffer byteBuffer) {
    // Implementation notes:
    //
    // Optimized for one-byte values, expected to be common.
    // The particular code below was selected from various candidates
    // empirically, by winning VarintBenchmark.
    //
    // Sign extension of (signed) Java bytes is usually a nuisance, but
    // we exploit it here to more easily obtain the sign of bytes read.
    // Instead of cleaning up the sign extension bits by masking eagerly,
    // we delay until we find the final (positive) byte, when we clear all
    // accumulated bits with one xor.  We depend on javac to constant fold.

    final byte[] buffer = byteBuffer.array();
    final int offset = byteBuffer.arrayOffset();
    final int pos = offset + byteBuffer.position();
    final int limit = offset + byteBuffer.limit();

    fastpath:
    {
      int tempPos = pos;

      if (limit == tempPos) {
        break fastpath;
      }

      long x;
      int y;
      if ((y = buffer[tempPos++]) >= 0) {
        byteBuffer.position(tempPos - offset);
        return y;
      } else if (limit - tempPos < 9) {
        break fastpath;
      } else if ((y ^= (buffer[tempPos++] << 7)) < 0) {
        x = y ^ (~0 << 7);
      } else if ((y ^= (buffer[tempPos++] << 14)) >= 0) {
        x = y ^ ((~0 << 7) ^ (~0 << 14));
      } else if ((y ^= (buffer[tempPos++] << 21)) < 0) {
        x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
      } else if ((x = y ^ ((long) buffer[tempPos++] << 28)) >= 0L) {
        x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
      } else if ((x ^= ((long) buffer[tempPos++] << 35)) < 0L) {
        x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
      } else if ((x ^= ((long) buffer[tempPos++] << 42)) >= 0L) {
        x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
      } else if ((x ^= ((long) buffer[tempPos++] << 49)) < 0L) {
        x ^=
            (~0L << 7)
                ^ (~0L << 14)
                ^ (~0L << 21)
                ^ (~0L << 28)
                ^ (~0L << 35)
                ^ (~0L << 42)
                ^ (~0L << 49);
      } else if ((x ^= ((long) buffer[tempPos++] << 56)) >= 0L) {
        x ^=
            (~0L << 7)
                ^ (~0L << 14)
                ^ (~0L << 21)
                ^ (~0L << 28)
                ^ (~0L << 35)
                ^ (~0L << 42)
                ^ (~0L << 49)
                ^ (~0L << 56);
      } else if ((x ^= ((long) buffer[tempPos++] << 63)) >= 0L) {
        x ^=
            (~0L << 7)
                ^ (~0L << 14)
                ^ (~0L << 21)
                ^ (~0L << 28)
                ^ (~0L << 35)
                ^ (~0L << 42)
                ^ (~0L << 49)
                ^ (~0L << 56)
                ^ (~0L << 63);
      } else {
        break fastpath; // Will throw malformedVarint()
      }
      byteBuffer.position(tempPos - offset);
      return x;
    }
    return getRawVarint64SlowPath(byteBuffer);
  }

  private static long getRawVarint64SlowPath(ByteBuffer byteBuffer) {
    long result = 0;
    for (int shift = 0; shift < 64; shift += 7) {
      final byte b = byteBuffer.get();
      result |= (long) (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        return result;
      }
    }
    throw new MalformedVarintException();
  }

  public static final class MalformedVarintException extends RuntimeException {}
}
