// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.conformance;

import java.util.HashMap;
import java.util.Map;

/** A simplified representation of a Firestore document for testing. */
public final class TestDocument {
  // Note: This code is copied from Google3.

  private final String id;
  private final HashMap<String, Object> fields = new HashMap<>();

  TestDocument(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }

  public Map<String, Object> fields() {
    return fields;
  }

  public void putField(String name, Object value) {
    fields.put(name, value);
  }
}
