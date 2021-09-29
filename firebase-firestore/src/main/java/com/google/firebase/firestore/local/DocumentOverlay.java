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
import com.google.firebase.firestore.model.mutation.Mutation;

/**
 * Provides methods to read and write document overlays.
 *
 * <p>An overlay is a saved {@code Mutation}, that gives a local view of a document when applied to
 * the remote version of the document.
 */
public interface DocumentOverlay {
  /**
   * Gets the saved overlay mutation for the given user and the given document key. Returns null if
   * there is no overlay for the user and document combination.
   */
  @Nullable
  Mutation getOverlay(DocumentKey key);

  /** Saves the given mutation as overlay for the user and document key. */
  void saveOverlay(DocumentKey key, Mutation mutation);

  /** Removes the overlay associated for the given user and document key. */
  void removeOverlay(DocumentKey key);
}
