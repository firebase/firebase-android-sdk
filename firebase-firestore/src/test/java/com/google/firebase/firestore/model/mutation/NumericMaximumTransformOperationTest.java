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
import static org.junit.Assert.assertNull;
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
public class NumericMaximumTransformOperationTest {

  private Value apply(NumericMaximumTransformOperation op, Object previousValue) {
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
        AssertionError.class, () -> new NumericMaximumTransformOperation(wrap("non-numeric")));
    assertThrows(AssertionError.class, () -> new NumericMaximumTransformOperation(wrap(true)));
    assertThrows(
        AssertionError.class, () -> new NumericMaximumTransformOperation(Values.NULL_VALUE));
  }

  @Test
  public void testEqualsAndHashCode() {
    new EqualsTester()
        .addEqualityGroup(
            new NumericMaximumTransformOperation(wrap(1L)),
            new NumericMaximumTransformOperation(wrap(1L)))
        .addEqualityGroup(
            new NumericMaximumTransformOperation(wrap(1.0)),
            new NumericMaximumTransformOperation(wrap(1.0)))
        .addEqualityGroup(
            new NumericMaximumTransformOperation(wrap(new Int32Value(1))),
            new NumericMaximumTransformOperation(wrap(new Int32Value(1))))
        .addEqualityGroup(
            new NumericMaximumTransformOperation(wrap(new Decimal128Value("1"))),
            new NumericMaximumTransformOperation(wrap(new Decimal128Value("1"))))
        .addEqualityGroup(new NumericMaximumTransformOperation(wrap(2L)))
        .testEquals();
  }

  @Test
  public void testComputeBaseValueReturnsNull() {
    NumericMaximumTransformOperation op = new NumericMaximumTransformOperation(wrap(5L));
    assertNull(op.computeBaseValue(wrap(10L)));
    assertNull(op.computeBaseValue(null));
  }

  @Test
  public void testMaximumInt32AndInt32() {
    NumericMaximumTransformOperation op15 =
        new NumericMaximumTransformOperation(wrap(new Int32Value(15)));
    assertEquals(wrap(new Int32Value(15)), apply(op15, new Int32Value(10)));

    NumericMaximumTransformOperation op5 =
        new NumericMaximumTransformOperation(wrap(new Int32Value(5)));
    assertEquals(wrap(new Int32Value(10)), apply(op5, new Int32Value(10)));

    // Equal values return previousValue
    assertEquals(wrap(new Int32Value(5)), apply(op5, new Int32Value(5)));
  }

  @Test
  public void testMaximumAcrossTypes() {
    // Int32 and Integer
    NumericMaximumTransformOperation opInt32_15 =
        new NumericMaximumTransformOperation(wrap(new Int32Value(15)));
    assertEquals(wrap(new Int32Value(15)), apply(opInt32_15, 10L));

    NumericMaximumTransformOperation opInt15 = new NumericMaximumTransformOperation(wrap(15L));
    assertEquals(wrap(15L), apply(opInt15, new Int32Value(10)));

    // Int32 and Double
    assertEquals(wrap(new Int32Value(15)), apply(opInt32_15, 10.5));
    NumericMaximumTransformOperation opDouble15 = new NumericMaximumTransformOperation(wrap(15.0));
    assertEquals(wrap(15.0), apply(opDouble15, new Int32Value(10)));

    // Int32 and Decimal128
    NumericMaximumTransformOperation opDec15 =
        new NumericMaximumTransformOperation(wrap(new Decimal128Value("15")));
    assertEquals(wrap(new Decimal128Value("15")), apply(opDec15, new Int32Value(10)));
    assertEquals(wrap(new Int32Value(15)), apply(opInt32_15, new Decimal128Value("10")));

    // Double and Decimal128
    assertEquals(wrap(new Decimal128Value("15")), apply(opDec15, 10.5));
    assertEquals(wrap(15.0), apply(opDouble15, new Decimal128Value("10")));

    // Integer and Decimal128
    assertEquals(wrap(new Decimal128Value("15")), apply(opDec15, 10L));
    assertEquals(wrap(15L), apply(opInt15, new Decimal128Value("10")));
  }

  @Test
  public void testMaximumWithNaN() {
    // The maximum of any numeric value x and NaN is NaN
    NumericMaximumTransformOperation opNaN = new NumericMaximumTransformOperation(wrap(Double.NaN));
    assertEquals(wrap(Double.NaN), apply(opNaN, 10L));
    assertEquals(wrap(Double.NaN), apply(opNaN, new Int32Value(10)));
    assertEquals(wrap(Double.NaN), apply(opNaN, new Decimal128Value("10")));

    NumericMaximumTransformOperation opDecNaN =
        new NumericMaximumTransformOperation(wrap(new Decimal128Value("NaN")));
    assertEquals(wrap(new Decimal128Value("NaN")), apply(opDecNaN, 10L));
    assertEquals(wrap(new Decimal128Value("NaN")), apply(opDecNaN, new Int32Value(10)));

    NumericMaximumTransformOperation opInt = new NumericMaximumTransformOperation(wrap(10L));
    assertEquals(wrap(Double.NaN), apply(opInt, Double.NaN));
    assertEquals(wrap(new Decimal128Value("NaN")), apply(opInt, new Decimal128Value("NaN")));

    // max(NaN, NaN) returns previousValue
    assertEquals(wrap(Double.NaN), apply(opNaN, Double.NaN));
  }

  @Test
  public void testMaximumNonNumericOrMissingBaseSelectsOperand() {
    NumericMaximumTransformOperation op =
        new NumericMaximumTransformOperation(wrap(new Int32Value(5)));
    assertEquals(wrap(new Int32Value(5)), apply(op, null));
    assertEquals(wrap(new Int32Value(5)), apply(op, "hello"));
    assertEquals(wrap(new Int32Value(5)), apply(op, true));
    assertEquals(wrap(new Int32Value(5)), apply(op, Values.NULL_VALUE));

    NumericMaximumTransformOperation opDec =
        new NumericMaximumTransformOperation(wrap(new Decimal128Value("10.5")));
    assertEquals(wrap(new Decimal128Value("10.5")), apply(opDec, null));
    assertEquals(wrap(new Decimal128Value("10.5")), apply(opDec, "hello"));
  }
}
