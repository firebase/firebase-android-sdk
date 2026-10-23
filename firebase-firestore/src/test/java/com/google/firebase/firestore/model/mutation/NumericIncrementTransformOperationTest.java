// Copyright 2026 Google LLC
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

package com.google.firebase.firestore.model.mutation;

import static com.google.firebase.firestore.testutil.TestUtil.wrap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.common.testing.EqualsTester;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Decimal128Value;
import com.google.firebase.firestore.Int32Value;
import com.google.firebase.firestore.model.Values;
import com.google.firestore.v1.Value;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class NumericIncrementTransformOperationTest {

  private Value apply(NumericIncrementTransformOperation op, Object previousValue) {
    Value prev;
    if (previousValue instanceof Value) {
      prev = (Value) previousValue;
    } else if (previousValue != null) {
      prev = wrap(previousValue);
    } else {
      prev = null;
    }
    return op.applyToLocalView(prev, Timestamp.now());
  }

  @Test
  public void testConstructorRequiresNumericOperand() {
    assertThrows(
        AssertionError.class, () -> new NumericIncrementTransformOperation(wrap("non-numeric")));
    assertThrows(AssertionError.class, () -> new NumericIncrementTransformOperation(wrap(true)));
    assertThrows(
        AssertionError.class, () -> new NumericIncrementTransformOperation(Values.NULL_VALUE));
  }

  @Test
  public void testEqualsAndHashCode() {
    new EqualsTester()
        .addEqualityGroup(
            new NumericIncrementTransformOperation(wrap(1L)),
            new NumericIncrementTransformOperation(wrap(1L)))
        .addEqualityGroup(
            new NumericIncrementTransformOperation(wrap(1.0)),
            new NumericIncrementTransformOperation(wrap(1.0)))
        .addEqualityGroup(
            new NumericIncrementTransformOperation(wrap(new Int32Value(1))),
            new NumericIncrementTransformOperation(wrap(new Int32Value(1))))
        .addEqualityGroup(
            new NumericIncrementTransformOperation(wrap(new Decimal128Value("1"))),
            new NumericIncrementTransformOperation(wrap(new Decimal128Value("1"))))
        .addEqualityGroup(new NumericIncrementTransformOperation(wrap(2L)))
        .testEquals();
  }

  @Test
  public void testComputeBaseValue() {
    NumericIncrementTransformOperation op = new NumericIncrementTransformOperation(wrap(1L));

    assertEquals(wrap(10L), op.computeBaseValue(wrap(10L)));
    assertEquals(wrap(10.5), op.computeBaseValue(wrap(10.5)));
    assertEquals(wrap(new Int32Value(10)), op.computeBaseValue(wrap(new Int32Value(10))));
    assertEquals(
        wrap(new Decimal128Value("10")), op.computeBaseValue(wrap(new Decimal128Value("10"))));

    assertEquals(wrap(0L), op.computeBaseValue(null));
    assertEquals(wrap(0L), op.computeBaseValue(wrap("hello")));
    assertEquals(wrap(0L), op.computeBaseValue(wrap(true)));
    assertEquals(wrap(0L), op.computeBaseValue(Values.NULL_VALUE));
  }

  @Test
  public void testIncrementInt32AndInt32() {
    NumericIncrementTransformOperation op =
        new NumericIncrementTransformOperation(wrap(new Int32Value(5)));
    assertEquals(wrap(new Int32Value(15)), apply(op, new Int32Value(10)));
    assertEquals(wrap(new Int32Value(5)), apply(op, new Int32Value(0)));
    assertEquals(wrap(new Int32Value(0)), apply(op, new Int32Value(-5)));
  }

  @Test
  public void testIncrementInt32Saturation() {
    NumericIncrementTransformOperation opPositive =
        new NumericIncrementTransformOperation(wrap(new Int32Value(1)));
    assertEquals(
        wrap(new Int32Value(Integer.MAX_VALUE)),
        apply(opPositive, new Int32Value(Integer.MAX_VALUE)));
    assertEquals(
        wrap(new Int32Value(Integer.MAX_VALUE)),
        apply(
            new NumericIncrementTransformOperation(wrap(new Int32Value(100))),
            new Int32Value(Integer.MAX_VALUE - 10)));

    NumericIncrementTransformOperation opNegative =
        new NumericIncrementTransformOperation(wrap(new Int32Value(-1)));
    assertEquals(
        wrap(new Int32Value(Integer.MIN_VALUE)),
        apply(opNegative, new Int32Value(Integer.MIN_VALUE)));
    assertEquals(
        wrap(new Int32Value(Integer.MIN_VALUE)),
        apply(
            new NumericIncrementTransformOperation(wrap(new Int32Value(-100))),
            new Int32Value(Integer.MIN_VALUE + 10)));
  }

  @Test
  public void testIncrementInt32BaseWithOtherOperandTypes() {
    // Int32 base + Integer operand -> Integer
    NumericIncrementTransformOperation opInt = new NumericIncrementTransformOperation(wrap(5L));
    assertEquals(wrap(15L), apply(opInt, new Int32Value(10)));

    // Int32 base + Double operand -> Double
    NumericIncrementTransformOperation opDouble = new NumericIncrementTransformOperation(wrap(2.5));
    assertEquals(wrap(12.5), apply(opDouble, new Int32Value(10)));

    // Int32 base + Decimal128 operand -> Decimal128
    NumericIncrementTransformOperation opDec =
        new NumericIncrementTransformOperation(wrap(new Decimal128Value("5")));
    assertEquals(wrap(new Decimal128Value("15")), apply(opDec, new Int32Value(10)));
  }

  @Test
  public void testIncrementIntegerBaseWithInt32Operand() {
    NumericIncrementTransformOperation op =
        new NumericIncrementTransformOperation(wrap(new Int32Value(5)));
    assertEquals(wrap(15L), apply(op, 10L));
  }

  @Test
  public void testIncrementIntegerSaturation() {
    NumericIncrementTransformOperation opPositive =
        new NumericIncrementTransformOperation(wrap(1L));
    assertEquals(wrap(Long.MAX_VALUE), apply(opPositive, Long.MAX_VALUE));

    NumericIncrementTransformOperation opNegative =
        new NumericIncrementTransformOperation(wrap(-1L));
    assertEquals(wrap(Long.MIN_VALUE), apply(opNegative, Long.MIN_VALUE));

    // Integer base + Int32 operand saturation
    NumericIncrementTransformOperation opInt32 =
        new NumericIncrementTransformOperation(wrap(new Int32Value(1)));
    assertEquals(wrap(Long.MAX_VALUE), apply(opInt32, Long.MAX_VALUE));
  }

  @Test
  public void testIncrementDoubleBaseWithInt32Operand() {
    NumericIncrementTransformOperation op =
        new NumericIncrementTransformOperation(wrap(new Int32Value(5)));
    assertEquals(wrap(15.5), apply(op, 10.5));
  }

  @Test
  public void testIncrementDecimal128Approximation() {
    // Decimal128 base + Integer operand -> Decimal128
    NumericIncrementTransformOperation opInt = new NumericIncrementTransformOperation(wrap(5L));
    assertEquals(wrap(new Decimal128Value("15")), apply(opInt, new Decimal128Value("10")));

    // Decimal128 base + Double operand -> Decimal128
    NumericIncrementTransformOperation opDouble = new NumericIncrementTransformOperation(wrap(5.5));
    assertEquals(wrap(new Decimal128Value("15.5")), apply(opDouble, new Decimal128Value("10")));

    // Decimal128 base + Int32 operand -> Decimal128
    NumericIncrementTransformOperation opInt32 =
        new NumericIncrementTransformOperation(wrap(new Int32Value(5)));
    assertEquals(wrap(new Decimal128Value("15")), apply(opInt32, new Decimal128Value("10")));

    // Decimal128 base + Decimal128 operand -> Decimal128
    NumericIncrementTransformOperation opDec =
        new NumericIncrementTransformOperation(wrap(new Decimal128Value("5")));
    assertEquals(wrap(new Decimal128Value("15")), apply(opDec, new Decimal128Value("10")));

    // Integer base + Decimal128 operand -> Decimal128
    assertEquals(wrap(new Decimal128Value("15")), apply(opDec, 10L));

    // Double base + Decimal128 operand -> Decimal128
    assertEquals(wrap(new Decimal128Value("15.5")), apply(opDec, 10.5));

    // Decimal128 with NaN
    NumericIncrementTransformOperation opNan =
        new NumericIncrementTransformOperation(wrap(new Decimal128Value("NaN")));
    assertEquals(wrap(new Decimal128Value("NaN")), apply(opNan, 10L));
    assertEquals(wrap(new Decimal128Value("NaN")), apply(opInt, new Decimal128Value("NaN")));

    // Decimal128 with Infinity
    NumericIncrementTransformOperation opInf =
        new NumericIncrementTransformOperation(wrap(new Decimal128Value("Infinity")));
    assertEquals(wrap(new Decimal128Value("Infinity")), apply(opInf, 10L));

    // Decimal128 with -Infinity
    NumericIncrementTransformOperation opNegInf =
        new NumericIncrementTransformOperation(wrap(new Decimal128Value("-Infinity")));
    assertEquals(wrap(new Decimal128Value("-Infinity")), apply(opNegInf, 10L));
  }

  @Test
  public void testIncrementMissingOrNonNumericBase() {
    // Missing base -> 0L + operand
    NumericIncrementTransformOperation opInt = new NumericIncrementTransformOperation(wrap(5L));
    assertEquals(wrap(5L), apply(opInt, null));

    NumericIncrementTransformOperation opDouble = new NumericIncrementTransformOperation(wrap(5.5));
    assertEquals(wrap(5.5), apply(opDouble, null));

    NumericIncrementTransformOperation opInt32 =
        new NumericIncrementTransformOperation(wrap(new Int32Value(5)));
    assertEquals(wrap(5L), apply(opInt32, null));

    NumericIncrementTransformOperation opDec =
        new NumericIncrementTransformOperation(wrap(new Decimal128Value("5")));
    assertEquals(wrap(new Decimal128Value("5")), apply(opDec, null));

    // Non-numeric base -> 0L + operand
    assertEquals(wrap(5L), apply(opInt, "hello"));
    assertEquals(wrap(5.5), apply(opDouble, "hello"));
    assertEquals(wrap(5L), apply(opInt32, "hello"));
    assertEquals(wrap(new Decimal128Value("5")), apply(opDec, "hello"));
  }
}
