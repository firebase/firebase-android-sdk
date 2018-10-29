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

/** A utility class for comparing numbers. */
public final class NumberComparisonHelper {

  // Long.MIN_VALUE has an exact representation as double, so the long lower bound is inclusive.
  public static final double LONG_INCLUSIVE_LOWER_BOUND_AS_DOUBLE = (double) Long.MIN_VALUE;
  // Long.MAX_VALUE has no exact representation as double (casting as we've done makes 2^63, which
  // is 1 larger than Long.MAX_VALUE), so the long upper bound is exclusive.
  public static final double LONG_EXCLUSIVE_UPPER_BOUND_AS_DOUBLE = (double) Long.MAX_VALUE;

  /** The maximum value in the main range of integers representable as both long and double. */
  public static final long MAX_SAFE_LONG = 1L << 53;
  /** The minimum value in the main range of integers representable as both long and double. */
  public static final long MIN_SAFE_LONG = -MAX_SAFE_LONG;

  /**
   * Compares a double and a with Firestore query semantics: NaN precedes all other numbers and
   * equals itself, all zeroes are equal.
   */
  public static int firestoreCompareDoubleWithLong(double doubleValue, long longValue) {
    // In Firestore NaN is defined to compare before all other numbers.
    if (Double.isNaN(doubleValue)) {
      return -1;
    }

    // This also handles negative infinity.
    if (doubleValue < LONG_INCLUSIVE_LOWER_BOUND_AS_DOUBLE) {
      return -1;
    }

    // This also handles positive infinity.
    if (doubleValue >= LONG_EXCLUSIVE_UPPER_BOUND_AS_DOUBLE) {
      return 1;
    }

    long doubleAsLong = (long) doubleValue;
    int cmp = compareLongs(doubleAsLong, longValue);
    if (cmp != 0) {
      return cmp;
    }

    // At this point the long representations are equal but this could be due to rounding.
    double longAsDouble = (double) longValue;
    return firestoreCompareDoubles(doubleValue, longAsDouble);
  }

  /**
   * Compares longs. Note that we can't use Long.compare because it's only available after Android
   * 19.
   */
  public static int compareLongs(long leftLong, long rightLong) {
    if (leftLong < rightLong) {
      return -1;
    } else if (leftLong > rightLong) {
      return 1;
    } else {
      return 0;
    }
  }

  /**
   * Compares doubles with Firestore query semantics: NaN precedes all other numbers and equals
   * itself, all zeroes are equal.
   *
   * @return a negative integer, zero, or a positive integer as the first argument is less than,
   *     equal to, or greater than the second.
   */
  public static int firestoreCompareDoubles(double leftDouble, double rightDouble) {
    // NaN sorts equal to itself and before any other number.
    if (leftDouble < rightDouble) {
      return -1;
    } else if (leftDouble > rightDouble) {
      return 1;
    } else if (leftDouble == rightDouble) {
      return 0;
    } else {
      // One or both of leftDouble and rightDouble are NaN.
      if (!Double.isNaN(rightDouble)) {
        // Only leftDouble is NaN.
        return -1;
      } else if (!Double.isNaN(leftDouble)) {
        // Only rightDouble is NaN.
        return 1;
      } else {
        // Both are Nan.
        return 0;
      }
    }
  }

  private NumberComparisonHelper() {}
}
