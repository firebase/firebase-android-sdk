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

import static com.google.firebase.firestore.testutil.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BsonTypesTest {

  @Test
  public void testBsonObjectIdEquality() {
    BsonObjectId bsonObjectId = new BsonObjectId("507f191e810c19729de860ea");
    BsonObjectId bsonObjectIdDup = new BsonObjectId("507f191e810c19729de860ea");
    BsonObjectId differentObjectId = new BsonObjectId("507f191e810c19729de860eb");

    assertEquals(bsonObjectId, bsonObjectIdDup);
    assertNotEquals(bsonObjectId, differentObjectId);
    assertNotEquals(bsonObjectIdDup, differentObjectId);

    assertEquals(bsonObjectId.hashCode(), bsonObjectIdDup.hashCode());
  }

  @Test
  public void testBsonTimeStampEquality() {
    BsonTimestamp bsonTimestamp = new BsonTimestamp(1, 2);
    BsonTimestamp bsonTimestampDup = new BsonTimestamp(1, 2);
    BsonTimestamp differentSecondsTimestamp = new BsonTimestamp(2, 2);
    BsonTimestamp differentIncrementTimestamp = new BsonTimestamp(1, 3);

    assertEquals(bsonTimestamp, bsonTimestampDup);
    assertNotEquals(bsonTimestamp, differentSecondsTimestamp);
    assertNotEquals(bsonTimestamp, differentIncrementTimestamp);
    assertNotEquals(bsonTimestampDup, differentSecondsTimestamp);
    assertNotEquals(bsonTimestampDup, differentIncrementTimestamp);

    assertEquals(bsonTimestamp.hashCode(), bsonTimestampDup.hashCode());
  }

  @Test
  public void testBsonBinaryDataEquality() {
    BsonBinaryData bsonBinaryData = BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3});
    BsonBinaryData bsonBinaryDataDup = BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3});
    BsonBinaryData differentSubtypeBinaryData = BsonBinaryData.fromBytes(2, new byte[] {1, 2, 3});
    BsonBinaryData differentDataBinaryData = BsonBinaryData.fromBytes(1, new byte[] {1, 2, 4});

    assertEquals(bsonBinaryData, bsonBinaryDataDup);
    assertNotEquals(bsonBinaryData, differentSubtypeBinaryData);
    assertNotEquals(bsonBinaryData, differentDataBinaryData);
    assertNotEquals(bsonBinaryDataDup, differentSubtypeBinaryData);
    assertNotEquals(bsonBinaryDataDup, differentDataBinaryData);

    assertEquals(bsonBinaryData.hashCode(), bsonBinaryDataDup.hashCode());
  }

  @Test
  public void testRegexEquality() {
    RegexValue regex = new RegexValue("^foo", "i");
    RegexValue regexDup = new RegexValue("^foo", "i");
    RegexValue differentPatternRegex = new RegexValue("^bar", "i");
    RegexValue differentOptionsRegex = new RegexValue("^foo", "m");

    assertEquals(regex, regexDup);
    assertNotEquals(regex, differentPatternRegex);
    assertNotEquals(regex, differentOptionsRegex);
    assertNotEquals(regexDup, differentPatternRegex);
    assertNotEquals(regexDup, differentOptionsRegex);

    assertEquals(regex.hashCode(), regexDup.hashCode());
  }

  @Test
  public void testInt32Equality() {
    Int32Value int32 = new Int32Value(1);
    Int32Value int32Dup = new Int32Value(1);
    Int32Value differentInt32 = new Int32Value(2);

    assertEquals(int32, int32Dup);
    assertNotEquals(int32, differentInt32);
    assertNotEquals(int32Dup, differentInt32);

    assertEquals(int32.hashCode(), int32Dup.hashCode());
  }

  @Test
  public void testDecimal128Equality() {
    Decimal128Value decimal128 = new Decimal128Value("1.2e3");
    Decimal128Value decimal128Dup = new Decimal128Value("1.2e3");
    Decimal128Value differentDecimal128 = new Decimal128Value("1.3e3");
    assertEquals(decimal128, decimal128Dup);
    assertNotEquals(decimal128, differentDecimal128);
    assertEquals(decimal128.hashCode(), decimal128Dup.hashCode());

    Decimal128Value dZeroPointFive = new Decimal128Value("0.5");
    Decimal128Value dHalf = new Decimal128Value(".5");
    Decimal128Value dFiveEminusOne = new Decimal128Value("5e-1");
    assertEquals(dZeroPointFive, dHalf);
    assertEquals(dZeroPointFive.hashCode(), dHalf.hashCode());
    assertEquals(dZeroPointFive, dFiveEminusOne);
    assertEquals(dZeroPointFive.hashCode(), dFiveEminusOne.hashCode());

    Decimal128Value dOne = new Decimal128Value("1");
    Decimal128Value dOnePointZero = new Decimal128Value("1.0");
    Decimal128Value dOnePointZeroZero = new Decimal128Value("1.00");
    assertEquals(dOne, dOnePointZero);
    assertEquals(dOne.hashCode(), dOnePointZero.hashCode());
    assertEquals(dOnePointZero, dOnePointZeroZero);
    assertEquals(dOnePointZero.hashCode(), dOnePointZeroZero.hashCode());

    // numerical equality with different string representation
    Decimal128Value dTwelveHundred_1_2e3 = new Decimal128Value("1.2e3");
    Decimal128Value dTwelveHundred_12e2 = new Decimal128Value("12e2");
    Decimal128Value dTwelveHundred_0_12e4 = new Decimal128Value("0.12e4");
    Decimal128Value dTwelveHundred_12000eMinus1 = new Decimal128Value("12000e-1");
    Decimal128Value dOnePointTwo = new Decimal128Value("1.2");
    assertEquals(dTwelveHundred_1_2e3, dTwelveHundred_12e2);
    assertEquals(dTwelveHundred_1_2e3.hashCode(), dTwelveHundred_12e2.hashCode());
    assertEquals(dTwelveHundred_1_2e3, dTwelveHundred_0_12e4);
    assertEquals(dTwelveHundred_1_2e3.hashCode(), dTwelveHundred_0_12e4.hashCode());
    assertEquals(dTwelveHundred_1_2e3, dTwelveHundred_12000eMinus1);
    assertEquals(dTwelveHundred_1_2e3.hashCode(), dTwelveHundred_12000eMinus1.hashCode());
    assertNotEquals(dTwelveHundred_1_2e3, dOnePointTwo);

    // Edge Cases: Zero
    Decimal128Value positiveZero = new Decimal128Value("0");
    Decimal128Value negativeZero = new Decimal128Value("-0");
    Decimal128Value anotherPositiveZero = new Decimal128Value("+0");
    Decimal128Value zeroWithDecimal = new Decimal128Value("0.0");
    Decimal128Value negativeZeroWithDecimal = new Decimal128Value("-0.0");
    Decimal128Value positiveZeroWithDecimal = new Decimal128Value("+0.0");
    Decimal128Value zeroWithLeadingAndTrailingZeros = new Decimal128Value("00.00");
    Decimal128Value negativeZeroWithLeadingAndTrailingZeros = new Decimal128Value("-00.000");
    Decimal128Value negativeZeroWithExponent = new Decimal128Value("-00.000e-10");
    Decimal128Value negativeZeroWithZeroExponent = new Decimal128Value("-00.000e-0");
    Decimal128Value zeroWithExponent = new Decimal128Value("00.000e10");
    assertEquals(positiveZero, negativeZero);
    assertEquals(positiveZero.hashCode(), negativeZero.hashCode());
    assertEquals(positiveZero, anotherPositiveZero);
    assertEquals(positiveZero.hashCode(), anotherPositiveZero.hashCode());
    assertEquals(positiveZero, zeroWithDecimal);
    assertEquals(positiveZero.hashCode(), zeroWithDecimal.hashCode());
    assertEquals(positiveZero, negativeZeroWithDecimal);
    assertEquals(positiveZero.hashCode(), negativeZeroWithDecimal.hashCode());
    assertEquals(positiveZero, positiveZeroWithDecimal);
    assertEquals(positiveZero.hashCode(), positiveZeroWithDecimal.hashCode());
    assertEquals(positiveZero, zeroWithLeadingAndTrailingZeros);
    assertEquals(positiveZero.hashCode(), zeroWithLeadingAndTrailingZeros.hashCode());
    assertEquals(positiveZero, negativeZeroWithLeadingAndTrailingZeros);
    assertEquals(positiveZero.hashCode(), negativeZeroWithLeadingAndTrailingZeros.hashCode());
    assertEquals(positiveZero, negativeZeroWithExponent);
    assertEquals(positiveZero.hashCode(), negativeZeroWithExponent.hashCode());
    assertEquals(positiveZero, negativeZeroWithZeroExponent);
    assertEquals(positiveZero.hashCode(), negativeZeroWithZeroExponent.hashCode());
    assertEquals(positiveZero, zeroWithExponent);
    assertEquals(positiveZero.hashCode(), zeroWithExponent.hashCode());

    // Infinity
    Decimal128Value positiveInfinity = new Decimal128Value("Infinity");
    Decimal128Value negInfinity = new Decimal128Value("-Infinity");
    Decimal128Value anotherPositiveInfinity = new Decimal128Value("Infinity");
    assertEquals(positiveInfinity, anotherPositiveInfinity);
    assertEquals(positiveInfinity.hashCode(), anotherPositiveInfinity.hashCode());
    assertNotEquals(positiveInfinity, negInfinity);

    // NaN
    Decimal128Value nan1 = new Decimal128Value("NaN");
    Decimal128Value nan2 = new Decimal128Value("NaN");
    assertEquals(nan1, nan2);
    assertEquals(nan1.hashCode(), nan2.hashCode());

    assertNotEquals(nan1, dOne);
    assertNotEquals(nan1, positiveInfinity);

    // Large Numbers
    Decimal128Value largeNum1 =
        new Decimal128Value("123456789012345678901234567890.123456789012345678901234567890");
    Decimal128Value largeNum2 =
        new Decimal128Value("1.23456789012345678901234567890123456789012345678901234567890e29");
    assertEquals(largeNum1, largeNum2);
    assertEquals(largeNum1.hashCode(), largeNum2.hashCode());

    // Small Numbers
    Decimal128Value smallNum1 =
        new Decimal128Value(
            "0.0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001");
    Decimal128Value smallNum2 = new Decimal128Value("1.0e-100");
    assertEquals(smallNum1, smallNum2);
    assertEquals(smallNum1.hashCode(), smallNum2.hashCode());
  }

  @Test
  public void testMaxKeyIsSingleton() {
    MaxKey maxKey = MaxKey.instance();
    MaxKey maxKeyDup = MaxKey.instance();
    assertEquals(maxKey, maxKeyDup);
    assertEquals(maxKey.hashCode(), maxKeyDup.hashCode());
  }

  @Test
  public void testMinKeyIsSingleton() {
    MinKey minKey = MinKey.instance();
    MinKey minKeyDup = MinKey.instance();
    assertEquals(minKey, minKeyDup);
    assertEquals(minKey.hashCode(), minKeyDup.hashCode());
  }

  @Test
  public void testMinKeyMaxKeyNullNotEqual() {
    MinKey minKey = MinKey.instance();
    MaxKey maxKey = MaxKey.instance();
    assertNotEquals(minKey, maxKey);
    assertNotEquals(minKey, null);
    assertNotEquals(maxKey, null);
  }

  @Test
  public void testThrows() {
    assertThrows(
        IllegalArgumentException.class, () -> BsonBinaryData.fromBytes(256, new byte[] {1}));
  }
}
