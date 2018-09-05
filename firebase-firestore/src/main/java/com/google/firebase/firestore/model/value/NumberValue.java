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

package com.google.firebase.firestore.model.value;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.firebase.firestore.util.Util;

/**
 * Base class inherited from by IntegerValue and DoubleValue. It implements proper number
 * comparisons between the two types.
 */
public abstract class NumberValue extends FieldValue {

  @Override
  public int typeOrder() {
    return TYPE_ORDER_NUMBER;
  }

  @Override
  public int compareTo(FieldValue o) {
    if (!(o instanceof NumberValue)) {
      return defaultCompareTo(o);
    }
    if (this instanceof DoubleValue) {
      double thisDouble = ((DoubleValue) this).getInternalValue();
      if (o instanceof DoubleValue) {
        return Util.compareDoubles(thisDouble, ((DoubleValue) o).getInternalValue());
      } else {
        hardAssert(o instanceof IntegerValue, "Unknown NumberValue: %s", o);
        return Util.compareMixed(thisDouble, ((IntegerValue) o).getInternalValue());
      }
    } else {
      hardAssert(this instanceof IntegerValue, "Unknown NumberValue: %s", this);
      long thisLong = ((IntegerValue) this).getInternalValue();
      if (o instanceof IntegerValue) {
        return Util.compareLongs(thisLong, ((IntegerValue) o).getInternalValue());
      } else {
        hardAssert(o instanceof DoubleValue, "Unknown NumberValue: %s", o);
        return -1 * Util.compareMixed(((DoubleValue) o).getInternalValue(), thisLong);
      }
    }
  }
}
