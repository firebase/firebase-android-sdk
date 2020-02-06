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
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.util.Assert;
import com.google.firestore.v1.Value;

/** A wrapper for reference values in Firestore. */
public class ReferenceValue extends FieldValue {
  private final DatabaseId databaseId;
  private final DocumentKey key;

  ReferenceValue(Value value) {
    super(value);

    ResourcePath resourceName = ResourcePath.fromString(value.getReferenceValue());
    Assert.hardAssert(
        resourceName.length() >= 4
            && resourceName.getSegment(0).equals("projects")
            && resourceName.getSegment(2).equals("databases")
            && resourceName.getSegment(4).equals("documents"),
        "Tried to create ReferenceValue from invalid key: %s",
        resourceName);
    databaseId = DatabaseId.forDatabase(resourceName.getSegment(1), resourceName.getSegment(3));
    key = DocumentKey.fromPath(resourceName.popFirst(5));
  }

  public DocumentKey getKey() {
    return key;
  }

  public DatabaseId getDatabaseId() {
    return databaseId;
  }

  public static ReferenceValue valueOf(DatabaseId databaseId, DocumentKey key) {
    Value value =
        Value.newBuilder()
            .setReferenceValue(
                String.format(
                    "projects/%s/databases/%s/documents/%s",
                    databaseId.getProjectId(), databaseId.getDatabaseId(), key.toString()))
            .build();
    return new ReferenceValue(value);
  }
}
