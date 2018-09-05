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

/** A wrapper for string values in Firestore. */
// TODO: Add truncation support
public class StringValue extends FieldValue {
  private final String internalValue;

  private StringValue(String s) {
    internalValue = s;
  }

  @Override
  public int typeOrder() {
    return TYPE_ORDER_STRING;
  }

  @Override
  public String value() {
    return internalValue;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof StringValue && internalValue.equals(((StringValue) o).internalValue);
  }

  @Override
  public int hashCode() {
    return internalValue.hashCode();
  }

  @Override
  public int compareTo(FieldValue o) {
    if (o instanceof StringValue) {
      return internalValue.compareTo(((StringValue) o).internalValue);
    } else {
      return defaultCompareTo(o);
    }
  }

  public static StringValue valueOf(String s) {
    return new StringValue(s);
  }
}
