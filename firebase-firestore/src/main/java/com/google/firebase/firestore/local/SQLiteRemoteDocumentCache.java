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

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentCollections;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.BackgroundQueue;
import com.google.firebase.firestore.util.Executors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

final class SQLiteRemoteDocumentCache implements RemoteDocumentCache {

  private final SQLitePersistence db;
  private final LocalSerializer serializer;
  private IndexManager indexManager;

  SQLiteRemoteDocumentCache(SQLitePersistence persistence, LocalSerializer serializer) {
    this.db = persistence;
    this.serializer = serializer;
  }

  @Override
  public void setIndexManager(IndexManager indexManager) {
    this.indexManager = indexManager;
  }

  @Override
  public void add(MutableDocument document, SnapshotVersion readTime) {
    hardAssert(
        !readTime.equals(SnapshotVersion.NONE),
        "Cannot add document to the RemoteDocumentCache with a read time of zero");

    DocumentKey documentKey = document.getKey();
    Timestamp timestamp = readTime.getTimestamp();
    MessageLite message = serializer.encodeMaybeDocument(document);

    db.execute(
        "INSERT OR REPLACE INTO remote_documents "
            + "(path, path_length, read_time_seconds, read_time_nanos, contents) "
            + "VALUES (?, ?, ?, ?, ?)",
        EncodedPath.encode(documentKey.getPath()),
        documentKey.getPath().length(),
        timestamp.getSeconds(),
        timestamp.getNanoseconds(),
        message.toByteArray());

    indexManager.addToCollectionParentIndex(document.getKey().getCollectionPath());
  }

  @Override
  public void removeAll(Collection<DocumentKey> keys) {
    if (keys.isEmpty()) return;

    List<Object> encodedPaths = new ArrayList<>();
    List<Document> deletedDocs = new ArrayList<>();

    for (DocumentKey key : keys) {
      encodedPaths.add(EncodedPath.encode(key.getPath()));
      deletedDocs.add(MutableDocument.newNoDocument(key, SnapshotVersion.NONE));
    }

    SQLitePersistence.LongQuery longQuery =
        new SQLitePersistence.LongQuery(
            db, "DELETE FROM remote_documents WHERE path IN (", encodedPaths, ")");
    while (longQuery.hasMoreSubqueries()) {
      longQuery.executeNextSubquery();
    }

    indexManager.updateIndexEntries(deletedDocs);
  }

  @Override
  public MutableDocument get(DocumentKey documentKey) {
    String path = EncodedPath.encode(documentKey.getPath());

    MutableDocument document =
        db.query(
                "SELECT contents, read_time_seconds, read_time_nanos "
                    + "FROM remote_documents WHERE path = ?")
            .binding(path)
            .firstValue(row -> decodeMaybeDocument(row.getBlob(0), row.getInt(1), row.getInt(2)));
    return document != null ? document : MutableDocument.newInvalidDocument(documentKey);
  }

  @Override
  public Map<DocumentKey, MutableDocument> getAll(Iterable<DocumentKey> documentKeys) {
    Map<DocumentKey, MutableDocument> results = new HashMap<>();
    List<Object> bindVars = new ArrayList<>();
    for (DocumentKey key : documentKeys) {
      bindVars.add(EncodedPath.encode(key.getPath()));

      // Make sure each key has a corresponding entry, which is null in case the document is not
      // found.
      results.put(key, MutableDocument.newInvalidDocument(key));
    }

    SQLitePersistence.LongQuery longQuery =
        new SQLitePersistence.LongQuery(
            db,
            "SELECT contents, read_time_seconds, read_time_nanos FROM remote_documents "
                + "WHERE path IN (",
            bindVars,
            ") ORDER BY path");

    while (longQuery.hasMoreSubqueries()) {
      longQuery
          .performNextSubquery()
          .forEach(
              row -> {
                MutableDocument decoded =
                    decodeMaybeDocument(row.getBlob(0), row.getInt(1), row.getInt(2));
                results.put(decoded.getKey(), decoded);
              });
    }

    return results;
  }

