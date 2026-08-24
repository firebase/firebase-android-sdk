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
public class NumericMinimumTransformOperationTest {

  private Value apply(NumericMinimumTransformOperation op, Object previousValue) {
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
        AssertionError.class, () -> new NumericMinimumTransformOperation(wrap("non-numeric")));
    assertThrows(AssertionError.class, () -> new NumericMinimumTransformOperation(wrap(true)));
    assertThrows(
        AssertionError.class, () -> new NumericMinimumTransformOperation(Values.NULL_VALUE));
  }

  @Test
  public void testEqualsAndHashCode() {
    new EqualsTester()
        .addEqualityGroup(
            new NumericMinimumTransformOperation(wrap(1L)),
            new NumericMinimumTransformOperation(wrap(1L)))
        .addEqualityGroup(
            new NumericMinimumTransformOperation(wrap(1.0)),
            new NumericMinimumTransformOperation(wrap(1.0)))
        .addEqualityGroup(
            new NumericMinimumTransformOperation(wrap(new Int32Value(1))),
            new NumericMinimumTransformOperation(wrap(new Int32Value(1))))
        .addEqualityGroup(
            new NumericMinimumTransformOperation(wrap(new Decimal128Value("1"))),
            new NumericMinimumTransformOperation(wrap(new Decimal128Value("1"))))
        .addEqualityGroup(new NumericMinimumTransformOperation(wrap(2L)))
        .testEquals();
  }

  @Test
  public void testComputeBaseValueReturnsNull() {
    NumericMinimumTransformOperation op = new NumericMinimumTransformOperation(wrap(5L));
    assertNull(op.computeBaseValue(wrap(10L)));
    assertNull(op.computeBaseValue(null));
  }

  @Test
  public void testMinimumInt32AndInt32() {
    NumericMinimumTransformOperation op5 =
        new NumericMinimumTransformOperation(wrap(new Int32Value(5)));
    assertEquals(wrap(new Int32Value(5)), apply(op5, new Int32Value(10)));

    NumericMinimumTransformOperation op15 =
        new NumericMinimumTransformOperation(wrap(new Int32Value(15)));
    assertEquals(wrap(new Int32Value(10)), apply(op15, new Int32Value(10)));

    // Equal values return previousValue
    assertEquals(wrap(new Int32Value(5)), apply(op5, new Int32Value(5)));
  }

  @Test
  public void testMinimumAcrossTypes() {
    // Int32 and Integer
    NumericMinimumTransformOperation opInt32_5 =
        new NumericMinimumTransformOperation(wrap(new Int32Value(5)));
    assertEquals(wrap(new Int32Value(5)), apply(opInt32_5, 10L));

    NumericMinimumTransformOperation opInt5 = new NumericMinimumTransformOperation(wrap(5L));
    assertEquals(wrap(5L), apply(opInt5, new Int32Value(10)));

    // Int32 and Double
    assertEquals(wrap(new Int32Value(5)), apply(opInt32_5, 10.5));
    NumericMinimumTransformOperation opDouble5 = new NumericMinimumTransformOperation(wrap(5.0));
    assertEquals(wrap(5.0), apply(opDouble5, new Int32Value(10)));

    // Int32 and Decimal128
    NumericMinimumTransformOperation opDec5 =
        new NumericMinimumTransformOperation(wrap(new Decimal128Value("5")));
    assertEquals(wrap(new Decimal128Value("5")), apply(opDec5, new Int32Value(10)));
    assertEquals(wrap(new Int32Value(5)), apply(opInt32_5, new Decimal128Value("10")));

    // Double and Decimal128
    assertEquals(wrap(new Decimal128Value("5")), apply(opDec5, 10.5));
    assertEquals(wrap(5.0), apply(opDouble5, new Decimal128Value("10")));

    // Integer and Decimal128
    assertEquals(wrap(new Decimal128Value("5")), apply(opDec5, 10L));
    assertEquals(wrap(5L), apply(opInt5, new Decimal128Value("10")));
  }

  @Test
  public void testMinimumWithNaN() {
    // NaN is smaller than all numbers in Firestore ordering
    NumericMinimumTransformOperation opNaN = new NumericMinimumTransformOperation(wrap(Double.NaN));
    assertEquals(wrap(Double.NaN), apply(opNaN, 10L));
    assertEquals(wrap(Double.NaN), apply(opNaN, new Int32Value(10)));
    assertEquals(wrap(Double.NaN), apply(opNaN, new Decimal128Value("10")));

    NumericMinimumTransformOperation opDecNaN =
        new NumericMinimumTransformOperation(wrap(new Decimal128Value("NaN")));
    assertEquals(wrap(new Decimal128Value("NaN")), apply(opDecNaN, 10L));
    assertEquals(wrap(new Decimal128Value("NaN")), apply(opDecNaN, new Int32Value(10)));

    NumericMinimumTransformOperation opInt = new NumericMinimumTransformOperation(wrap(10L));
    assertEquals(wrap(Double.NaN), apply(opInt, Double.NaN));
    assertEquals(wrap(new Decimal128Value("NaN")), apply(opInt, new Decimal128Value("NaN")));
  }

  @Test
  public void testMinimumNonNumericOrMissingBaseSelectsOperand() {
    NumericMinimumTransformOperation op =
        new NumericMinimumTransformOperation(wrap(new Int32Value(5)));
    assertEquals(wrap(new Int32Value(5)), apply(op, null));
    assertEquals(wrap(new Int32Value(5)), apply(op, "hello"));
    assertEquals(wrap(new Int32Value(5)), apply(op, true));
    assertEquals(wrap(new Int32Value(5)), apply(op, Values.NULL_VALUE));

    NumericMinimumTransformOperation opDec =
        new NumericMinimumTransformOperation(wrap(new Decimal128Value("5")));
    assertEquals(wrap(new Decimal128Value("5")), apply(opDec, null));
    assertEquals(wrap(new Decimal128Value("5")), apply(opDec, "hello"));
  }
}
