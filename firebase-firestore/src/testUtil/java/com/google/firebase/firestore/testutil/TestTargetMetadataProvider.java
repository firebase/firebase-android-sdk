// Copyright 2019 Google LLC
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

package com.google.firebase.firestore.testutil;

import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.local.TargetData;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.remote.WatchChangeAggregator;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of TargetMetadataProvider that provides controlled access to the
 * `TargetMetadataProvider` callbacks. Any target accessed via these callbacks must be registered
 * beforehand via `setSyncedKeys()`.
 */
public class TestTargetMetadataProvider implements WatchChangeAggregator.TargetMetadataProvider {
  final Map<Integer, ImmutableSortedSet<DocumentKey>> syncedKeys = new HashMap<>();
  final Map<Integer, TargetData> queryData = new HashMap<>();

  @Override
  public ImmutableSortedSet<DocumentKey> getRemoteKeysForTarget(int targetId) {
    return syncedKeys.get(targetId) != null ? syncedKeys.get(targetId) : DocumentKey.emptyKeySet();
  }

  @androidx.annotation.Nullable
  @Override
  public TargetData getTargetDataForTarget(int targetId) {
    return queryData.get(targetId);
  }

  /** Sets or replaces the local state for the provided query data. */
  public void setSyncedKeys(TargetData targetData, ImmutableSortedSet<DocumentKey> keys) {
    this.queryData.put(targetData.getTargetId(), targetData);
    this.syncedKeys.put(targetData.getTargetId(), keys);
  }
}
