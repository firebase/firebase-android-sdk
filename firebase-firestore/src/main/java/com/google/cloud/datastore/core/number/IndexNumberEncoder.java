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

/**
 * Encodes numbers (longs and doubles) as bytes whose order matches the numeric order.
 *
 * <p>Implementation conforms to the UTF Style Encoding section of "Number Index Entry Encoding".
 *
 * @see "https://docs.google.com/document/d/1QX32BCTFWFS_4BneQHFRDnPb2ts04fYrm4Vgy0HLSBg/edit#"
 */
public class IndexNumberEncoder {

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
  public static final int MAX_ENCODED_BYTES = 11;

  // These package visible constants are also used by class IndexNumberDecoder.

  @SuppressWarnings("NumericOverflow")
  static final long DOUBLE_SIGN_BIT = 1L << (Double.SIZE - 1);

  /** Double precision stores the exponent in an offset binary form, with this bias. */
  static final int DOUBLE_EXPONENT_BIAS = 1023;

  /**
   * The minimum value of the exponent of a normal double-precision number (after removing bias).
   * -1023 is reserved for zero and a subnormal indicator.
   */
  static final int DOUBLE_MIN_EXPONENT = -1022;

  /**
   * The number of explicitly stored significand bits in IEEE 754 double precision format. This does
   * not count the implied leading significand bit.
   */
  static final int DOUBLE_SIGNIFICAND_BITS = 52;

  static final int SIGNIFICAND_BITS = 64;

  static final int POSITIVE_INFINITE_EXPONENT = Integer.MAX_VALUE;
  static final int NEGATIVE_INFINITE_EXPONENT = Integer.MIN_VALUE;

  static final int EXP1_END = (1 << 2);
  static final int EXP2_END = EXP1_END + (1 << 4);
  static final int EXP3_END = EXP2_END + (1 << 7);
  static final int EXP4_END = EXP3_END + (1 << 10);

  private IndexNumberEncoder() {}

  /**
   * Writes bytes encoding the long to the buffer at the offset, in the order specified by
   * descending, and returns the number of bytes written.
   *
   * @param descending when true, produce an encoding that orders numbers in descending order
   * @param value the long to encode
   * @param buffer buffer for bytes to write the encoded bytes
   * @param offset index into buffer of first byte to write
   * @return number of bytes written to buffer
   */
  public static int encodeLong(boolean descending, long value, byte[] buffer, int offset) {
    if (value == 0) {
      return encodeZero(buffer, offset);
    }

    boolean negative = descending;
    if (value < 0) {
      // Note that Long.MIN_VALUE does not need special handling here, despite the fact that
      // -Long.MIN_VALUE doesn't actually negate the value.
      //
      // This works because Long.MIN_VALUE, when reinterpreted as an unsigned long happens to have
      // the right bit pattern to represent itself.
      //
      // For all other negative values, negate them so that the encoder only has to deal with
      // positive values.
      negative = !negative;
      value = -value;
    }

    int leadingZeros = Long.numberOfLeadingZeros(value);
    int exponent = SIGNIFICAND_BITS - 1 - leadingZeros;

    // Hide the leading bit
    long significand = value & ~(1L << exponent);

    // Left-justify into the leading bits of the value. This removes both the leading zeros and the
    // most significant 1 bit, making the significand suitable for encoding
    significand <<= leadingZeros + 1;

    return encodeNumber(negative, exponent, significand, buffer, offset);
  }

