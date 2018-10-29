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

/** Decoder for {@link IndexNumberEncoder}. Not thread-safe. */
public class IndexNumberDecoder {

  private boolean resultNegative;
  private int resultExponent;
  private long resultSignificand;
  /** Null indicates unknown. "" indicates no problem. */
  private String longResultRepProblemMessage;
  /** Null indicates unknown. "" indicates no problem. */
  private String doubleResultRepProblemMessage;
  /** Meaningless if longResultRepProblemMessage is not "". */
  private long resultAsLong;
  /** Meaningless if doubleResultRepProblemMessage is not "". */
  private double resultAsDouble;

  public IndexNumberDecoder() {
    reset();
  }

  /** Reset the decoder. Erases the result of the last call to {@link #decode}. */
  public void reset() {
    String noBytesDecodedMessage = "No bytes decoded.";
    longResultRepProblemMessage = noBytesDecodedMessage;
    doubleResultRepProblemMessage = noBytesDecodedMessage;
  }

  /** Returns whether the decoded number is representable as a long. */
  public boolean isResultLong() {
    updateResultLongState();
    return longResultRepProblemMessage.isEmpty();
  }

  /** Returns the decoded number as a long, or throws IllegalArgumentException if not possible. */
  public long resultAsLong() {
    updateResultLongState();
    if (!longResultRepProblemMessage.isEmpty()) {
      throw new IllegalArgumentException(longResultRepProblemMessage);
    }
    return resultAsLong;
  }

  /** Returns whether the decoded number is representable as a double. */
  public boolean isResultDouble() {
    updateResultDoubleState();
    return doubleResultRepProblemMessage.isEmpty();
  }

  private void updateResultLongState() {
    if (longResultRepProblemMessage != null) {
      return;
    }
    String resultNotInteger = "Number is not an integer.";
    String resultOutOfRange = "Number is outside the long range.";
    longResultRepProblemMessage = ""; // Assume no problem until one is found.
    if (resultExponent == IndexNumberEncoder.POSITIVE_INFINITE_EXPONENT) {
      if (resultSignificand == 0) {
        if (resultNegative) {
          longResultRepProblemMessage = "+Infinity is not an integer.";
        } else {
          longResultRepProblemMessage = "-Infinity is not an integer.";
        }
      } else {
        longResultRepProblemMessage = "NaN is not an integer.";
      }
      return;
    }
    if ((resultExponent == IndexNumberEncoder.NEGATIVE_INFINITE_EXPONENT)
        && (resultSignificand == 0)) {
      resultAsLong = 0;
      return;
    }
    if (resultExponent < 0) {
      longResultRepProblemMessage = resultNotInteger;
    } else if (resultExponent >= IndexNumberEncoder.SIGNIFICAND_BITS) {
      longResultRepProblemMessage = resultOutOfRange;
    } else if (resultExponent == (IndexNumberEncoder.SIGNIFICAND_BITS - 1)) {
      // The only legit long value with an exponent of 63 is Long.MIN_VALUE.
      if ((resultSignificand == 0) && resultNegative) {
        resultAsLong = Long.MIN_VALUE;
      } else {
        longResultRepProblemMessage = resultOutOfRange;
      }
    } else {
      int numSignicandBits =
          IndexNumberEncoder.SIGNIFICAND_BITS - Long.numberOfTrailingZeros(resultSignificand);
      // Check if the number contains a fractional part.
      if (resultExponent < numSignicandBits) {
        longResultRepProblemMessage = resultNotInteger;
      } else {
        // Compute resultAsLong.
        long longValue = resultSignificand;
        int leadingZeros = (IndexNumberEncoder.SIGNIFICAND_BITS - 1) - resultExponent;
        longValue >>>= leadingZeros + 1;

        // Unhide the leading bit
        longValue ^= (1L << resultExponent);

        // Re-apply sign
        if (resultNegative) {
          longValue = -longValue;
        }
        resultAsLong = longValue;
      }
    }
  }

  /** Returns the decoded number as a double, or throws IllegalArgumentException if not possible. */
  public double resultAsDouble() {
    updateResultDoubleState();
    if (!doubleResultRepProblemMessage.isEmpty()) {
      throw new IllegalArgumentException(doubleResultRepProblemMessage);
    }
    return resultAsDouble;
  }

