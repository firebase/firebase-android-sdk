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

import static com.google.firebase.firestore.util.Assert.fail;

import android.database.Cursor;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.Overlay;
import com.google.firebase.firestore.util.BackgroundQueue;
import com.google.firebase.firestore.util.Executors;
import com.google.firestore.v1.Write;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.Executor;

public class SQLiteDocumentOverlayCache implements DocumentOverlayCache {
  private final SQLitePersistence db;
  private final LocalSerializer serializer;
  private final String uid;

  public SQLiteDocumentOverlayCache(SQLitePersistence db, LocalSerializer serializer, User user) {
    this.db = db;
    this.serializer = serializer;
    this.uid = user.isAuthenticated() ? user.getUid() : "";
  }

  @Nullable
  @Override
  public Overlay getOverlay(DocumentKey key) {
    String collectionPath = EncodedPath.encode(key.getPath().popLast());
    String documentId = key.getPath().getLastSegment();
    return db.query(
            "SELECT overlay_mutation, largest_batch_id FROM document_overlays "
                + "WHERE uid = ? AND collection_path = ? AND document_id = ?")
        .binding(uid, collectionPath, documentId)
        .firstValue(row -> this.decodeOverlay(row.getBlob(0), row.getInt(1)));
  }

  @Override
  public Map<DocumentKey, Overlay> getOverlays(SortedSet<DocumentKey> keys) {
    Map<DocumentKey, Overlay> result = new HashMap<>();

    BackgroundQueue backgroundQueue = new BackgroundQueue();
    ResourcePath currentCollectionPath = ResourcePath.EMPTY;
    List<Object> currentDocumentIds = new ArrayList<>();
    for (DocumentKey key : keys) {
      if (!currentCollectionPath.equals(key.getCollectionPath())) {
        processSingleCollection(result, backgroundQueue, currentCollectionPath, currentDocumentIds);
        currentDocumentIds = new ArrayList<>();
      }
      currentCollectionPath = key.getCollectionPath();
      currentDocumentIds.add(key.getDocumentId());
    }

    processSingleCollection(result, backgroundQueue, currentCollectionPath, currentDocumentIds);
    backgroundQueue.drain();
    return result;
  }

  /** Reads the overlays for the documents in a single collection. */
  private void processSingleCollection(
      Map<DocumentKey, Overlay> result,
      BackgroundQueue backgroundQueue,
      ResourcePath collectionPath,
      List<Object> documentIds) {
    SQLitePersistence.LongQuery longQuery =
        new SQLitePersistence.LongQuery(
            db,
            "SELECT overlay_mutation, largest_batch_id FROM document_overlays "
                + "WHERE uid = ? AND collection_path = ? AND document_id IN (",
            Arrays.asList(uid, EncodedPath.encode(collectionPath)),
            documentIds,
            ")");
    while (longQuery.hasMoreSubqueries()) {
      longQuery
          .performNextSubquery()
          .forEach(row -> processOverlaysInBackground(backgroundQueue, result, row));
    }
  }

  private void saveOverlay(int largestBatchId, DocumentKey key, @Nullable Mutation mutation) {
    String group = key.getCollectionGroup();
    String collectionPath = EncodedPath.encode(key.getPath().popLast());
    String documentId = key.getPath().getLastSegment();
    db.execute(
        "INSERT OR REPLACE INTO document_overlays "
            + "(uid, collection_group, collection_path, document_id, largest_batch_id, overlay_mutation) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        uid,
        group,
        collectionPath,
        documentId,
        largestBatchId,
        serializer.encodeMutation(mutation).toByteArray());
  }

  @Override
  public void saveOverlays(int largestBatchId, Map<DocumentKey, Mutation> overlays) {
    for (Map.Entry<DocumentKey, Mutation> entry : overlays.entrySet()) {
      if (entry.getValue() != null) {
        saveOverlay(largestBatchId, entry.getKey(), entry.getValue());
      }
    }
  }

