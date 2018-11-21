// Copyright 2018 Google LLC
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

package com.google.cloud.datastore.core.number;

import static com.google.cloud.datastore.core.number.NumberParts.SIGNIFICAND_BITS;

import java.util.Arrays;

/**
 * Combined number index format, suitable for ordering double and int64 values together.
 *
 * <p>Implementation conforms to the UTF Style Encoding section of "Number Index Entry Encoding".
 *
 * @see "https://docs.google.com/document/d/1QX32BCTFWFS_4BneQHFRDnPb2ts04fYrm4Vgy0HLSBg/edit#"
 */
public class NumberIndexEncoder {

  /**
   * The encoded representation of zero.
   *
   * <p>Positive sign, negative exponent sign, infinite exponent value.
   */
  private static final byte[] ENCODED_ZERO = new byte[] {(byte) 0x80};

  /**
   * The encoded representation of NaN (not a number), specially chosen to come before all other
   * numbers.
   *
   * <p>negative sign, positive exponent sign, infinite exponent value, NaN designator.
   */
  private static final byte[] ENCODED_NAN = new byte[] {0x00, 0x60};

  /**
   * The encoded representation of negative infinity, specially crafted to come after NaN.
   *
   * <p>negative sign, positive exponent sign, infinite exponent value, negative infinity
   * designator.
   */
  private static final byte[] ENCODED_NEGATIVE_INFINITY = new byte[] {0x00, (byte) 0x80};

  /**
   * The encoded representation of positive infinity.
   *
   * <p>Unlike IEEE 754 double precision, our encoding normalizes all NaNs to precede negative
   * infinity. As such we don't need to distinguish positive NaN and positive infinity and thus do
   * not include a designating byte that would be the complement of {@link
   * #ENCODED_NEGATIVE_INFINITY}.
   *
   * <p>Positive sign, positive exponent sign, infinite exponent value.
   */
  private static final byte[] ENCODED_POSITIVE_INFINITY = new byte[] {(byte) 0xFF};

  private static final int EXP1_END = (1 << 2);
  private static final int EXP2_END = EXP1_END + (1 << 4);
  private static final int EXP3_END = EXP2_END + (1 << 7);
  private static final int EXP4_END = EXP3_END + (1 << 10);

  /**
   * The encoder assumes the input is either a long or a double and assumes the maximum length based
   * on the largest values for each type.
   *
   * <p>11 bytes are required to render the largest longs. Long.MAX_VALUE encodes as follows:
   *
   * <ul>
   *   <li>exponent == 62 requires the st110eee form.
   *   <li>st110eee requires 2 leading bytes (into which exponent and 4 significand bits are
   *       packed).
   *   <li>The remaining 58 significand bits are packed into bytes with continuation bits.
   *   <li>ceil(58 / 7) == 9 bytes.
   * </ul>
   *
   * <p>10 bytes are required to render the largest doubles. Double.MAX_VALUE encodes as follows:
   *
   * <ul>
   *   <li>exponent == 1023 requires the st1110ee form.
   *   <li>st1110ee requires 2 bytes (into which only the exponent is packed).
   *   <li>The remaining 52 significand bits are packed into bytes with continuation bits.
   *   <li>ceil(52 / 7) == 8 bytes.
   * </ul>
   *
   * <p>Note that while subnormal values require larger exponents to encode, their reduced precision
   * makes them no larger to encode than Double.MAX_VALUE.
   */
  private static final int MAX_ENCODED_BYTES = 11;

  public static byte[] encodeDouble(double value) {
    return encode(NumberParts.fromDouble(value));
  }

  public static byte[] encodeLong(long value) {
    return encode(NumberParts.fromLong(value));
  }

