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

import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.DocumentKey;

/** A wrapper for reference values in Firestore. */
public class ReferenceValue extends FieldValue {
  private final DatabaseId databaseId;
  private final DocumentKey key;

  private ReferenceValue(DatabaseId databaseId, DocumentKey documentKey) {
    this.databaseId = databaseId;
    key = documentKey;
  }

  @Override
  public int typeOrder() {
    return TYPE_ORDER_REFERENCE;
  }

  @Override
  public DocumentKey value() {
    return key;
  }

  public DatabaseId getDatabaseId() {
    return databaseId;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ReferenceValue) {
      ReferenceValue ref = (ReferenceValue) o;
      return key.equals(ref.key) && databaseId.equals(ref.databaseId);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    int result = 31;
    result = 31 * result + databaseId.hashCode();
    result = 31 * result + key.hashCode();
    return result;
  }

  @Override
  public int compareTo(FieldValue o) {
    if (o instanceof ReferenceValue) {
      ReferenceValue ref = (ReferenceValue) o;
      int cmp = databaseId.compareTo(ref.databaseId);
      return cmp != 0 ? cmp : key.compareTo(ref.key);
    } else {
      return defaultCompareTo(o);
    }
  }

  public static ReferenceValue valueOf(DatabaseId databaseId, DocumentKey documentKey) {
    return new ReferenceValue(databaseId, documentKey);
  }
}
