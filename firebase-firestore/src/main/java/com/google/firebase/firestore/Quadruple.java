// Copyright 2025 Google LLC
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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.QuadrupleBuilder.EXPONENT_OF_INFINITY;

/**
 * A 128-bit binary floating point number which supports comparisons and creation from long, double
 * and string.
 *
 * @param negative the sign of the number.
 * @param biasedExponent the unsigned and biased (by 0x7FFF_FFFF) binary exponent.
 * @param mantHi the unsigned high 64 bits of the mantissa (leading 1 omitted).
 * @param mantLo the unsigned low 64 bits of the mantissa.
 *
 * This class is for internal usage only and should not be exposed externally.
 * @hide
 */
public final class Quadruple implements Comparable<Quadruple> {
  public static final Quadruple POSITIVE_ZERO = new Quadruple(false, 0, 0, 0);
  public static final Quadruple NEGATIVE_ZERO = new Quadruple(true, 0, 0, 0);
  public static final Quadruple NaN = new Quadruple(false, (int) EXPONENT_OF_INFINITY, 1L << 63, 0);
  public static final Quadruple NEGATIVE_INFINITY =
      new Quadruple(true, (int) EXPONENT_OF_INFINITY, 0, 0);
  public static final Quadruple POSITIVE_INFINITY =
      new Quadruple(false, (int) EXPONENT_OF_INFINITY, 0, 0);
  private static final Quadruple MIN_LONG = new Quadruple(true, bias(63), 0, 0);
  private static final Quadruple POSITIVE_ONE = new Quadruple(false, bias(0), 0, 0);
  private static final Quadruple NEGATIVE_ONE = new Quadruple(true, bias(0), 0, 0);
  private final boolean negative;
  private final int biasedExponent;
  private final long mantHi;
  private final long mantLo;

  /**
   * Build a new quadruple from its raw representation - sign, biased exponent, 128-bit mantissa.
   */
  public Quadruple(boolean negative, int biasedExponent, long mantHi, long mantLo) {
    this.negative = negative;
    this.biasedExponent = biasedExponent;
    this.mantHi = mantHi;
    this.mantLo = mantLo;
  }

  /** Return the sign of this {@link Quadruple}. */
  public boolean negative() {
    return negative;
  }

  /** Return the unsigned-32-bit biased exponent of this {@link Quadruple}. */
  public int biasedExponent() {
    return biasedExponent;
  }

  /** Return the high-order unsigned-64-bits of the mantissa of this {@link Quadruple}. */
  public long mantHi() {
    return mantHi;
  }

  /** Return the low-order unsigned-64-bits of the mantissa of this {@link Quadruple}. */
  public long mantLo() {
    return mantLo;
  }

  /** Return the (unbiased) exponent of this {@link Quadruple}. */
  public int exponent() {
    return biasedExponent - QuadrupleBuilder.EXPONENT_BIAS;
  }

  /** Return true if this {@link Quadruple} is -0 or +0 */
  public boolean isZero() {
    return biasedExponent == 0 && mantHi == 0 && mantLo == 0;
  }

  /** Return true if this {@link Quadruple} is -infinity or +infinity */
  public boolean isInfinite() {
    return biasedExponent == (int) EXPONENT_OF_INFINITY && mantHi == 0 && mantLo == 0;
  }

  /** Return true if this {@link Quadruple} is a NaN. */
  public boolean isNaN() {
    return biasedExponent == (int) EXPONENT_OF_INFINITY && !(mantHi == 0 && mantLo == 0);
  }