  /**
   * Encodes a finite number to bytes suitable for inclusion in an index. The number is specified as
   * a set of components similar in spirit to the encoding of a double-precision number.
   *
   * @param parts the number to encode
   * @return a newly allocated byte array containing the encoded form of the number
   */
  public static byte[] encode(NumberParts parts) {
    if (parts.isZero()) {
      return copyOf(ENCODED_ZERO);
    } else if (parts.isNaN()) {
      return copyOf(ENCODED_NAN);
    } else if (parts.isInfinite()) {
      return parts.negative()
          ? copyOf(ENCODED_NEGATIVE_INFINITY)
          : copyOf(ENCODED_POSITIVE_INFINITY);
    }

    // The exponent is the exponent of the largest power of two less than or equal to the number to
    // encode. Unlike IEEE 754 double-precision, the exponent should not be biased. Only finite
    // exponents are allowed.
    int exponent = parts.exponent();
    // The bits of the fractional part of the number, left justified, with an implicit leading 1.
    long significand = parts.significand();

    byte[] buffer = new byte[MAX_ENCODED_BYTES];
    int bufferPos = 0;

    // If the number is negative then all bytes are stored as their complement. As each byte is
    // written to the buffer above its value is XORed with this inverter value.
    int inverter = parts.negative() ? 0xFF : 0;

    // In addition to the complement above, exponent values are subject to an additional complement
    // if the exponent itself is negative. Unlike inverter, within a given byte not all bits
    // are exponent-related so the inverter needs to be specified in a positionally dependent way.
    //
    // Of course the positionally dependent inverter does not apply if the exponent is non-negative
    // so mask the exponent inverter with this value before XORing.
    int exponentMask = 0;
    if (exponent < 0) {
      exponent = -exponent;
      exponentMask = 0xFF;
    }

    // Taken together, appending a byte containing exponent bits takes the following form:
    //
    //   lastByte ^= (exponentInverter & exponentMask) ^ inverter
    //
    // Note that if the trailing byte with an exponent contains a continuation bit the last
    // inversion is deferred until the significand encoding loop completes.

    int lastByte;
    if (exponent < EXP1_END) {
      // st00 001C => exponent 0
      // st00 01mC => exponent 1
      // st00 1mmC => exponent 2
      // st01 mmmC => exponent 3
      //
      // All these cases encode exactly one exponent value, in order to pack significand bits into
      // the leading byte.

      // Start with sign bits (assuming positive number and positive exponent).
      lastByte = 0xC0;

      // Compute a magnitude marker
      int significandStart = exponent + 1;
      lastByte |= 1 << significandStart;

      // The number of bits of significand is equal to the exponent. The significand bits are also
      // shifted one left to make room for the continuation bit. Note the trailing -2 here both
      // creates the mask of all bits from significandStart down and clears the trailing bit.
      int significandMask = (1 << significandStart) - 2;
      lastByte |= (int) (significand >>> (SIGNIFICAND_BITS - significandStart)) & significandMask;
      significand <<= exponent;

      // Compute the inverter for the exponent (note exponent is no longer negative by this point
      // so exponentMask is the only indicator that the exponent was negative).
      if (exponentMask != 0) {
        // Excluding the sign bit, everything up to the start of the significand (including
        // continuation bit) is an exponent bit.
        int exponentInverter = (-1 << significandStart) & 0x7E;
        lastByte ^= exponentInverter;
      }

    } else if (exponent < EXP2_END) {
      // st10 eeee mmmm mmmC
      lastByte = 0xE0; // 1110 0000: magnitude marker

      // Store exponents [4, 19] biased as [0, 15]
      exponent -= EXP1_END;
      assert exponent <= 0xF;

      // Store exponent as low order 4 bits, no continuation bit
      lastByte |= exponent;
      lastByte ^= (0x7F & exponentMask) ^ inverter;
      buffer[bufferPos++] = (byte) lastByte;

      // No continuation bit in the prior byte means a trailing significand byte must exist,
      // regardless of value (even all zero).
      lastByte = topSignificandByte(significand);
      significand <<= 7;

    } else if (exponent < EXP3_END) {
      // st11 0eee eeee mmmm mmmm mmmC
      lastByte = 0xF0; // 1111 0000: magnitude marker

      // Store exponents [20, 147] biased as [0, 127]
      exponent -= EXP2_END;
      assert exponent <= 0x7F;

      // Pack the top 3 bits of the 7 bit exponent as the low order bits
      lastByte |= exponent >>> 4;
      lastByte ^= (0x7F & exponentMask) ^ inverter;
      buffer[bufferPos++] = (byte) lastByte;

      // Bottom 4 exponent bits get packed into the high bits second byte
      lastByte = (exponent << 4) & 0xF0;

      // No continuation bit, so consume 4 bits directly
      lastByte |= (int) (significand >>> (SIGNIFICAND_BITS - 4));
      significand <<= 4;

      lastByte ^= (0xF0 & exponentMask) ^ inverter;
      buffer[bufferPos++] = (byte) lastByte;

      // No continuation bit in the prior byte means a trailing significand byte must exist,
      // regardless of value (even all zero).
      lastByte = topSignificandByte(significand);
      significand <<= 7;

    } else if (exponent < EXP4_END) {
      // st11 10ee eeee eeee mmmm mmmC
      lastByte = 0xF8; // 1111 1000: magnitude marker

      // Store exponents [148, 1171] biased as [0, 1023]
      exponent -= EXP3_END;
      assert exponent <= 0x3FF;

      // Pack the top 2 bits of the exponent as the low order bits
      lastByte |= exponent >>> 8;
      lastByte ^= (0x7F & exponentMask) ^ inverter;
      buffer[bufferPos++] = (byte) lastByte;

      // Bottom 8 exponent bits taken directly, with no continuation bit
      lastByte = exponent & 0xFF;
      lastByte ^= (0xFF & exponentMask) ^ inverter;
      buffer[bufferPos++] = (byte) lastByte;

      // No continuation bit in the prior byte means a trailing significand byte must exist,
      // regardless of value (even all zero).
      lastByte = topSignificandByte(significand);
      significand <<= 7;

    } else {
      throw new IllegalStateException("unimplemented");
    }

    while (significand != 0) {
      // Append a continuation bit to the byte.
      lastByte |= 1;
      lastByte ^= inverter;
      buffer[bufferPos++] = (byte) lastByte;

      lastByte = topSignificandByte(significand);
      significand <<= 7;
    }

    lastByte ^= inverter;
    buffer[bufferPos++] = (byte) lastByte;

    // resultPos is now the length of the result array
    return Arrays.copyOf(buffer, bufferPos);
  }

