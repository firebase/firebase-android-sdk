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

import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.DocumentViewChange;
import com.google.firebase.firestore.core.ViewSnapshot;
import com.google.firebase.firestore.model.DocumentKey;
import java.util.ArrayList;

/**
 * A set of changes to what documents are currently in view and out of view for a given query. These
 * changes are sent to the LocalStore by the View (via the SyncEngine) and are used to pin / unpin
 * documents as appropriate.
 */
public final class LocalViewChanges {

  public static LocalViewChanges fromViewSnapshot(int targetId, ViewSnapshot snapshot) {
    ImmutableSortedSet<DocumentKey> addedKeys =
        new ImmutableSortedSet<DocumentKey>(new ArrayList<>(), DocumentKey.comparator());
    ImmutableSortedSet<DocumentKey> removedKeys =
        new ImmutableSortedSet<DocumentKey>(new ArrayList<>(), DocumentKey.comparator());

    for (DocumentViewChange docChange : snapshot.getChanges()) {
      switch (docChange.getType()) {
        case ADDED:
          addedKeys = addedKeys.insert(docChange.getDocument().getKey());
          break;

        case REMOVED:
          removedKeys = removedKeys.insert(docChange.getDocument().getKey());
          break;

        default:
          // Do nothing.
          break;
      }
    }

    return new LocalViewChanges(targetId, snapshot.isFromCache(), addedKeys, removedKeys);
  }

  private final int targetId;
  private final boolean fromCache;

  private final ImmutableSortedSet<DocumentKey> added;
  private final ImmutableSortedSet<DocumentKey> removed;

  public LocalViewChanges(
      int targetId,
      boolean fromCache,
      ImmutableSortedSet<DocumentKey> added,
      ImmutableSortedSet<DocumentKey> removed) {
    this.targetId = targetId;
    this.fromCache = fromCache;
    this.added = added;
    this.removed = removed;
  }

  public int getTargetId() {
    return targetId;
  }

  public boolean isFromCache() {
    return fromCache;
  }

  public ImmutableSortedSet<DocumentKey> getAdded() {
    return added;
  }

  public ImmutableSortedSet<DocumentKey> getRemoved() {
    return removed;
  }
}
