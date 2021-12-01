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

package com.google.firebase.firestore.local;

import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;

/**
 * A test-only QueryEngine that forwards all API calls and exposes the number of documents and
 * mutations read.
 */
class CountingQueryEngine implements QueryEngine {
  private final QueryEngine queryEngine;

  private final int[] mutationsReadByQuery = new int[] {0};
  private final int[] mutationsReadByKey = new int[] {0};
  private final int[] documentsReadByQuery = new int[] {0};
  private final int[] documentsReadByKey = new int[] {0};

  CountingQueryEngine(QueryEngine queryEngine) {
    this.queryEngine = queryEngine;
  }

  void resetCounts() {
    mutationsReadByQuery[0] = 0;
    mutationsReadByKey[0] = 0;
    documentsReadByQuery[0] = 0;
    documentsReadByKey[0] = 0;
  }

  @Override
  public void setLocalDocumentsView(LocalDocumentsView localDocuments) {
    LocalDocumentsView view =
        new LocalDocumentsView(
            wrapRemoteDocumentCache(localDocuments.getRemoteDocumentCache()),
            wrapMutationQueue(localDocuments.getMutationQueue()),
            localDocuments.getDocumentOverlayCache(),
            localDocuments.getIndexManager());
    queryEngine.setLocalDocumentsView(view);
  }

  @Override
  public void setIndexManager(IndexManager indexManager) {
    // Not implemented.
  }

  @Override
  public ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
      Query query,
      SnapshotVersion lastLimboFreeSnapshotVersion,
      ImmutableSortedSet<DocumentKey> remoteKeys) {
    return queryEngine.getDocumentsMatchingQuery(query, lastLimboFreeSnapshotVersion, remoteKeys);
  }

  /** Returns the query engine that is used as the backing implementation. */
  QueryEngine getSubject() {
    return queryEngine;
  }

  /**
   * Returns the number of documents returned by the RemoteDocumentCache's
   * `getDocumentsMatchingQuery()` API (since the last call to `resetCounts()`)
   */
  int getDocumentsReadByQuery() {
    return documentsReadByQuery[0];
  }

  /**
   * Returns the number of documents returned by the RemoteDocumentCache's `getEntry()` and
   * `getEntries()` APIs (since the last call to `resetCounts()`)
   */
  int getDocumentsReadByKey() {
    return documentsReadByKey[0];
  }

  /**
   * Returns the number of mutations returned by the MutationQueue's
   * `getAllMutationBatchesAffectingQuery()` API (since the last call to `resetCounts()`)
   */
  int getMutationsReadByQuery() {
    return mutationsReadByQuery[0];
  }

  /**
   * Returns the number of mutations returned by the MutationQueue's
   * `getAllMutationBatchesAffectingDocumentKey()` and
   * `getAllMutationBatchesAffectingDocumentKeys()` APIs (since the last call to `resetCounts()`)
   */
  int getMutationsReadByKey() {
    return mutationsReadByKey[0];
  }

  private RemoteDocumentCache wrapRemoteDocumentCache(RemoteDocumentCache subject) {
    return new RemoteDocumentCache() {
      @Override
      public void setIndexManager(IndexManager indexManager) {
        // Not implemented.
      }

      @Override
      public void add(MutableDocument document, SnapshotVersion readTime) {
        subject.add(document, readTime);
      }

      @Override
      public void remove(DocumentKey documentKey) {
        subject.remove(documentKey);
      }

      @Nullable
      @Override
      public MutableDocument get(DocumentKey documentKey) {
        MutableDocument result = subject.get(documentKey);
        documentsReadByKey[0] += result.isValidDocument() ? 1 : 0;
        return result;
      }

      @Override
      public Map<DocumentKey, MutableDocument> getAll(Iterable<DocumentKey> documentKeys) {
        Map<DocumentKey, MutableDocument> result = subject.getAll(documentKeys);
        for (MutableDocument document : result.values()) {
          documentsReadByKey[0] += document.isValidDocument() ? 1 : 0;
        }
        return result;
      }

      @Override
      public Map<DocumentKey, MutableDocument> getAll(
          String collectionGroup, IndexOffset offset, int count) {
        Map<DocumentKey, MutableDocument> result = subject.getAll(collectionGroup, offset, count);
        documentsReadByQuery[0] += result.size();
        return result;
      }

      @Override
      public ImmutableSortedMap<DocumentKey, MutableDocument> getAllDocumentsMatchingQuery(
          Query query, IndexOffset offset) {
        ImmutableSortedMap<DocumentKey, MutableDocument> result =
            subject.getAllDocumentsMatchingQuery(query, offset);
        documentsReadByQuery[0] += result.size();
        return result;
      }

      @Override
      public SnapshotVersion getLatestReadTime() {
        return subject.getLatestReadTime();
      }
    };
  }

  private MutationQueue wrapMutationQueue(MutationQueue subject) {
    return new MutationQueue() {
      @Override
      public void start() {
        subject.start();
      }

      @Override
      public boolean isEmpty() {
        return subject.isEmpty();
      }

      @Override
      public void acknowledgeBatch(MutationBatch batch, ByteString streamToken) {
        subject.acknowledgeBatch(batch, streamToken);
      }

      @Override
      public ByteString getLastStreamToken() {
        return subject.getLastStreamToken();
      }

      @Override
      public void setLastStreamToken(ByteString streamToken) {
        subject.setLastStreamToken(streamToken);
      }

      @Override
      public MutationBatch addMutationBatch(
          Timestamp localWriteTime, List<Mutation> baseMutations, List<Mutation> mutations) {
        return subject.addMutationBatch(localWriteTime, baseMutations, mutations);
      }

      @Nullable
      @Override
      public MutationBatch lookupMutationBatch(int batchId) {
        return subject.lookupMutationBatch(batchId);
      }

      @Nullable
      @Override
      public MutationBatch getNextMutationBatchAfterBatchId(int batchId) {
        return subject.getNextMutationBatchAfterBatchId(batchId);
      }

      @Override
      public int getHighestUnacknowledgedBatchId() {
        return subject.getHighestUnacknowledgedBatchId();
      }

      @Override
      public List<MutationBatch> getAllMutationBatches() {
        List<MutationBatch> result = subject.getAllMutationBatches();
        mutationsReadByKey[0] += result.size();
        return result;
      }

      @Override
      public List<MutationBatch> getAllMutationBatchesAffectingDocumentKey(
          DocumentKey documentKey) {
        List<MutationBatch> result = subject.getAllMutationBatchesAffectingDocumentKey(documentKey);
        mutationsReadByKey[0] += result.size();
        return result;
      }

      @Override
      public List<MutationBatch> getAllMutationBatchesAffectingDocumentKeys(
          Iterable<DocumentKey> documentKeys) {
        List<MutationBatch> result =
            subject.getAllMutationBatchesAffectingDocumentKeys(documentKeys);
        mutationsReadByKey[0] += result.size();
        return result;
      }

      @Override
      public List<MutationBatch> getAllMutationBatchesAffectingQuery(Query query) {
        List<MutationBatch> result = subject.getAllMutationBatchesAffectingQuery(query);
        mutationsReadByQuery[0] += result.size();
        return result;
      }

      @Override
      public void removeMutationBatch(MutationBatch batch) {
        subject.removeMutationBatch(batch);
      }

      @Override
      public void performConsistencyCheck() {
        subject.performConsistencyCheck();
      }
    };
  }
}