  private void updateResultDoubleState() {
    if (doubleResultRepProblemMessage != null) {
      return;
    }
    doubleResultRepProblemMessage = ""; // Assume no problem until one is found.
    if (resultExponent == IndexNumberEncoder.POSITIVE_INFINITE_EXPONENT) {
      if (resultSignificand == 0) {
        if (resultNegative) {
          resultAsDouble = Double.NEGATIVE_INFINITY;
        } else {
          resultAsDouble = Double.POSITIVE_INFINITY;
        }
      } else {
        resultAsDouble = Double.NaN;
      }
      return;
    }
    if ((resultExponent == IndexNumberEncoder.NEGATIVE_INFINITE_EXPONENT)
        && (resultSignificand == 0)) {
      // Ignore negative and thus never produce -0.0.
      // The encoder does not generate this representation.
      resultAsDouble = 0.0;
      return;
    }
    int numSignicandBits =
        IndexNumberEncoder.SIGNIFICAND_BITS - Long.numberOfTrailingZeros(resultSignificand);
    if (numSignicandBits > IndexNumberEncoder.DOUBLE_SIGNIFICAND_BITS) {
      doubleResultRepProblemMessage = "Number has too many significant bits for a double.";
      return;
    }
    // Compute resultAsDouble.
    // Move significand bits down to their expected location.
    resultSignificand >>>=
        (IndexNumberEncoder.SIGNIFICAND_BITS - IndexNumberEncoder.DOUBLE_SIGNIFICAND_BITS);
    // TODO: Handle too large and too small exponents.  Not possible with current encoder.
    if (resultExponent >= Double.MIN_EXPONENT) {
      // Normal; re-add bias
      resultExponent += IndexNumberEncoder.DOUBLE_EXPONENT_BIAS;
    } else {
      // Subnormal
      int adjustment = Double.MIN_EXPONENT - resultExponent;
      long unadjustedSignificand = resultSignificand;
      resultSignificand >>>= adjustment;
      if ((resultSignificand << adjustment) != unadjustedSignificand) {
        // TODO: Test.  Not possible with current encoder.
        doubleResultRepProblemMessage =
            "Number has too many significant bits for a subnormal double.";
      }
      // Re-add explicit leading 1
      resultSignificand |= 1L << (IndexNumberEncoder.DOUBLE_SIGNIFICAND_BITS - adjustment);
      resultExponent = 0;
    }
    long doubleValueAsLong = resultSignificand;
    doubleValueAsLong |= ((long) resultExponent) << IndexNumberEncoder.DOUBLE_SIGNIFICAND_BITS;
    doubleValueAsLong |= resultNegative ? IndexNumberEncoder.DOUBLE_SIGN_BIT : 0;
    resultAsDouble = Double.longBitsToDouble(doubleValueAsLong);
  }