  public static double decodeDouble(byte[] bytes) {
    return decode(bytes).parts().asDouble();
  }

  public static long decodeLong(byte[] bytes) {
    return decode(bytes).parts().asLong();
  }

  /** The results of decoding {@link NumberParts} from a byte array. */
  public static final class DecodedNumberParts {

    private final int bytesRead;
    private final NumberParts parts;

    private DecodedNumberParts(int bytesRead, NumberParts parts) {
      this.bytesRead = bytesRead;
      this.parts = parts;
    }

    /** The number of bytes that were read to decode the number parts. */
    public int bytesRead() {
      return bytesRead;
    }

    /** The {@link NumberParts} instance that was decoded. */
    public NumberParts parts() {
      return parts;
    }

    static DecodedNumberParts create(int bytesRead, NumberParts parts) {
      return new DecodedNumberParts(bytesRead, parts);
    }
  }

  public static DecodedNumberParts decode(byte[] bytes) {
    int bufferPos = 0;

    if (bytes.length < 1) {
      throw new IllegalArgumentException("Invalid encoded byte array");
    }

    int b = bytes[bufferPos++] & 0xFF;

    // Determine if the leading byte is negative, and if so, invert it.
    boolean negative = (b & 0x80) == 0;
    int inverter = negative ? 0xFF : 0;
    b ^= inverter;

    // Determine if the exponent is negative too, don't invert again though because this could
    // mangle any significand bits in the leading byte.
    boolean exponentNegative = (b & 0x40) == 0;
    int exponentInverter = exponentNegative ? 0xFF : 0;

    int exponent;
    long significand = 0L;

    // The position in the significand to write the next bit
    int writeBit = SIGNIFICAND_BITS;

    int marker = decodeMarker(b ^ exponentInverter);
    switch (marker) {
      case -4:
        // st00001C => exponent 0
        if (exponentNegative) {
          throw new IllegalArgumentException(
              "Invalid encoded number "
                  + Arrays.toString(bytes)
                  + ": exponent negative zero is invalid");
        }
        exponent = 0;
        break;

      case -3: // Falls through
      case -2: // Falls through
      case -1:
        // st0001mC => exponent 1
        // st001mmC => exponent 2
        // st01mmmC => exponent 3
        //
        // All these cases have a fixed exponent value and pack significand bits into the leading
        // byte.
        exponent = EXP1_END + marker;
        writeBit -= exponent;

        // The number of bits of significand is equal to the exponent. The significand bits are also
        // shifted one left to make room for the continuation bit.
        int significandStart = exponent + 1;
        int significandMask = ~(-1 << significandStart) & 0x7E;
        significand |= ((long) (b & significandMask)) << (writeBit - 1);
        break;

      case 1:
        // st10 eeee mmmm mmmC
        if (bytes.length < 2) {
          throw new IllegalArgumentException("Invalid encoded byte array");
        }

        // Exponent is stored in the low-order 4 bits, with bias
        exponent = (b ^ exponentInverter) & 0x0F;
        exponent += EXP1_END;

        b = (bytes[bufferPos++] & 0xFF) ^ inverter;
        writeBit -= 7;
        significand |= decodeTrailingSignificandByte(b, writeBit);
        break;

      case 2:
        // st11 0eee eeee mmmm mmmm mmmC
        if (bytes.length < 3) {
          throw new IllegalArgumentException("Invalid encoded byte array");
        }

        // 7 bit exponent. bits 6-4 come from the leading byte
        exponent = ((b ^ exponentInverter) & 0x07) << 4;

        // bits 3-0 come from the next byte, then undo bias
        b = (bytes[bufferPos++] & 0xFF) ^ inverter;
        exponent |= (b ^ exponentInverter) >>> 4;
        exponent += EXP2_END;

        // 4 bits of significand in the low-order four bits of the second byte
        writeBit -= 4;
        significand |= ((long) b & 0x0F) << writeBit;

        // And a forced third byte with continuation bit
        b = (bytes[bufferPos++] & 0xFF) ^ inverter;
        writeBit -= 7;
        significand |= decodeTrailingSignificandByte(b, writeBit);
        break;

      case 3:
        // st11 10ee eeee eeee mmmm mmmC
        if (bytes.length < 3) {
          throw new IllegalArgumentException("Invalid encoded byte array");
        }

        // 10 bit exponent. Bits 9-8 come from the leading byte
        exponent = ((b ^ exponentInverter) & 0x03) << 8;

        // bits 7-0 come from the next byte, then undo bias
        b = (bytes[bufferPos++] & 0xFF) ^ inverter;
        exponent |= b ^ exponentInverter;
        exponent += EXP3_END;

        // And a forced third byte with continuation bit
        b = (bytes[bufferPos++] & 0xFF) ^ inverter;
        writeBit -= 7;
        significand |= decodeTrailingSignificandByte(b, writeBit);
        break;

      case 6:
        // st11 1111
        // Special cases where exponent is infinite
        NumberParts parts;
        if (negative) {
          if (exponentNegative) {
            // negative zero.
            parts = NumberParts.create(true, Integer.MIN_VALUE, 0);

          } else {
            // Need another byte to distinguish NaN from -Infinity
            if (bytes.length < 2) {
              throw new IllegalArgumentException("Invalid encoded byte array");
            }
            // These special values are defined in terms of the complemented bytes so don't invert
            // to make the comparisons
            b = (bytes[bufferPos++] & 0xFF);
            if (b == 0x80) {
              // -Infinity
              parts = NumberParts.create(true, NumberParts.POSITIVE_INFINITE_EXPONENT, 0);
            } else if (b == 0x60) {
              // NaN
              parts = NumberParts.create(true, NumberParts.POSITIVE_INFINITE_EXPONENT, 1);
            } else {
              throw new IllegalArgumentException("Invalid encoded byte array");
            }
          }
        } else {
          if (exponentNegative) {
            // zero: positive number, negative infinite exponent
            parts = NumberParts.create(false, NumberParts.NEGATIVE_INFINITE_EXPONENT, 0);
          } else {
            // Positive infinity
            parts = NumberParts.create(false, NumberParts.POSITIVE_INFINITE_EXPONENT, 0);
          }
        }
        return DecodedNumberParts.create(bufferPos, parts);

      default:
        // Several cases are intentionally unimplemented:
        //   * case 0 is impossible
        //   * cases -6 and -5 make no sense because we need a continuation bit to express the
        //     presence or absence of significand bits for small exponent values.
        //   * cases 4 and 5 are reserved for expansion (possibly to quad precision)
        throw new IllegalArgumentException("Invalid encoded byte array");
    }

    while ((b & 1) != 0) {
      // Continuation bit present; examine the next byte
      if (bufferPos >= bytes.length) {
        throw new IllegalArgumentException("Invalid encoded byte array");
      }
      b = (bytes[bufferPos++] & 0xFF) ^ inverter;

      writeBit -= 7;
      if (writeBit >= 0) {
        significand |= decodeTrailingSignificandByte(b, writeBit);
      } else {
        significand |= ((long) (b & 0xFE)) >>> -(writeBit - 1);
        writeBit = 0;

        // The write position passing zero should only happen on the final byte in the sequence
        // (and only happens because the final few low-order bits of significand come from the
        // high order of an encoded significand byte.
        if ((b & 1) != 0) {
          throw new IllegalArgumentException("Invalid encoded byte array: overlong sequence");
        }
      }
    }

    if (exponentNegative) {
      exponent = -exponent;
    }

    return DecodedNumberParts.create(
        bufferPos, NumberParts.create(negative, exponent, significand));
  }

