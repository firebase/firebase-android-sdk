// Copyright 2024 Google LLC
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

package com.google.firebase.firestore;

/**
 * Configures the source option of {@code addSnapshotListener()} calls on {@link DocumentReference}
 * and {@link Query}. This controls how a listener retrieves data updates.
 */
public enum ListenSource {
  /**
   * The default behavior. The listener attempts to return initial snapshot from cache and retrieve
   * up-to-date snapshots from the Firestore server. Snapshot events will be triggered on local
   * mutations and server side updates.
   */
  DEFAULT,

  /**
   * The listener retrieves data and listens to updates from the local Firestore cache only. If the
   * cache is empty, an empty snapshot will be returned. Snapshot events will be triggered on cache
   * updates, like local mutations or load bundles.
   *
   * <p>Note that the data might be stale if the cache hasn't synchronized with recent server-side
   * changes.
   */
  CACHE
}
