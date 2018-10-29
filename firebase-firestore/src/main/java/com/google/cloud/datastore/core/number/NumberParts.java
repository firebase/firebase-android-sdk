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
 * The representation of a number that can accommodate the range of doubles and longs without loss
 * of precision.
 */
public final class NumberParts {

  /**
   * The number of bits in the common significand representation long enough to represent both
   * doubles and int64s.
   */
  static final int SIGNIFICAND_BITS = 64;

  static final int POSITIVE_INFINITE_EXPONENT = Integer.MAX_VALUE;
  static final int NEGATIVE_INFINITE_EXPONENT = Integer.MIN_VALUE;

  /** Double precision stores the exponent in an offset binary form, with this bias. */
  private static final int DOUBLE_EXPONENT_BIAS = 1023;

  /**
   * The minimum value of the exponent of a normal double-precision number (after removing bias).
   * -1023 is reserved for zero and a subnormal indicator.
   */
  private static final int DOUBLE_MIN_EXPONENT = -1022;

  /**
   * The number of explicitly stored significand bits in IEEE 754 double precision format. This does
   * not count the implied leading significand bit.
   */
  private static final int DOUBLE_SIGNIFICAND_BITS = 52;

  @SuppressWarnings("NumericOverflow")
  private static final long DOUBLE_SIGN_BIT = 1L << (Double.SIZE - 1);

  private final boolean negative;
  private final int exponent;
  private final long significand;

  private NumberParts(boolean negative, int exponent, long significand) {
    this.negative = negative;
    this.exponent = exponent;
    this.significand = significand;
  }

  /** True if the number was overall negative (i.e. less than zero). */
  public boolean negative() {
    return negative;
  }

  /** The actual value of the binary exponent in the floating point encoding. */
  public int exponent() {
    return exponent;
  }