  /**
   * Returns the type of marker is present in the leading byte. The markers dictate how to decode
   * the remainder of the bytes in the value.
   *
   * <p>The value returned is the number of repeated elements, ignoring the sign bits. A positive
   * result means that number of repeated 1 values (before encountering a 0). A negative result
   * means that number of 0 values (before encountering a 1) after negation.
   *
   * <p>For example (only six bits because the two leading bits are sign bits):
   *
   * <ul>
   *   <li>111111 => 6
   *   <li>111110 => 5
   *   <li>111100 => 4
   *   <li>111000 => 3
   *   <li>110000 => 2
   *   <li>100000 => 1
   *   <li>010000 => -1
   *   <li>001000 => -2
   *   <li>000100 => -3
   *   <li>000010 => -4
   *   <li>000001 => -5
   *   <li>000000 => -6
   * </ul>
   *
   * <p>Note that this does not include a zero value because the highest order bit is used to choose
   * which unary coding to decode. Therefore the sequence encountered is always at least one long.
   */
  static int decodeMarker(int byteValue) {
    // First two bits are signs, ignore them. Extract the leading bit to determine which unary
    // coding to use (leading 0s indicate exponents 0-3, leading 1s indicate exponents >= 4).
    boolean leadingOne = (byteValue & 0x20) != 0;

    if (leadingOne) {
      // Invert the byte so that we can count zeros
      byteValue ^= 0xFF;
    }

    // Mask away the sign bits that don't matter
    byteValue &= 0x3F;

    // Compute the floor of the log (base 2) of the masked byte value. The maximum value would be
    //   byteValue == 0x3F (63)
    //   log2(63) == 5.977
    //   floor(log2(63)) == 5
    int log2 = (Integer.SIZE - 1) - Integer.numberOfLeadingZeros(byteValue);
    int leader = 5 - log2;
    return leadingOne ? leader : -leader;
  }

  private static long decodeTrailingSignificandByte(int value, int position) {
    return ((long) (value & 0xFE)) << (position - 1);
  }

  private static int topSignificandByte(long significand) {
    return ((int) (significand >>> (SIGNIFICAND_BITS - 8))) & 0xFE;
  }

  private static byte[] copyOf(byte[] value) {
    return value.clone();
  }
}
