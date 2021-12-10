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

package com.google.firebase.firestore.local;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.Overlay;
import java.util.Map;

/**
 * Provides methods to read and write document overlays.
 *
 * <p>An overlay is a saved {@link Mutation}, that gives a local view of a document when applied to
 * the remote version of the document.
 *
 * <p>Each overlay stores the largest batch ID that is included in the overlay, which allows us to
 * remove the overlay once all batches leading up to it have been acknowledged.
 */
public interface DocumentOverlayCache {
  /**
   * Gets the saved overlay mutation for the given document key. Returns null if there is no overlay
   * for that key.
   */
  @Nullable
  Overlay getOverlay(DocumentKey key);

  /**
   * Saves the given document key to mutation map to persistence as overlays. All overlays will have
   * their largest batch id set to {@code largestBatchId}.
   */
  void saveOverlays(int largestBatchId, Map<DocumentKey, Mutation> overlays);

  /** Removes the overlay whose largest-batch-id equals to the given Id. */
  void removeOverlaysForBatchId(int batchId);

  /**
   * Returns all saved overlays for the given collection.
   *
   * @param collection The collection path to get the overlays for.
   * @param sinceBatchId The minimum batch ID to filter by (exclusive). Only overlays that contain a
   *     change past `sinceBatchId` are returned.
   * @return Mapping of each document key in the collection to its overlay.
   */
  Map<DocumentKey, Overlay> getOverlays(ResourcePath collection, int sinceBatchId);
}
