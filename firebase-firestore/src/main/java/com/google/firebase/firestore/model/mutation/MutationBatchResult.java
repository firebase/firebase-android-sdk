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

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.model.DocumentCollections;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.Assert;
import com.google.protobuf.ByteString;
import java.util.List;

/** The result of applying a mutation batch to the backend. */
public final class MutationBatchResult {
  private final MutationBatch batch;
  private final SnapshotVersion commitVersion;
  private final List<MutationResult> mutationResults;
  private final ByteString streamToken;
  private final ImmutableSortedMap<DocumentKey, SnapshotVersion> docVersions;

  private MutationBatchResult(
      MutationBatch batch,
      SnapshotVersion commitVersion,
      List<MutationResult> mutationResults,
      ByteString streamToken,
      ImmutableSortedMap<DocumentKey, SnapshotVersion> docVersions) {
    this.batch = batch;
    this.commitVersion = commitVersion;
    this.mutationResults = mutationResults;
    this.streamToken = streamToken;
    this.docVersions = docVersions;
  }

  /**
   * Creates a new MutationBatchResult for the given batch and results. There must be one result for
   * each mutation in the batch. This static factory caches a document=>version mapping (as
   * docVersions).
   */
  public static MutationBatchResult create(
      MutationBatch batch,
      SnapshotVersion commitVersion,
      List<MutationResult> mutationResults,
      ByteString streamToken) {
    Assert.hardAssert(
        batch.getMutations().size() == mutationResults.size(),
        "Mutations sent %d must equal results received %d",
        batch.getMutations().size(),
        mutationResults.size());
    ImmutableSortedMap<DocumentKey, SnapshotVersion> docVersions =
        DocumentCollections.emptyVersionMap();
    List<Mutation> mutations = batch.getMutations();
    for (int i = 0; i < mutations.size(); i++) {
      SnapshotVersion version = mutationResults.get(i).getVersion();
      docVersions = docVersions.insert(mutations.get(i).getKey(), version);
    }

    return new MutationBatchResult(batch, commitVersion, mutationResults, streamToken, docVersions);
  }

  public MutationBatch getBatch() {
    return batch;
  }

  public SnapshotVersion getCommitVersion() {
    return commitVersion;
  }

  public List<MutationResult> getMutationResults() {
    return mutationResults;
  }

  public ByteString getStreamToken() {
    return streamToken;
  }

  public ImmutableSortedMap<DocumentKey, SnapshotVersion> getDocVersions() {
    return docVersions;
  }
}
