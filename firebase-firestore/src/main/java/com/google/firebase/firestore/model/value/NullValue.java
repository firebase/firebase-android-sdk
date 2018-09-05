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

import javax.annotation.Nullable;

/** A wrapper for null values in Firestore. */
public class NullValue extends FieldValue {
  private static final NullValue INSTANCE = new NullValue();

  private NullValue() {}

  @Override
  public int typeOrder() {
    return TYPE_ORDER_NULL;
  }

  @Override
  @Nullable
  public Object value() {
    return null;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof NullValue;
  }

  @Override
  public int hashCode() {
    return -1;
  }

  @Override
  public int compareTo(FieldValue other) {
    if (other instanceof NullValue) {
      return 0;
    } else {
      return defaultCompareTo(other);
    }
  }

  public static NullValue nullValue() {
    return INSTANCE;
  }
}