  /**
   * Reads bytes encoding a number from the buffer at the offset, in the order specified by
   * descending, and returns the new offset.
   *
   * <p>The decoded number is accessible via {@link #isResultLong}, {@link #resultAsLong}, {@link
   * #isResultDouble}, {@link #resultAsDouble}.
   *
   * @param descending when true, expect an encoding that orders numbers in descending order
   * @param buffer bytes to read
   * @param offset index into buffer of first byte to read
   * @return number of bytes read from buffer
   */
  public int decode(boolean descending, byte[] buffer, int offset) {
    int bufferPos = offset;

    int b = buffer[bufferPos++] & 0xFF;

    // Determine if the leading byte is negative, and if so, invert it.
    boolean invertEncoding = (b & 0x80) == 0;
    int inverter = invertEncoding ? 0xFF : 0;
    b ^= inverter;

    // Determine if the result is negative.
    boolean negative = invertEncoding ^ descending;

    // Determine if the exponent is negative too, don't invert again though because this could
    // mangle any significand bits in the leading byte.
    boolean exponentNegative = (b & 0x40) == 0;
    int exponentInverter = exponentNegative ? 0xFF : 0;

    int exponent;
    long significand = 0L;

    // The position in the significand to write the next bit
    int writeBit = IndexNumberEncoder.SIGNIFICAND_BITS;

    int marker = decodeMarker(b ^ exponentInverter);
    switch (marker) {
      case -4:
        // st00001C => exponent 0
        if (exponentNegative) {
          throw new IllegalArgumentException(
              "Invalid encoded number: exponent negative zero is invalid");
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
        exponent = IndexNumberEncoder.EXP1_END + marker;
        writeBit -= exponent;

        // The number of bits of significand is equal to the exponent. The significand bits are also
        // shifted one left to make room for the continuation bit.
        int significandStart = exponent + 1;
        int significandMask = ~(-1 << significandStart) & 0x7E;
        significand |= ((long) (b & significandMask)) << (writeBit - 1);
        break;

      case 1:
        // st10 eeee mmmm mmmC

        // Exponent is stored in the low-order 4 bits, with bias
        exponent = (b ^ exponentInverter) & 0x0F;
        exponent += IndexNumberEncoder.EXP1_END;

        b = (buffer[bufferPos++] & 0xFF) ^ inverter;
        writeBit -= 7;
        significand |= decodeTrailingSignificandByte(b, writeBit);
        break;

      case 2:
        // st11 0eee eeee mmmm mmmm mmmC

        // 7 bit exponent. bits 6-4 come from the leading byte
        exponent = ((b ^ exponentInverter) & 0x07) << 4;

        // bits 3-0 come from the next byte, then undo bias
        b = (buffer[bufferPos++] & 0xFF) ^ inverter;
        exponent |= (b ^ exponentInverter) >>> 4;
        exponent += IndexNumberEncoder.EXP2_END;

        // 4 bits of significand in the low-order four bits of the second byte
        writeBit -= 4;
        significand |= ((long) b & 0x0F) << writeBit;

        // And a forced third byte with continuation bit
        b = (buffer[bufferPos++] & 0xFF) ^ inverter;
        writeBit -= 7;
        significand |= decodeTrailingSignificandByte(b, writeBit);
        break;

      case 3:
        // st11 10ee eeee eeee mmmm mmmC

        // 10 bit exponent. Bits 9-8 come from the leading byte
        exponent = ((b ^ exponentInverter) & 0x03) << 8;

        // bits 7-0 come from the next byte, then undo bias
        b = (buffer[bufferPos++] & 0xFF) ^ inverter;
        exponent |= b ^ exponentInverter;
        exponent += IndexNumberEncoder.EXP3_END;

        // And a forced third byte with continuation bit
        b = (buffer[bufferPos++] & 0xFF) ^ inverter;
        writeBit -= 7;
        significand |= decodeTrailingSignificandByte(b, writeBit);
        break;

      case 6:
        // st11 1111
        // Special cases where exponent is infinite
        if (invertEncoding) {
          if (exponentNegative) {
            // zero: negative number, negative infinite exponent
            // Not generated by the encoder and converted to positive 0 shortly.
            recordNumber(negative, IndexNumberEncoder.NEGATIVE_INFINITE_EXPONENT, 0);

          } else {
            // Need another byte to distinguish NaN from -Infinity

            // These special values are defined in terms of the complemented bytes so don't invert
            // to make the comparisons
            b = (buffer[bufferPos++] & 0xFF);
            if (b == 0x80) {
              // -Infinity
              recordNumber(negative, IndexNumberEncoder.POSITIVE_INFINITE_EXPONENT, 0);
            } else if (b == 0x60) {
              // NaN
              recordNumber(negative, IndexNumberEncoder.POSITIVE_INFINITE_EXPONENT, 1);
            } else {
              throw new IllegalArgumentException("Invalid encoded byte array");
            }
          }
        } else {
          if (exponentNegative) {
            // zero: positive number, negative infinite exponent
            recordNumber(negative, IndexNumberEncoder.NEGATIVE_INFINITE_EXPONENT, 0);
          } else {
            // Positive infinity
            recordNumber(negative, IndexNumberEncoder.POSITIVE_INFINITE_EXPONENT, 0);
          }
        }
        return bufferPos - offset;

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

      b = (buffer[bufferPos++] & 0xFF) ^ inverter;

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

    recordNumber(negative, exponent, significand);
    return bufferPos - offset;
  }

  private void recordNumber(boolean negative, int exponent, long significand) {
    longResultRepProblemMessage = null;
    doubleResultRepProblemMessage = null;
    resultNegative = negative;
    resultExponent = exponent;
    resultSignificand = significand;
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
}
