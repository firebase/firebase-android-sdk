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

package com.google.firebase.firestore.local;

import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.IndexRange;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firestore.v1.Value;

/**
 * A persisted "collection index" of all documents in the local cache (with mutations overlaid on
 * top of remote documents).
 *
 * <p>NOTE: There is no in-memory implementation at this time.
 */
public class SQLiteCollectionIndex {

  private final SQLitePersistence db;
  private final String uid;

  SQLiteCollectionIndex(SQLitePersistence persistence, User user) {
    this.db = persistence;
    this.uid = user.isAuthenticated() ? user.getUid() : "";
  }

  /** Adds the specified entry to the index. */
  public void addEntry(FieldPath fieldPath, Value fieldValue, DocumentKey documentKey) {
    throw new RuntimeException("Not yet implemented.");
  }

  /** Adds the specified entry to the index. */
  public void removeEntry(FieldPath fieldPath, Value fieldValue, DocumentKey documentKey) {
    throw new RuntimeException("Not yet implemented.");
  }

  /**
   * Gets a forward or reverse cursor for the specified range of the index. Since index entries are
   * lossy, some cursor results may not match the specified range, so the consumer must always
   * post-filter the results.
   */
  public IndexCursor getCursor(ResourcePath collectionPath, IndexRange indexRange) {
    throw new RuntimeException("Not yet implemented.");
  }
}
