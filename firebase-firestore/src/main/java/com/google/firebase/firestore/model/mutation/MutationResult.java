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

package com.google.firebase.firestore.model.mutation;

import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firestore.v1.Value;
import java.util.List;

/**
 * The result of applying a mutation to the server. This is a model of the WriteResult proto
 * message.
 *
 * <p>Note that MutationResult does not name which document was mutated. The association is implied
 * positionally: for each entry in the array of Mutations, there's a corresponding entry in the
 * array of MutationResults.
 */
public final class MutationResult {
  private final SnapshotVersion version;
  @Nullable private final List<Value> transformResults;

  public MutationResult(SnapshotVersion version, @Nullable List<Value> transformResults) {
    this.version = checkNotNull(version);
    this.transformResults = transformResults;
  }

  /**
   * The version at which the mutation was committed.
   *
   * <ul>
   *   <li>For most operations, this is the updateTime in the WriteResult.
   *   <li>For deletes, it is the commitTime of the WriteResponse (because deletes are not stored
   *       and have no updateTime).
   * </ul>
   *
   * <p>Note that these versions can be different: No-op writes will not change the updateTime even
   * though the commitTime advances.
   */
  public SnapshotVersion getVersion() {
    return version;
  }

  /**
   * The resulting fields returned from the backend after a mutation containing field transforms has
   * been committed. Contains one Value for each FieldTransform that was in the mutation.
   *
   * <p>Will be null if the mutation did not contain any field transforms.
   */
  @Nullable
  public List<Value> getTransformResults() {
    return transformResults;
  }
}
