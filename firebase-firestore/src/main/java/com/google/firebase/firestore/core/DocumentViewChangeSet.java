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

import static com.google.firebase.firestore.util.Assert.fail;

import com.google.firebase.firestore.core.DocumentViewChange.Type;
import com.google.firebase.firestore.model.DocumentKey;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** A set of changes to documents with respect to a view. This set is mutable. */
public class DocumentViewChangeSet {
  // This map is sorted to make the unit tests simpler.
  private final TreeMap<DocumentKey, DocumentViewChange> changes;

  public DocumentViewChangeSet() {
    changes = new TreeMap<>();
  }

  public void addChange(DocumentViewChange change) {
    DocumentKey key = change.getDocument().getKey();
    DocumentViewChange old = changes.get(key);
    if (old == null) {
      changes.put(key, change);
      return;
    }

    Type oldType = old.getType();
    Type newType = change.getType();
    if (newType != Type.ADDED && oldType == Type.METADATA) {
      changes.put(key, change);
    } else if (newType == Type.METADATA && oldType != Type.REMOVED) {
      DocumentViewChange newChange = DocumentViewChange.create(oldType, change.getDocument());
      changes.put(key, newChange);
    } else if (newType == Type.MODIFIED && oldType == Type.MODIFIED) {
      DocumentViewChange newChange = DocumentViewChange.create(Type.MODIFIED, change.getDocument());
      changes.put(key, newChange);
    } else if (newType == Type.MODIFIED && oldType == Type.ADDED) {
      DocumentViewChange newChange = DocumentViewChange.create(Type.ADDED, change.getDocument());
      changes.put(key, newChange);
    } else if (newType == Type.REMOVED && oldType == Type.ADDED) {
      changes.remove(key);
    } else if (newType == Type.REMOVED && oldType == Type.MODIFIED) {
      DocumentViewChange newChange = DocumentViewChange.create(Type.REMOVED, old.getDocument());
      changes.put(key, newChange);
    } else if (newType == Type.ADDED && oldType == Type.REMOVED) {
      DocumentViewChange newChange = DocumentViewChange.create(Type.MODIFIED, change.getDocument());
      changes.put(key, newChange);
    } else {
      // This includes these cases, which don't make sense:
      // Added -> Added
      // Removed -> Removed
      // Modified -> Added
      // Removed -> Modified
      // Metadata -> Added
      // Removed -> Metadata
      throw fail("Unsupported combination of changes %s after %s", newType, oldType);
    }
  }

  List<DocumentViewChange> getChanges() {
    return new ArrayList<>(changes.values());
  }
}