  /**
   * The value of the significand in the floating point encoding, left justified, with a hidden
   * leading one bit.
   */
  public long significand() {
    return significand;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NumberParts)) {
      return false;
    }

    NumberParts that = (NumberParts) o;

    return negative == that.negative
        && exponent == that.exponent
        && significand == that.significand;
  }

  @Override
  public int hashCode() {
    int result = (negative ? 1 : 0);
    result = 31 * result + exponent;
    result = 31 * result + (int) (significand ^ (significand >>> 32));
    return result;
  }

  /**
   * Returns whether this NumberParts represents 0.
   *
   * <p>There is only one zero representation in NumberParts (unlike double, which has both negative
   * and positive zero).
   */
  public boolean isZero() {
    return exponent() == NEGATIVE_INFINITE_EXPONENT && significand() == 0;
  }

  /**
   * Returns whether this NumberParts represents NaN.
   *
   * <p>There is only one NaN representation in NumberParts (unlike double).
   */
  public boolean isNaN() {
    return exponent() == POSITIVE_INFINITE_EXPONENT && significand() != 0;
  }

  /** Returns whether this NumberParts represent an infinity (positive or negative). */
  public boolean isInfinite() {
    return exponent() == POSITIVE_INFINITE_EXPONENT && significand() == 0;
  }

  public static NumberParts create(boolean negative, int exponent, long significand) {
    // Reject non-normalized values.
    if (exponent == POSITIVE_INFINITE_EXPONENT && significand != 0) {
      if (!negative || significand != 1) {
        throw new IllegalArgumentException("Invalid number parts: non-normalized NaN");
      }
    }

    return new NumberParts(negative, exponent, significand);
  }

  /** Returns the NumberParts representation of the given long. */
  public static NumberParts fromLong(long value) {
    if (value == 0) {
      return NumberParts.create(false, NEGATIVE_INFINITE_EXPONENT, 0);
    }

    boolean negative = false;
    if (value < 0) {
      // Note that Long.MIN_VALUE does not need special handling here, despite the fact that
      // -Long.MIN_VALUE doesn't actually negate the value.
      //
      // This works because Long.MIN_VALUE, when reinterpreted as an unsigned long happens to have
      // the right bit pattern to represent itself.
      //
      // For all other negative values, negate them so that the encoder only has to deal with
      // positive values.
      negative = true;
      value = -value;
    }

    int leadingZeros = Long.numberOfLeadingZeros(value);
    int binaryExponent = SIGNIFICAND_BITS - 1 - leadingZeros;

    // Hide the leading bit
    long significand = value & ~(1L << binaryExponent);

    // Left-justify into the leading bits of the value. This removes both the leading zeros and the
    // most significant 1 bit, making the significand suitable for encoding
    significand <<= leadingZeros + 1;

    return NumberParts.create(negative, binaryExponent, significand);
  }

  /** Returns the NumberParts representation of the given double. */
  public static NumberParts fromDouble(double value) {
    // Convert long bits where all NaNs have been normalized 0x7FFC000000000000L.
    long doubleBits = Double.doubleToLongBits(value);

    boolean negative = value < 0.0;
    int exponent = (int) ((doubleBits >>> DOUBLE_SIGNIFICAND_BITS) & 0x7FF) - DOUBLE_EXPONENT_BIAS;
    long significand = doubleBits & ~(-1L << DOUBLE_SIGNIFICAND_BITS);

    // Special cases:
    if (exponent < DOUBLE_MIN_EXPONENT) {
      if (significand == 0) {
        return NumberParts.create(false, NEGATIVE_INFINITE_EXPONENT, 0);
      } else {
        // Subnormal value; normalize. This is possible because the encoding can represent exponents
        // much smaller (more negative) than are possible even with subnormals.
        int leadingZeros = Long.numberOfLeadingZeros(significand);
        int binaryExponent = SIGNIFICAND_BITS - 1 - leadingZeros;

        // Mask out the highest bit to convert to implicit leading 1 form
        significand &= ~(1L << binaryExponent);

        // As with longs below, left justify
        significand <<= leadingZeros + 1;

        // Adjust the exponent to reflect the shift required to get to normal form.
        int adjustment = leadingZeros - (SIGNIFICAND_BITS - DOUBLE_SIGNIFICAND_BITS);
        exponent -= adjustment;
      }
    } else if (exponent > DOUBLE_EXPONENT_BIAS) {
      // Infinities or NaN
      return significand == 0
          ? (negative
              ? NumberParts.create(true, POSITIVE_INFINITE_EXPONENT, 0)
              : NumberParts.create(false, POSITIVE_INFINITE_EXPONENT, 0))
          : NumberParts.create(true, POSITIVE_INFINITE_EXPONENT, 1);
    } else {
      // The main encode loop takes a significand whose values have a hidden leading bit and are
      // left-justified (i.e. MSB of the significand guaranteed to be in the MSB of a long). Since
      // doubles only have 52 bits of significand (excluding hidden bit), a left shift is required.
      significand <<= SIGNIFICAND_BITS - DOUBLE_SIGNIFICAND_BITS;
    }

    return NumberParts.create(negative, exponent, significand);
  }

  /**
   * Returns this NumberParts with the sign flipped.
   *
   * <p>Returns the same instance for zero (negative 0 is not representable in NumberParts) and NaN.
   */
  public NumberParts negate() {
    if (isZero() || isNaN()) {
      return this;
    }
    return create(!negative(), exponent(), significand());
  }

  /**
   * Returns whether or not this NumberParts can be represented as a double without loss of
   * precision.
   */
  public boolean representableAsDouble() {
    return doubleRepresentationError() == null;
  }

  /**
   * Returns whether or not this NumberParts can be represented as a long without loss of precision.
   */
  public boolean representableAsLong() {
    return longRepresentationError() == null;
  }

  /**
   * Returns the double representation of this NumberParts,
   *
   * @throws IllegalArgumentException if this would lead to a loss of precision.
   */
  public double asDouble() {
    String representationError = doubleRepresentationError();
    if (representationError != null) {
      throw new IllegalArgumentException(representationError);
    }

    if (isZero()) {
      return 0.0;
    } else if (isInfinite()) {
      return negative() ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    } else if (isNaN()) {
      return Double.NaN;
    }

    long exponent = exponent();
    long significand = significand();

    // Move significand bits down to their expected location.
    significand >>>= SIGNIFICAND_BITS - DOUBLE_SIGNIFICAND_BITS;
    if (exponent >= Double.MIN_EXPONENT) {
      // Normal; re-add bias
      exponent += DOUBLE_EXPONENT_BIAS;
    } else {
      // Subnormal
      int adjustment = Double.MIN_EXPONENT - exponent();
      significand >>>= adjustment;

      // Re-add explicit leading 1
      significand |= 1L << (DOUBLE_SIGNIFICAND_BITS - adjustment);

      exponent = 0;
    }

    long result = significand;
    result |= exponent << DOUBLE_SIGNIFICAND_BITS;
    result |= negative() ? DOUBLE_SIGN_BIT : 0;
    return Double.longBitsToDouble(result);
  }

  /**
   * Returns the long representation of this NumberParts,
   *
   * @throws IllegalArgumentException if this is not representable as a long.
   */
  public long asLong() {
    String representationError = longRepresentationError();
    if (representationError != null) {
      throw new IllegalArgumentException(representationError);
    }

    if (isZero()) {
      return 0L;
    } else if (exponent() == (SIGNIFICAND_BITS - 1)) {
      return Long.MIN_VALUE;
    }

    long result = significand();
    int leadingZeros = (SIGNIFICAND_BITS - 1) - exponent();
    result >>>= leadingZeros + 1;

    // Unhide the leading bit
    result ^= (1L << exponent());

    // Re-apply sign
    if (negative()) {
      result = -result;
    }
    return result;
  }

  // @Nullable
  private static String doubleRepresentationError() {
    // TODO: check for overflow
    return null;
  }

  // TODO: see go/objecttostring-lsc
  @SuppressWarnings("ObjectToString")
  private String longRepresentationError() {
    if (isZero()) {
      return null;
    } else if (isInfinite()) {
      // TODO: NumberParts does not implement toString() in this
      return "Invalid encoded long " + this + ": Infinity is not a long";
    } else if (isNaN()) {
      // TODO: NumberParts does not implement toString() in this
      return "Invalid encoded long " + this + ": NaN is not a long";
    } else if (exponent() == (SIGNIFICAND_BITS - 1)) {
      // The only legit value with an exponent of 63 is Long.MIN_VALUE
      if (significand() != 0 || !negative()) {
        // TODO: NumberParts does not implement toString() in this
        return "Invalid encoded long " + this + ": overflow";
      }
      return null;
    } else if (exponent() < 0 || exponent() > (SIGNIFICAND_BITS - 1)) {
      // Exponent is negative, or too large
      // TODO: NumberParts does not implement toString() in this
      return "Invalid encoded long " + this + ": exponent " + exponent() + " too large";
    }

    // Check if the number contains a fractional part.
    int trailingZeros = Long.numberOfTrailingZeros(significand());
    if (exponent() < (SIGNIFICAND_BITS - trailingZeros)) {
      // TODO: NumberParts does not implement toString() in this
      return "Invalid encoded long " + this + ": contains fractional part";
    }
    return null;
  }
}
