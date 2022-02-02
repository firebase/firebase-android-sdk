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

import static com.google.firebase.firestore.model.DocumentCollections.emptyDocumentMap;
import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;
import static com.google.firebase.firestore.util.Util.firstNEntries;
import static com.google.firebase.firestore.util.Util.repeatSequence;

import androidx.annotation.VisibleForTesting;
import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.BackgroundQueue;
import com.google.firebase.firestore.util.Executors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

final class SQLiteRemoteDocumentCache implements RemoteDocumentCache {
  /** The number of bind args per collection group in {@link #getAll(String, IndexOffset, int)} */
  @VisibleForTesting static final int BINDS_PER_STATEMENT = 9;

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
    ImmutableSortedMap<DocumentKey, Document> deletedDocs = emptyDocumentMap();

    for (DocumentKey key : keys) {
      encodedPaths.add(EncodedPath.encode(key.getPath()));
      deletedDocs =
          deletedDocs.insert(key, MutableDocument.newNoDocument(key, SnapshotVersion.NONE));
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
  public Map<DocumentKey, MutableDocument> getAll(
      String collectionGroup, IndexOffset offset, int limit) {
    List<ResourcePath> collectionParents = indexManager.getCollectionParents(collectionGroup);
    List<ResourcePath> collections = new ArrayList<>(collectionParents.size());
    for (ResourcePath collectionParent : collectionParents) {
      collections.add(collectionParent.append(collectionGroup));
    }

    if (collections.isEmpty()) {
      return Collections.emptyMap();
    } else if (BINDS_PER_STATEMENT * collections.size() < SQLitePersistence.MAX_ARGS) {
      return getAll(collections, offset, limit);
    } else {
      // We need to fan out our collection scan since SQLite only supports 999 binds per statement.
      Map<DocumentKey, MutableDocument> results = new HashMap<>();
      int pageSize = SQLitePersistence.MAX_ARGS / BINDS_PER_STATEMENT;
      for (int i = 0; i < collections.size(); i += pageSize) {
        results.putAll(
            getAll(
                collections.subList(i, Math.min(collections.size(), i + pageSize)), offset, limit));
      }
      return firstNEntries(results, limit, IndexOffset.DOCUMENT_COMPARATOR);
    }
  }

  /**
   * Returns the next {@code count} documents from the provided collections, ordered by read time.
   */
  private Map<DocumentKey, MutableDocument> getAll(
      List<ResourcePath> collections, IndexOffset offset, int count) {
    Timestamp readTime = offset.getReadTime().getTimestamp();
    DocumentKey documentKey = offset.getDocumentKey();

    StringBuilder sql =
        repeatSequence(
            "SELECT contents, read_time_seconds, read_time_nanos, path "
                + "FROM remote_documents "
                + "WHERE path >= ? AND path < ? AND path_length = ? "
                + "AND (read_time_seconds > ? OR ( "
                + "read_time_seconds = ? AND read_time_nanos > ?) OR ( "
                + "read_time_seconds = ? AND read_time_nanos = ? and path > ?)) ",
            collections.size(),
            " UNION ");
    sql.append("ORDER BY read_time_seconds, read_time_nanos, path LIMIT ?");

    Object[] bindVars = new Object[BINDS_PER_STATEMENT * collections.size() + 1];
    int i = 0;
    for (ResourcePath collection : collections) {
      String prefixPath = EncodedPath.encode(collection);
      bindVars[i++] = prefixPath;
      bindVars[i++] = EncodedPath.prefixSuccessor(prefixPath);
      bindVars[i++] = collection.length() + 1;
      bindVars[i++] = readTime.getSeconds();
      bindVars[i++] = readTime.getSeconds();
      bindVars[i++] = readTime.getNanoseconds();
      bindVars[i++] = readTime.getSeconds();
      bindVars[i++] = readTime.getNanoseconds();
      bindVars[i++] = EncodedPath.encode(documentKey.getPath());
    }
    bindVars[i] = count;

    BackgroundQueue backgroundQueue = new BackgroundQueue();
    Map<DocumentKey, MutableDocument>[] results =
        (HashMap<DocumentKey, MutableDocument>[]) (new HashMap[] {new HashMap()});

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
                    synchronized (SQLiteRemoteDocumentCache.this) {
                      results[0].put(document.getKey(), document);
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
  public Map<DocumentKey, MutableDocument> getAll(ResourcePath collection, IndexOffset offset) {
    return getAll(Collections.singletonList(collection), offset, Integer.MAX_VALUE);
  }

  private MutableDocument decodeMaybeDocument(
      byte[] bytes, int readTimeSeconds, int readTimeNanos) {
    try {
      return serializer
          .decodeMaybeDocument(com.google.firebase.firestore.proto.MaybeDocument.parseFrom(bytes))
          .setReadTime(new SnapshotVersion(new Timestamp(readTimeSeconds, readTimeNanos)));
    } catch (InvalidProtocolBufferException e) {
      throw fail("MaybeDocument failed to parse: %s", e);
    }
  }
}
