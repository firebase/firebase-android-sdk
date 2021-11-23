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
import com.google.firebase.firestore.model.DocumentCollections;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.BackgroundQueue;
import com.google.firebase.firestore.util.Executors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import java.util.ArrayList;
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

    String path = pathForKey(document.getKey());
    Timestamp timestamp = readTime.getTimestamp();
    MessageLite message = serializer.encodeMaybeDocument(document);

    db.execute(
        "INSERT OR REPLACE INTO remote_documents "
            + "(path, read_time_seconds, read_time_nanos, contents) "
            + "VALUES (?, ?, ?, ?)",
        path,
        timestamp.getSeconds(),
        timestamp.getNanoseconds(),
        message.toByteArray());

    indexManager.addToCollectionParentIndex(document.getKey().getPath().popLast());
  }

  @Override
  public void remove(DocumentKey documentKey) {
    String path = pathForKey(documentKey);

    db.execute("DELETE FROM remote_documents WHERE path = ?", path);
  }

  @Override
  public MutableDocument get(DocumentKey documentKey) {
    String path = pathForKey(documentKey);

    MutableDocument document =
        db.query(
                "SELECT contents, read_time_seconds, read_time_nanos "
                    + "FROM remote_documents "
                    + "WHERE path = ?")
            .binding(path)
            .firstValue(row -> decodeMaybeDocument(row.getBlob(0), row.getInt(1), row.getInt(2)));
    return document != null ? document : MutableDocument.newInvalidDocument(documentKey);
  }

  @Override
  public Map<DocumentKey, MutableDocument> getAll(Iterable<DocumentKey> documentKeys) {
    List<Object> args = new ArrayList<>();
    for (DocumentKey key : documentKeys) {
      args.add(EncodedPath.encode(key.getPath()));
    }

    Map<DocumentKey, MutableDocument> results = new HashMap<>();
    for (DocumentKey key : documentKeys) {
      // Make sure each key has a corresponding entry, which is null in case the document is not
      // found.
      results.put(key, MutableDocument.newInvalidDocument(key));
    }

    SQLitePersistence.LongQuery longQuery =
        new SQLitePersistence.LongQuery(
            db,
            "SELECT contents, read_time_seconds, read_time_nanos FROM remote_documents "
                + "WHERE path IN (",
            args,
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
      final Query query, SnapshotVersion sinceReadTime) {
    hardAssert(
        !query.isCollectionGroupQuery(),
        "CollectionGroup queries should be handled in LocalDocumentsView");

    // Use the query path as a prefix for testing if a document matches the query.
    ResourcePath prefix = query.getPath();
    int immediateChildrenPathLength = prefix.length() + 1;

    String prefixPath = EncodedPath.encode(prefix);
    String prefixSuccessorPath = EncodedPath.prefixSuccessor(prefixPath);
    Timestamp readTime = sinceReadTime.getTimestamp();

    BackgroundQueue backgroundQueue = new BackgroundQueue();

    ImmutableSortedMap<DocumentKey, MutableDocument>[] matchingDocuments =
        (ImmutableSortedMap<DocumentKey, MutableDocument>[])
            new ImmutableSortedMap[] {DocumentCollections.emptyMutableDocumentMap()};

    SQLitePersistence.Query sqlQuery;
    if (sinceReadTime.equals(SnapshotVersion.NONE)) {
      sqlQuery =
          db.query(
                  "SELECT path, contents, read_time_seconds, read_time_nanos "
                      + "FROM remote_documents WHERE path >= ? AND path < ?")
              .binding(prefixPath, prefixSuccessorPath);
    } else {
      // Execute an index-free query and filter by read time. This is safe since all document
      // changes to queries that have a lastLimboFreeSnapshotVersion (`sinceReadTime`) have a read
      // time set.
      sqlQuery =
          db.query(
                  "SELECT path, contents, read_time_seconds, read_time_nanos "
                      + "FROM remote_documents WHERE path >= ? AND path < ?"
                      + "AND (read_time_seconds > ? OR (read_time_seconds = ? AND read_time_nanos > ?))")
              .binding(
                  prefixPath,
                  prefixSuccessorPath,
                  readTime.getSeconds(),
                  readTime.getSeconds(),
                  readTime.getNanoseconds());
    }
    sqlQuery.forEach(
        row -> {
          // TODO: Actually implement a single-collection query
          //
          // The query is actually returning any path that starts with the query path prefix
          // which may include documents in subcollections. For example, a query on 'rooms'
          // will return rooms/abc/messages/xyx but we shouldn't match it. Fix this by
          // discarding rows with document keys more than one segment longer than the query
          // path.
          ResourcePath path = EncodedPath.decodeResourcePath(row.getString(0));
          if (path.length() != immediateChildrenPathLength) {
            return;
          }

          // Store row values in array entries to provide the correct context inside the executor.
          final byte[] rawDocument = row.getBlob(1);
          final int[] readTimeSeconds = {row.getInt(2)};
          final int[] readTimeNanos = {row.getInt(3)};

          // Since scheduling background tasks incurs overhead, we only dispatch to a
          // background thread if there are still some documents remaining.
          Executor executor = row.isLast() ? Executors.DIRECT_EXECUTOR : backgroundQueue;
          executor.execute(
              () -> {
                MutableDocument document =
                    decodeMaybeDocument(rawDocument, readTimeSeconds[0], readTimeNanos[0]);
                if (document.isFoundDocument() && query.matches(document)) {
                  synchronized (SQLiteRemoteDocumentCache.this) {
                    matchingDocuments[0] = matchingDocuments[0].insert(document.getKey(), document);
                  }
                }
              });
        });

    try {
      backgroundQueue.drain();
    } catch (InterruptedException e) {
      fail("Interrupted while deserializing documents", e);
    }

    return matchingDocuments[0];
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

  private String pathForKey(DocumentKey key) {
    return EncodedPath.encode(key.getPath());
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