  @Override
  public ImmutableSortedMap<DocumentKey, MutableDocument> getAllDocumentsMatchingQuery(
      final Query query, FieldIndex.IndexOffset offset) {
    hardAssert(
        !query.isCollectionGroupQuery(),
        "CollectionGroup queries should be handled in LocalDocumentsView");

    StringBuilder sql =
        new StringBuilder(
            "SELECT contents, read_time_seconds, read_time_nanos "
                + "FROM remote_documents WHERE path >= ? AND path < ? AND path_length = ?");

    boolean hasOffset = !FieldIndex.IndexOffset.NONE.equals(offset);
    Object[] bindVars = new Object[3 + (hasOffset ? 6 : 0)];

    String prefix = EncodedPath.encode(query.getPath());

    int i = 0;
    bindVars[i++] = prefix;
    bindVars[i++] = EncodedPath.prefixSuccessor(prefix);
    bindVars[i++] = query.getPath().length() + 1;

    if (hasOffset) {
      Timestamp readTime = offset.getReadTime().getTimestamp();
      DocumentKey documentKey = offset.getDocumentKey();

      sql.append(
          " AND (read_time_seconds > ? OR ("
              + "read_time_seconds = ? AND read_time_nanos > ?) OR ("
              + "read_time_seconds = ? AND read_time_nanos = ? and path > ?))");
      bindVars[i++] = readTime.getSeconds();
      bindVars[i++] = readTime.getSeconds();
      bindVars[i++] = readTime.getNanoseconds();
      bindVars[i++] = readTime.getSeconds();
      bindVars[i++] = readTime.getNanoseconds();
      bindVars[i] = EncodedPath.encode(documentKey.getPath());
    }

    ImmutableSortedMap<DocumentKey, MutableDocument>[] results =
        (ImmutableSortedMap<DocumentKey, MutableDocument>[])
            new ImmutableSortedMap[] {DocumentCollections.emptyMutableDocumentMap()};
    BackgroundQueue backgroundQueue = new BackgroundQueue();

    db.query(sql.toString())
        .binding(bindVars)
        .forEach(
            row -> {
              // Store row values in array entries to provide the correct context inside the
              // executor.
              final byte[] rawDocument = row.getBlob(0);
              final int[] readTimeSeconds = {row.getInt(1)};
              final int[] readTimeNanos = {row.getInt(2)};

              // Since scheduling background tasks incurs overhead, we only dispatch to a
              // background thread if there are still some documents remaining.
              Executor executor = row.isLast() ? Executors.DIRECT_EXECUTOR : backgroundQueue;
              executor.execute(
                  () -> {
                    MutableDocument document =
                        decodeMaybeDocument(rawDocument, readTimeSeconds[0], readTimeNanos[0]);
                    if (document.isFoundDocument() && query.matches(document)) {
                      synchronized (SQLiteRemoteDocumentCache.this) {
                        results[0] = results[0].insert(document.getKey(), document);
                      }
                    }
                  });
            });

    try {
      backgroundQueue.drain();
    } catch (InterruptedException e) {
      fail("Interrupted while deserializing documents", e);
    }

    return results[0];
  }

  @Override
  public SnapshotVersion getLatestReadTime() {
    SnapshotVersion latestReadTime =
        db.query(
                "SELECT read_time_seconds, read_time_nanos "
                    + "FROM remote_documents ORDER BY read_time_seconds DESC, read_time_nanos DESC "
                    + "LIMIT 1")
            .firstValue(row -> new SnapshotVersion(new Timestamp(row.getLong(0), row.getInt(1))));
    return latestReadTime != null ? latestReadTime : SnapshotVersion.NONE;
  }

  private MutableDocument decodeMaybeDocument(
      byte[] bytes, int readTimeSeconds, int readTimeNanos) {
    try {
      return serializer
          .decodeMaybeDocument(com.google.firebase.firestore.proto.MaybeDocument.parseFrom(bytes))
          .withReadTime(new SnapshotVersion(new Timestamp(readTimeSeconds, readTimeNanos)));
    } catch (InvalidProtocolBufferException e) {
      throw fail("MaybeDocument failed to parse: %s", e);
    }
  }
}
