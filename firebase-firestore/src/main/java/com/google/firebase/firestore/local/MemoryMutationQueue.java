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

import static com.google.firebase.firestore.util.Assert.hardAssert;
import static com.google.firebase.firestore.util.Preconditions.checkNotNull;
import static java.util.Collections.emptyList;

import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.firebase.firestore.remote.WriteStream;
import com.google.firebase.firestore.util.Util;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class MemoryMutationQueue implements MutationQueue {

  /**
   * A FIFO queue of all mutations to apply to the backend. Mutations are added to the end of the
   * queue as they're written, and removed from the front of the queue as the mutations become
   * visible or are rejected.
   *
   * <p>When successfully applied, mutations must be acknowledged by the write stream and made
   * visible on the watch stream. It's possible for the watch stream to fall behind in which case
   * the batches at the head of the queue will be acknowledged but held until the watch stream sees
   * the changes.
   *
   * <p>If a batch is rejected while there are held write acknowledgements at the head of the queue
   * the rejected batch is converted to a tombstone: its mutations are removed but the batch remains
   * in the queue. This maintains a simple consecutive ordering of batches in the queue.
   *
   * <p>Once the held write acknowledgements become visible they are removed from the head of the
   * queue along with any tombstones that follow.
   */
  private final TreeMap<Integer, MutationBatch> queue = new TreeMap<>();

  /** An ordered mapping between documents and the mutation batch IDs. */
  private ImmutableSortedSet<DocumentReference> batchesByDocumentKey;

  /** The next value to use when assigning sequential IDs to each mutation batch. */
  private int nextBatchId;

  /**
   * The last received stream token from the server, used to acknowledge which responses the client
   * has processed. Stream tokens are opaque checkpoint markers whose only real value is their
   * inclusion in the next request.
   */
  private ByteString lastStreamToken;

  private final MemoryPersistence persistence;
  private final MemoryIndexManager indexManager;

  MemoryMutationQueue(MemoryPersistence persistence, User user) {
    this.persistence = persistence;

    batchesByDocumentKey = new ImmutableSortedSet<>(emptyList(), DocumentReference.BY_KEY);
    nextBatchId = 1;
    lastStreamToken = WriteStream.EMPTY_STREAM_TOKEN;
    indexManager = persistence.getIndexManager(user);
  }

  // MutationQueue implementation

  @Override
  public void start() {
    // Note: The queue may be shutdown / started multiple times, since we maintain the queue for the
    // duration of the app session in case a user logs out / back in. To behave like the
    // SQLite-backed MutationQueue (and accommodate tests that expect as much), we reset nextBatchId
    // if the queue is empty.
    if (isEmpty()) {
      nextBatchId = 1;
    }
  }

  @Override
  public boolean isEmpty() {
    // If the queue has any entries at all, the first entry must not be a tombstone (otherwise it
    // would have been removed already).
    return queue.isEmpty();
  }

  @Override
  public void acknowledgeBatch(MutationBatch batch, ByteString streamToken) {
    int batchId = batch.getBatchId();
    hardAssert(queue.containsKey(batchId), "Can not acknowledge unknown batch ID: %d", batchId);

    int firstKey = queue.firstKey();
    hardAssert(
        batchId == firstKey,
        "Can only acknowledge the first batch in the mutation queue: got batchId %d, but expected %d",
        batchId,
        firstKey);

    lastStreamToken = checkNotNull(streamToken);
  }

  @Override
  public ByteString getLastStreamToken() {
    return lastStreamToken;
  }

  @Override
  public void setLastStreamToken(ByteString streamToken) {
    this.lastStreamToken = checkNotNull(streamToken);
  }

  @Override
  public MutationBatch addMutationBatch(
      Timestamp localWriteTime, List<Mutation> baseMutations, List<Mutation> mutations) {
    hardAssert(!mutations.isEmpty(), "Mutation batches should not be empty");

    int batchId = nextBatchId;
    nextBatchId += 1;

    if (!queue.isEmpty()) {
      int priorKey = queue.lastKey();
      hardAssert(
          priorKey < batchId,
          "Mutation batchIds must be monotonically increasing order:" + " priorKey=%d, batchId=%d",
          priorKey,
          batchId);
    }

    MutationBatch batch = new MutationBatch(batchId, localWriteTime, baseMutations, mutations);
    queue.put(batchId, batch);

    // Track references by document key and index collection parents.
    for (Mutation mutation : mutations) {
      batchesByDocumentKey =
          batchesByDocumentKey.insert(new DocumentReference(mutation.getKey(), batchId));

      indexManager.addToCollectionParentIndex(mutation.getKey().getCollectionPath());
    }

    return batch;
  }

  @Nullable
  @Override
  public MutationBatch lookupMutationBatch(int batchId) {
    return queue.get(batchId);
  }

  @Nullable
  @Override
  public MutationBatch getNextMutationBatchAfterBatchId(int batchId) {
    Map.Entry<Integer, MutationBatch> nextEntry = queue.higherEntry(batchId);
    return nextEntry == null ? null : nextEntry.getValue();
  }

  @Override
  public int getHighestUnacknowledgedBatchId() {
    return queue.isEmpty() ? MutationBatch.UNKNOWN : nextBatchId - 1;
  }

  @Override
  public Collection<MutationBatch> getAllMutationBatches() {
    return Collections.unmodifiableCollection(queue.values());
  }

  @Override
  public List<MutationBatch> getAllMutationBatchesAffectingDocumentKey(DocumentKey documentKey) {
    DocumentReference start = new DocumentReference(documentKey, 0);

    List<MutationBatch> result = new ArrayList<>();
    Iterator<DocumentReference> iterator = batchesByDocumentKey.iteratorFrom(start);
    while (iterator.hasNext()) {
      DocumentReference reference = iterator.next();
      if (!documentKey.equals(reference.getKey())) {
        break;
      }

      MutationBatch batch = lookupMutationBatch(reference.getId());
      hardAssert(batch != null, "Batches in the index must exist in the main table");
      result.add(batch);
    }

    return result;
  }

  @Override
  public List<MutationBatch> getAllMutationBatchesAffectingDocumentKeys(
      Iterable<DocumentKey> documentKeys) {
    ImmutableSortedSet<Integer> uniqueBatchIDs =
        new ImmutableSortedSet<Integer>(emptyList(), Util.comparator());

    for (DocumentKey key : documentKeys) {
      DocumentReference start = new DocumentReference(key, 0);
      Iterator<DocumentReference> batchesIter = batchesByDocumentKey.iteratorFrom(start);
      while (batchesIter.hasNext()) {
        DocumentReference reference = batchesIter.next();
        if (!key.equals(reference.getKey())) {
          break;
        }
        uniqueBatchIDs = uniqueBatchIDs.insert(reference.getId());
      }
    }

    return lookupMutationBatches(uniqueBatchIDs);
  }

  @Override
  public List<MutationBatch> getAllMutationBatchesAffectingQuery(Query query) {
    hardAssert(
        !query.isCollectionGroupQuery(),
        "CollectionGroup queries should be handled in LocalDocumentsView");

    // Use the query path as a prefix for testing if a document matches the query.
    ResourcePath prefix = query.getPath();
    int immediateChildrenPathLength = prefix.length() + 1;

    // Construct a document reference for actually scanning the index. Unlike the prefix, the
    // document key in this reference must have an even number of segments. The empty segment can be
    // used as a suffix of the query path because it precedes all other segments in an ordered
    // traversal.
    ResourcePath startPath = prefix;
    if (!DocumentKey.isDocumentKey(startPath)) {
      startPath = startPath.append("");
    }
    DocumentReference start = new DocumentReference(DocumentKey.fromPath(startPath), 0);

    // Find unique batchIDs referenced by all documents potentially matching the query.
    ImmutableSortedSet<Integer> uniqueBatchIDs =
        new ImmutableSortedSet<Integer>(emptyList(), Util.comparator());

    Iterator<DocumentReference> iterator = batchesByDocumentKey.iteratorFrom(start);
    while (iterator.hasNext()) {
      DocumentReference reference = iterator.next();
      ResourcePath rowKeyPath = reference.getKey().getPath();
      if (!prefix.isPrefixOf(rowKeyPath)) {
        break;
      }

      // Rows with document keys more than one segment longer than the query path can't be matches.
      // For example, a query on 'rooms' can't match the document /rooms/abc/messages/xyx.
      // TODO: we'll need a different scanner when we implement ancestor queries.
      if (rowKeyPath.length() == immediateChildrenPathLength) {
        uniqueBatchIDs = uniqueBatchIDs.insert(reference.getId());
      }
    }

    return lookupMutationBatches(uniqueBatchIDs);
  }

  private List<MutationBatch> lookupMutationBatches(ImmutableSortedSet<Integer> batchIds) {
    // Construct an array of matching batches, sorted by batchID to ensure that multiple mutations
    // affecting the same document key are applied in order.
    List<MutationBatch> result = new ArrayList<>();
    for (Integer batchId : batchIds) {
      MutationBatch batch = lookupMutationBatch(batchId);
      if (batch != null) {
        result.add(batch);
      }
    }

    return result;
  }

  @Override
  public void removeMutationBatch(MutationBatch batch) {
    int batchId = batch.getBatchId();
    hardAssert(queue.containsKey(batchId), "Can not remove unknown batch ID: %d", batchId);

    int firstKey = queue.firstKey();
    hardAssert(
        batchId == firstKey,
        "Can only remove the first batch in the mutation queue:"
            + " got batchId %d, but expected %d",
        batchId,
        firstKey);

    queue.remove(batchId);

    // Remove entries from the index too.
    ImmutableSortedSet<DocumentReference> references = batchesByDocumentKey;
    for (Mutation mutation : batch.getMutations()) {
      DocumentKey key = mutation.getKey();
      persistence.getReferenceDelegate().removeMutationReference(key);

      DocumentReference reference = new DocumentReference(key, batch.getBatchId());
      references = references.remove(reference);
    }
    batchesByDocumentKey = references;
  }

  @Override
  public void performConsistencyCheck() {
    if (queue.isEmpty()) {
      hardAssert(
          batchesByDocumentKey.isEmpty(),
          "Document leak -- detected dangling mutation references when queue is empty.");
    }
  }

  boolean containsKey(DocumentKey key) {
    // Create a reference with a zero ID as the start position to find any document reference with
    // this key.
    DocumentReference reference = new DocumentReference(key, 0);

    Iterator<DocumentReference> iterator = batchesByDocumentKey.iteratorFrom(reference);
    if (!iterator.hasNext()) {
      return false;
    }

    DocumentKey firstKey = iterator.next().getKey();
    return firstKey.equals(key);
  }

  // Helpers

  long getByteSize(LocalSerializer serializer) {
    long count = 0;
    for (MutationBatch batch : queue.values()) {
      count += serializer.encodeMutationBatch(batch).getSerializedSize();
    }
    return count;
  }
}
