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

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.BackgroundQueue;
import com.google.firebase.firestore.util.Executors;
import com.google.firebase.firestore.util.Function;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class SQLiteRemoteDocumentCache implements RemoteDocumentCache {
  /** The number of bind args per collection group in {@link #getAll(String, IndexOffset, int)} */
  @VisibleForTesting static final int BINDS_PER_STATEMENT = 9;

  private final SQLitePersistence db;
  private final LocalSerializer serializer;
  private IndexManager indexManager;

  private final DocumentTypeBackfiller documentTypeBackfiller = new DocumentTypeBackfiller();

  SQLiteRemoteDocumentCache(SQLitePersistence persistence, LocalSerializer serializer) {
    this.db = persistence;
    this.serializer = serializer;
  }

  @Override
  public void setIndexManager(IndexManager indexManager) {
    this.indexManager = indexManager;
  }

  private enum DocumentType {
    NO_DOCUMENT(1),
    FOUND_DOCUMENT(2),
    UNKNOWN_DOCUMENT(3),
    INVALID_DOCUMENT(4);

    final int dbValue;

    DocumentType(int dbValue) {
      this.dbValue = dbValue;
    }

    static DocumentType forMutableDocument(MutableDocument document) {
      if (document.isNoDocument()) {
        return NO_DOCUMENT;
      } else if (document.isFoundDocument()) {
        return FOUND_DOCUMENT;
      } else if (document.isUnknownDocument()) {
        return UNKNOWN_DOCUMENT;
      } else {
        hardAssert(!document.isValidDocument(), "MutableDocument has an unknown type");
        return INVALID_DOCUMENT;
      }
    }
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
            + "(path, path_length, read_time_seconds, read_time_nanos, document_type, contents) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        EncodedPath.encode(documentKey.getPath()),
        documentKey.getPath().length(),
        timestamp.getSeconds(),
        timestamp.getNanoseconds(),
        DocumentType.forMutableDocument(document).dbValue,
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
    return getAll(Collections.singletonList(documentKey)).get(documentKey);
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
            "SELECT contents, read_time_seconds, read_time_nanos, document_type, path "
                + "FROM remote_documents "
                + "WHERE path IN (",
            bindVars,
            ") ORDER BY path");

    BackgroundQueue backgroundQueue = new BackgroundQueue();
    while (longQuery.hasMoreSubqueries()) {
      longQuery
          .performNextSubquery()
          .forEach(row -> processRowInBackground(backgroundQueue, results, row, /*filter*/ null));
    }
    backgroundQueue.drain();

    // Backfill any rows with null "document_type" discovered by processRowInBackground().
    documentTypeBackfiller.backfill(db);

    // Synchronize on `results` to avoid a data race with the background queue.
    synchronized (results) {
      return results;
    }
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
      return getAll(collections, offset, limit, /*filter*/ null);
    } else {
      // We need to fan out our collection scan since SQLite only supports 999 binds per statement.
      Map<DocumentKey, MutableDocument> results = new HashMap<>();
      int pageSize = SQLitePersistence.MAX_ARGS / BINDS_PER_STATEMENT;
      for (int i = 0; i < collections.size(); i += pageSize) {
        results.putAll(
            getAll(
                collections.subList(i, Math.min(collections.size(), i + pageSize)),
                offset,
                limit,
                /*filter*/ null));
      }
      return firstNEntries(results, limit, IndexOffset.DOCUMENT_COMPARATOR);
    }
  }

  /**
   * Returns the next {@code count} documents from the provided collections, ordered by read time.
   */
  private Map<DocumentKey, MutableDocument> getAll(
      List<ResourcePath> collections,
      IndexOffset offset,
      int count,
      @Nullable DocumentType tryFilterDocumentType,
      @Nullable Function<MutableDocument, Boolean> filter,
      @Nullable QueryContext context) {
    Timestamp readTime = offset.getReadTime().getTimestamp();
    DocumentKey documentKey = offset.getDocumentKey();

    StringBuilder sql =
        repeatSequence(
            "SELECT contents, read_time_seconds, read_time_nanos, document_type, path "
                + "FROM remote_documents "
                + "WHERE path >= ? AND path < ? AND path_length = ? "
                + (tryFilterDocumentType == null
                    ? ""
                    : " AND (document_type IS NULL OR document_type = ?) ")
                + "AND (read_time_seconds > ? OR ( "
                + "read_time_seconds = ? AND read_time_nanos > ?) OR ( "
                + "read_time_seconds = ? AND read_time_nanos = ? and path > ?)) ",
            collections.size(),
            " UNION ");
    sql.append("ORDER BY read_time_seconds, read_time_nanos, path LIMIT ?");

    Object[] bindVars =
        new Object
            [(BINDS_PER_STATEMENT + (tryFilterDocumentType != null ? 1 : 0)) * collections.size()
                + 1];
    int i = 0;
    for (ResourcePath collection : collections) {
      String prefixPath = EncodedPath.encode(collection);
      bindVars[i++] = prefixPath;
      bindVars[i++] = EncodedPath.prefixSuccessor(prefixPath);
      bindVars[i++] = collection.length() + 1;
      if (tryFilterDocumentType != null) {
        bindVars[i++] = tryFilterDocumentType.dbValue;
      }
      bindVars[i++] = readTime.getSeconds();
      bindVars[i++] = readTime.getSeconds();
      bindVars[i++] = readTime.getNanoseconds();
      bindVars[i++] = readTime.getSeconds();
      bindVars[i++] = readTime.getNanoseconds();
      bindVars[i++] = EncodedPath.encode(documentKey.getPath());
    }
    bindVars[i] = count;

    BackgroundQueue backgroundQueue = new BackgroundQueue();
    Map<DocumentKey, MutableDocument> results = new HashMap<>();
    db.query(sql.toString())
        .binding(bindVars)
        .forEach(
            row -> {
              processRowInBackground(backgroundQueue, results, row, filter);
              if (context != null) {
                context.incrementDocumentReadCount();
              }
            });
    backgroundQueue.drain();

    // Backfill any null "document_type" columns discovered by processRowInBackground().
    documentTypeBackfiller.backfill(db);

    // Synchronize on `results` to avoid a data race with the background queue.
    synchronized (results) {
      return results;
    }
  }

  private Map<DocumentKey, MutableDocument> getAll(
      List<ResourcePath> collections,
      IndexOffset offset,
      int count,
      @Nullable Function<MutableDocument, Boolean> filter) {
    return getAll(
        collections, offset, count, /*tryFilterDocumentType*/ null, filter, /*context*/ null);
  }

  private void processRowInBackground(
      BackgroundQueue backgroundQueue,
      Map<DocumentKey, MutableDocument> results,
      Cursor row,
      @Nullable Function<MutableDocument, Boolean> filter) {
    byte[] rawDocument = row.getBlob(0);
    int readTimeSeconds = row.getInt(1);
    int readTimeNanos = row.getInt(2);
    boolean documentTypeIsNull = row.isNull(3);
    String path = row.getString(4);

    // Since scheduling background tasks incurs overhead, we only dispatch to a
    // background thread if there are still some documents remaining.
    Executor executor = row.isLast() ? Executors.DIRECT_EXECUTOR : backgroundQueue;
    executor.execute(
        () -> {
          MutableDocument document =
              decodeMaybeDocument(rawDocument, readTimeSeconds, readTimeNanos);
          if (documentTypeIsNull) {
            documentTypeBackfiller.enqueue(path, readTimeSeconds, readTimeNanos, document);
          }
          if (filter == null || filter.apply(document)) {
            synchronized (results) {
              results.put(document.getKey(), document);
            }
          }
        });
  }

  @Override
  public Map<DocumentKey, MutableDocument> getDocumentsMatchingQuery(
      Query query, IndexOffset offset, @Nonnull Set<DocumentKey> mutatedKeys) {
    return getDocumentsMatchingQuery(query, offset, mutatedKeys, /*context*/ null);
  }

  @Override
  public Map<DocumentKey, MutableDocument> getDocumentsMatchingQuery(
      Query query,
      IndexOffset offset,
      @Nonnull Set<DocumentKey> mutatedKeys,
      @Nullable QueryContext context) {
    return getAll(
        Collections.singletonList(query.getPath()),
        offset,
        Integer.MAX_VALUE,
        // Specify tryFilterDocumentType=FOUND_DOCUMENT to getAll() as an optimization, because
        // query.matches(doc) will return false for all non-"found" document types anyways.
        // See https://github.com/firebase/firebase-android-sdk/issues/7295
        DocumentType.FOUND_DOCUMENT,
        (MutableDocument doc) -> query.matches(doc) || mutatedKeys.contains(doc.getKey()),
        context);
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

  /**
   * Helper class to backfill the `document_type` column in the `remote_documents` table.
   * <p>
   * The `document_type` column was added as an optimization to skip deleted document tombstones
   * when running queries. Any time a new row is added to the `remote_documents` table it _should_
   * have its `document_type` column set to the value that matches the `contents` field. However,
   * when upgrading from an older schema version the column value for existing rows will be null
   * and this backfiller is intended to replace those null values to improve the future performance
   * of queries.
   * <p>
   * When traversing the `remote_documents` table call `add()` upon finding a row whose
   * `document_type` is null. Then, call `backfill()` later on to efficiently update the added
   * rows in batches.
   * <p>
   * This class is thread safe and all public methods may be safely called concurrently from
   * multiple threads. This makes it safe to use instances of this class from BackgroundQueue.
   *
   * @see <a href="https://github.com/firebase/firebase-android-sdk/issues/7295">#7295</a>
   */
  private static class DocumentTypeBackfiller {

    private final ConcurrentHashMap<BackfillKey, DocumentType> documentTypeByBackfillKey =
        new ConcurrentHashMap<>();

    void enqueue(String path, int readTimeSeconds, int readTimeNanos, MutableDocument document) {
      BackfillKey backfillKey = new BackfillKey(path, readTimeSeconds, readTimeNanos);
      DocumentType documentType = DocumentType.forMutableDocument(document);
      documentTypeByBackfillKey.putIfAbsent(backfillKey, documentType);
    }

    void backfill(SQLitePersistence db) {
      while (true) {
        BackfillSqlInfo backfillSqlInfo = calculateBackfillSql();
        if (backfillSqlInfo == null) {
          break;
        }
        android.util.Log.i(
            "zzyzx",
            "Backfilling document_type for " + backfillSqlInfo.numDocumentsAffected + " documents");
        db.execute(backfillSqlInfo.sql, backfillSqlInfo.bindings);
      }
    }

    private static class BackfillSqlInfo {
      final String sql;
      final Object[] bindings;
      final int numDocumentsAffected;

      BackfillSqlInfo(String sql, Object[] bindings, int numDocumentsAffected) {
        this.sql = sql;
        this.bindings = bindings;
        this.numDocumentsAffected = numDocumentsAffected;
      }
    }

    @Nullable
    BackfillSqlInfo calculateBackfillSql() {
      if (documentTypeByBackfillKey.isEmpty()) {
        return null; // short circuit
      }

      ArrayList<Object> bindings = new ArrayList<>();
      StringBuilder caseClauses = new StringBuilder();
      StringBuilder whereClauses = new StringBuilder();

      Iterator<BackfillKey> backfillKeys = documentTypeByBackfillKey.keySet().iterator();
      int numDocumentsAffected = 0;
      while (backfillKeys.hasNext() && bindings.size() < SQLitePersistence.LongQuery.LIMIT) {
        BackfillKey backfillKey = backfillKeys.next();
        DocumentType documentType = documentTypeByBackfillKey.remove(backfillKey);
        if (documentType == null) {
          continue;
        }

        numDocumentsAffected++;
        bindings.add(backfillKey.path);
        int pathBindingNumber = bindings.size();
        bindings.add(backfillKey.readTimeSeconds);
        int readTimeSecondsBindingNumber = bindings.size();
        bindings.add(backfillKey.readTimeNanos);
        int readTimeNanosBindingNumber = bindings.size();
        bindings.add(documentType.dbValue);
        int dbValueBindingNumber = bindings.size();

        caseClauses
            .append(" WHEN path=?")
            .append(pathBindingNumber)
            .append(" AND read_time_seconds=?")
            .append(readTimeSecondsBindingNumber)
            .append(" AND read_time_nanos=?")
            .append(readTimeNanosBindingNumber)
            .append(" THEN ?")
            .append(dbValueBindingNumber);

        if (whereClauses.length() > 0) {
          whereClauses.append(" OR");
        }
        whereClauses
            .append(" (path=?")
            .append(pathBindingNumber)
            .append(" AND read_time_seconds=?")
            .append(readTimeSecondsBindingNumber)
            .append(" AND read_time_nanos=?")
            .append(readTimeNanosBindingNumber)
            .append(')');
      }

      if (numDocumentsAffected == 0) {
        return null;
      }

      String sql =
          "UPDATE remote_documents SET document_type = CASE"
              + caseClauses
              + " ELSE NULL END WHERE"
              + whereClauses;

      return new BackfillSqlInfo(sql, bindings.toArray(), numDocumentsAffected);
    }

    private static class BackfillKey {
      final String path;
      final int readTimeSeconds;
      final int readTimeNanos;

      BackfillKey(String path, int readTimeSeconds, int readTimeNanos) {
        this.path = path;
        this.readTimeSeconds = readTimeSeconds;
        this.readTimeNanos = readTimeNanos;
      }

      @NonNull
      @Override
      public String toString() {
        return "DocumentTypeBackfiller.BackfillKey(path="
            + path
            + ", readTimeSeconds="
            + readTimeSeconds
            + ", readTimeNanos="
            + readTimeNanos
            + ")";
      }
    }
  }
}