  /**
   * Writes bytes encoding the double to the buffer at the offset, in the order specified by
   * descending, and returns the number of bytes written.
   *
   * <p>Warning! When descending is true, NaN is encoded as (still) smaller than all other numbers.
   * See TODO in this file.
   *
   * @param descending when true, produce an encoding that orders numbers in descending order
   * @param value the double to encode
   * @param buffer buffer for bytes to write the encoded bytes
   * @param offset index into buffer of first byte to write
   * @return number of bytes written to buffer
   */
  public static int encodeDouble(boolean descending, double value, byte[] buffer, int offset) {
    if (value == 0.0) {
      // Note -0.0 is changed to +0.0 here.
      return encodeZero(buffer, offset);
    }

    // Convert long bits where all NaNs have been normalized 0x7FFC000000000000L.
    long doubleBits = Double.doubleToLongBits(value);

    // Encoding represents negative numbers and positive numbers encoded in descending order
    // identically. This boolean specifies the intended encoding direction.
    boolean invertEncoding = (value < 0.0) ^ descending;
    int exponent = (int) ((doubleBits >>> DOUBLE_SIGNIFICAND_BITS) & 0x7FF) - DOUBLE_EXPONENT_BIAS;
    long significand = doubleBits & ~(-1L << DOUBLE_SIGNIFICAND_BITS);

    // Special cases:
    if (exponent < DOUBLE_MIN_EXPONENT) {
      // Subnormal value; normalize. This is possible because the encoding can represent exponents
      // much smaller (more negative) than are possible even with subnormals.
      int leadingZeros = Long.numberOfLeadingZeros(significand);
      int binaryExponent = SIGNIFICAND_BITS - 1 - leadingZeros;

      // Mask out the highest bit to convert to implicit leading 1 form
      significand &= ~(1L << binaryExponent);

      // As with longs above, left justify
      significand <<= leadingZeros + 1;

      // Adjust the exponent to reflect the shift required to get to normal form.
      int adjustment = leadingZeros - (SIGNIFICAND_BITS - DOUBLE_SIGNIFICAND_BITS);
      exponent -= adjustment;
    } else if (exponent > DOUBLE_EXPONENT_BIAS) {
      // TODO: NaN is handled incorrectly.
      // NaN always comes first, which is incorrect for descending.
      // The correct solution would use 4 different 2-byte encodings for +Infinity, -Infinity, and
      // 2 NaNs, one ascending and one descending.  The encodings would be ordered so:
      //    ascending NaN
      //    -Infinity (effectively ascending -Infinity and descending +Infinity)
      //    regular numbers (negated when descending -- but beware Long.MIN_VALUE)
      //    +Infinity (effectively ascending +Infinity and descending -Infinity)
      //    descending NaN
      // Alternately, another fix would be to reject NaN values, and use 1-byte encodings for
      // +Infinity and -Infinity.  NaN would be handled by higher level Firestore index encoding,
      // as it currently is, which is why this issue does not affect Firestore.
      // But either fix would require changing existing Firestore indexes.
      if (significand == 0) {
        if (invertEncoding) {
          // The number is -Infinity.
          // Encode the 2 byte representation of -Infinity, specially crafted to come after NaN.
          // Negative sign, positive exponent sign, infinite exponent value,
          // negative infinity designator.
          buffer[offset++] = (byte) 0x00;
          buffer[offset++] = (byte) 0x80;
          return 2;
        } else {
          // The number is +Infinity.
          // Encode the 1 byte representation of +Infinity.
          // Positive sign, positive exponent sign, infinite exponent value.
          buffer[offset++] = (byte) 0xFF;
          return 1;
        }
      } else {
        // The number is NaN.  (There are several.)
        // Encode the 2 byte representation of the canonical NaN, specially chosen to come before
        // every other number's byte representation for both the ascending and descending encodings.
        // (Despite that being unhelpful for the descending encoding -- see note above.)
        // Negative sign, positive exponent sign, infinite exponent value, NaN designator.
        buffer[offset++] = (byte) 0x00;
        buffer[offset++] = (byte) 0x60;
        return 2;
      }
    } else {
      // The main encode loop takes a significand whose values have a hidden leading bit and are
      // left-justified (i.e. MSB of the significand guaranteed to be in the MSB of a long). Since
      // doubles only have 52 bits of significand (excluding hidden bit), a left shift is required.
      significand <<= SIGNIFICAND_BITS - DOUBLE_SIGNIFICAND_BITS;
    }

    return encodeNumber(invertEncoding, exponent, significand, buffer, offset);
  }

  // Returns the number of bytes encoded.
  private static int encodeZero(byte[] buffer, int offset) {
    // The number is 0, 0.0, or -0.0.
    // Encode the byte representation of zero.
    // Positive sign, negative exponent sign, infinite exponent value.
    buffer[offset] = (byte) 0x80;
    return 1;
  }

  // The number must not be zero, +Infinity, -Infinity, or any NaN.  It may be subnormal.
  // Returns the number of bytes encoded.
  private static int encodeNumber(
      boolean invertEncoding, int exponent, long significand, byte[] buffer, int offset) {
    int bufferPos = offset;

    // If the encoding is inverted then all bytes are stored as their complement. As each byte is
    // written to the buffer above its value is XORed with this inverter value.
    int inverter = invertEncoding ? 0xFF : 0;

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

    return bufferPos - offset;
  }

  private static int topSignificandByte(long significand) {
    return ((int) (significand >>> (SIGNIFICAND_BITS - 8))) & 0xFE;
  }
}
