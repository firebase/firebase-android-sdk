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

package com.google.firebase.firestore.core;

import com.google.firebase.firestore.model.DocumentKey;

/** change to a particular document wrt to whether it is in "limbo". */
public class LimboDocumentChange {
  /** The type of the change. */
  public enum Type {
    ADDED,
    REMOVED,
  }

  private final Type type;

  private final DocumentKey key;

  public LimboDocumentChange(Type type, DocumentKey key) {
    this.type = type;
    this.key = key;
  }

  public Type getType() {
    return type;
  }

  public DocumentKey getKey() {
    return key;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LimboDocumentChange)) {
      return false;
    }
    LimboDocumentChange other = (LimboDocumentChange) o;
    return type.equals(other.getType()) && key.equals(other.getKey());
  }

  @Override
  public int hashCode() {
    int res = 67;
    res = res * 31 + type.hashCode();
    res = res * 31 + key.hashCode();
    return res;
  }
}