  @Override
  public void removeOverlaysForBatchId(int batchId) {
    db.execute(
        "DELETE FROM document_overlays WHERE uid = ? AND largest_batch_id = ?", uid, batchId);
  }

  @Override
  public Map<DocumentKey, Overlay> getOverlays(ResourcePath collection, int sinceBatchId) {
    Map<DocumentKey, Overlay> result = new HashMap<>();
    BackgroundQueue backgroundQueue = new BackgroundQueue();
    db.query(
            "SELECT overlay_mutation, largest_batch_id FROM document_overlays "
                + "WHERE uid = ? AND collection_path = ? AND largest_batch_id > ?")
        .binding(uid, EncodedPath.encode(collection), sinceBatchId)
        .forEach(row -> processOverlaysInBackground(backgroundQueue, result, row));
    backgroundQueue.drain();
    return result;
  }

  @Override
  public Map<DocumentKey, Overlay> getOverlays(
      String collectionGroup, int sinceBatchId, int count) {
    Map<DocumentKey, Overlay> result = new HashMap<>();
    String[] lastCollectionPath = new String[] {null};
    String[] lastDocumentPath = new String[] {null};
    int[] lastLargestBatchId = new int[] {0};

    BackgroundQueue backgroundQueue = new BackgroundQueue();
    db.query(
            "SELECT overlay_mutation, largest_batch_id, collection_path, document_id "
                + " FROM document_overlays "
                + "WHERE uid = ? AND collection_group = ? AND largest_batch_id > ? "
                + "ORDER BY largest_batch_id, collection_path, document_id LIMIT ?")
        .binding(uid, collectionGroup, sinceBatchId, count)
        .forEach(
            row -> {
              lastLargestBatchId[0] = row.getInt(1);
              lastCollectionPath[0] = row.getString(2);
              lastDocumentPath[0] = row.getString(3);
              processOverlaysInBackground(backgroundQueue, result, row);
            });

    if (lastCollectionPath[0] == null) {
      return result;
    }

    // This function should not return partial batch overlays, even if the number of overlays in the
    // result set exceeds the given `count` argument. Since the `LIMIT` in the above query might
    // result in a partial batch, the following query appends any remaining overlays for the last
    // batch.
    db.query(
            "SELECT overlay_mutation, largest_batch_id FROM document_overlays "
                + "WHERE uid = ? AND collection_group = ? "
                + "AND (collection_path > ? OR (collection_path = ? AND document_id > ?)) "
                + "AND largest_batch_id = ?")
        .binding(
            uid,
            collectionGroup,
            lastCollectionPath[0],
            lastCollectionPath[0],
            lastDocumentPath[0],
            lastLargestBatchId[0])
        .forEach(row -> processOverlaysInBackground(backgroundQueue, result, row));
    backgroundQueue.drain();
    return result;
  }

  private void processOverlaysInBackground(
      BackgroundQueue backgroundQueue, Map<DocumentKey, Overlay> results, Cursor row) {
    byte[] rawMutation = row.getBlob(0);
    int largestBatchId = row.getInt(1);

    // Since scheduling background tasks incurs overhead, we only dispatch to a
    // background thread if there are still some documents remaining.
    Executor executor = row.isLast() ? Executors.DIRECT_EXECUTOR : backgroundQueue;
    executor.execute(
        () -> {
          Overlay document = decodeOverlay(rawMutation, largestBatchId);
          synchronized (results) {
            results.put(document.getKey(), document);
          }
        });
  }

  private Overlay decodeOverlay(byte[] rawMutation, int largestBatchId) {
    try {
      Write write = Write.parseFrom(rawMutation);
      Mutation mutation = serializer.decodeMutation(write);
      return Overlay.create(largestBatchId, mutation);
    } catch (InvalidProtocolBufferException e) {
      throw fail("Overlay failed to parse: %s", e);
    }
  }
}