  // equals (and hashCode) follow Double.equals: all NaNs are equal and -0 != 0
  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Quadruple)) {
      return false;
    }
    Quadruple otherQuadruple = (Quadruple) other;
    if (isNaN()) {
      return otherQuadruple.isNaN();
    } else {
      return negative == otherQuadruple.negative
          && biasedExponent == otherQuadruple.biasedExponent
          && mantHi == otherQuadruple.mantHi
          && mantLo == otherQuadruple.mantLo;
    }
  }

  @Override
  public int hashCode() {
    if (isNaN()) {
      return HASH_NAN;
    } else {
      int hashCode = Boolean.hashCode(negative);
      hashCode = hashCode * 31 + Integer.hashCode(biasedExponent);
      hashCode = hashCode * 31 + Long.hashCode(mantHi);
      hashCode = hashCode * 31 + Long.hashCode(mantLo);
      return hashCode;
    }
  }

  private static final int HASH_NAN = 31 * 31 * Integer.hashCode((int) EXPONENT_OF_INFINITY);

  // Compare two quadruples, with -0 < 0, and all NaNs equal and larger than all numbers.
  @Override
  public int compareTo(Quadruple other) {
    if (isNaN()) {
      return other.isNaN() ? 0 : 1;
    }
    if (other.isNaN()) {
      return -1;
    }
    int lessThan;
    int greaterThan;
    if (negative) {
      if (!other.negative) {
        return -1;
      }
      lessThan = 1;
      greaterThan = -1;
    } else {
      if (other.negative) {
        return 1;
      }
      lessThan = -1;
      greaterThan = 1;
    }
    int expCompare = Integer.compareUnsigned(biasedExponent, other.biasedExponent);
    if (expCompare < 0) {
      return lessThan;
    }
    if (expCompare > 0) {
      return greaterThan;
    }
    int mantHiCompare = Long.compareUnsigned(mantHi, other.mantHi);
    if (mantHiCompare < 0) {
      return lessThan;
    }
    if (mantHiCompare > 0) {
      return greaterThan;
    }
    int mantLoCompare = Long.compareUnsigned(mantLo, other.mantLo);
    if (mantLoCompare < 0) {
      return lessThan;
    }
    if (mantLoCompare > 0) {
      return greaterThan;
    }
    return 0;
  }

  public static Quadruple fromLong(long value) {
    if (value == Long.MIN_VALUE) {
      return MIN_LONG;
    }
    if (value == 0) {
      return POSITIVE_ZERO;
    }
    if (value == 1) {
      return POSITIVE_ONE;
    }
    if (value == -1) {
      return NEGATIVE_ONE;
    }
    boolean negative = value < 0;
    if (negative) {
      value = -value;
    }
    // Left-justify with the leading 1 dropped - value=0 or 1 is handled separately above, so
    // leadingZeros+1 <= 63.
    int leadingZeros = Long.numberOfLeadingZeros(value);
    return new Quadruple(negative, bias(63 - leadingZeros), value << (leadingZeros + 1), 0);
  }

  public static Quadruple fromDouble(double value) {
    if (Double.isNaN(value)) {
      return NaN;
    }
    if (Double.isInfinite(value)) {
      return value < 0 ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
    }
    if (Double.compare(value, 0.0) == 0) {
      return POSITIVE_ZERO;
    }
    if (Double.compare(value, -0.0) == 0) {
      return NEGATIVE_ZERO;
    }
    long bits = Double.doubleToLongBits(value);
    long mantHi = bits << 12;
    long exponent = bits >>> 52 & 0x7ff;
    if (exponent == 0) {
      // subnormal - mantHi cannot be zero as that means value==+/-0
      int leadingZeros = Long.numberOfLeadingZeros(mantHi);
      mantHi = leadingZeros < 63 ? mantHi << (leadingZeros + 1) : 0;
      exponent = -leadingZeros;
    }
    return new Quadruple(value < 0, bias((int) (exponent - 1023)), mantHi, 0);
  }

  /**
   * Converts a decimal number to a {@link Quadruple}. The supported format (no whitespace allowed)
   * is:
   *
   * <ul>
   *   <li>NaN for Quadruple.NaN
   *   <li>Infinity or +Infinity for Quadruple.POSITIVE_INFINITY
   *   <li>-Infinity for Quadruple.NEGATIVE_INFINITY
   *   <li>regular expression: [+-]?[0-9]*(.[0-9]*)?([eE][+-]?[0-9]+)? - the exponent cannot be more
   *       than 9 digits, and the whole string cannot be empty
   * </ul>
   */
  public static Quadruple fromString(String s) {
    if (s.equals("NaN")) {
      return NaN;
    }
    if (s.equals("-Infinity")) {
      return NEGATIVE_INFINITY;
    }
    if (s.equals("Infinity") || s.equals("+Infinity")) {
      return POSITIVE_INFINITY;
    }
    char[] chars = s.toCharArray();
    byte[] digits = new byte[chars.length];
    int len = chars.length;
    int i = 0;
    int j = 0;
    int exponent = 0;
    boolean negative = false;
    if (i < len) {
      if (chars[i] == '-') {
        negative = true;
        i++;
      } else if (chars[i] == '+') {
        i++;
      }
    }
    int firstDigit = i;
    while (i < len && Character.isDigit(chars[i])) {
      digits[j++] = (byte) (chars[i++] - '0');
    }
    if (i < len && chars[i] == '.') {
      int decimal = ++i;
      while (i < len && Character.isDigit(chars[i])) {
        digits[j++] = (byte) (chars[i++] - '0');
      }
      exponent = decimal - i;
    }
    if (i < len && (chars[i] == 'e' || chars[i] == 'E')) {
      int exponentValue = 0;
      i++;
      int exponentSign = 1;
      if (i < len) {
        if (chars[i] == '-') {
          exponentSign = -1;
          i++;
        } else if (chars[i] == '+') {
          i++;
        }
      }
      int firstExponent = i;
      while (i < len && Character.isDigit(chars[i])) {
        exponentValue = exponentValue * 10 + chars[i++] - '0';
        if (i - firstExponent > 9) {
          throw new NumberFormatException("Exponent too large " + s);
        }
      }
      if (i == firstExponent) {
        throw new NumberFormatException("Invalid number " + s);
      }
      exponent += exponentValue * exponentSign;
    }
    if (j == 0 || i != len) {
      throw new NumberFormatException("Invalid number " + s);
    }
    byte[] digitsCopy = new byte[j];
    System.arraycopy(digits, 0, digitsCopy, 0, j);
    QuadrupleBuilder parsed = QuadrupleBuilder.parseDecimal(digitsCopy, exponent);
    return new Quadruple(negative, parsed.exponent, parsed.mantHi, parsed.mantLo);
  }

  private static final int bias(int exponent) {
    return exponent + QuadrupleBuilder.EXPONENT_BIAS;
  }
}
