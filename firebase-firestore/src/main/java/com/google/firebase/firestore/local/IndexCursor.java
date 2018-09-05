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

import com.google.firebase.firestore.model.DocumentKey;
import java.io.Closeable;

/**
 * A cursor used to iterate through entries in an index. Each entry is returned merely as a
 * DocumentKey (and a separate lookup must be done to read the document contents), since the actual
 * index entries may be lossy and should not be relied upon.
 *
 * <p>TODO: We could probably avoid one final document lookup for most queries by exposing the
 * (lossy) index entry, or at least the typeOrder for it (since the type itself will never be
 * lossy).
 */
public class IndexCursor implements Closeable {
  /**
   * Advances the cursor (to the first result if this is the first call), returning false if there
   * are no more items.
   */
  public boolean next() {
    throw new RuntimeException("Not yet implemented.");
  }

  /** Returns the DocumentKey for the current index entry (throws if there are no more entries). */
  public DocumentKey getDocumentKey() {
    throw new RuntimeException("Not yet implemented.");
  }

  @Override
  public void close() {
    throw new RuntimeException("Not yet implemented.");
  }
}
