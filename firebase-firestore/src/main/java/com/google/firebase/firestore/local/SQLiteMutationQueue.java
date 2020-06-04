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
import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.firebase.firestore.remote.WriteStream;
import com.google.firebase.firestore.util.Consumer;
import com.google.firebase.firestore.util.Util;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A mutation queue for a specific user, backed by SQLite. */
final class SQLiteMutationQueue implements MutationQueue {

  /**
   * On Android, SQLite Cursors are limited reading no more than 2 MB per row (despite being able to
   * write very large values). All reads of the mutations column in the mutations table need to read
   * in chunks with SUBSTR to avoid going over this limit.
   *
   * <p>The value here has to be 2 MB or smaller, while allowing for all possible other values that
   * might be selected out along with the mutations column in any given result set. Nearly 1 MB is
   * conservative, but allows all combinations of document paths and batch ids without needing to
   * figure out if the row has gotten too large.
   */
  private static final int BLOB_MAX_INLINE_LENGTH = 1000000;

  private final SQLitePersistence db;
  private final LocalSerializer serializer;

  /** The normalized uid (e.g. null => "") used in the uid column. */
  private final String uid;

  /**
   * Next value to use when assigning sequential IDs to each mutation batch.
   *
   * <p>NOTE: There can only be one SQLiteMutationQueue for a given db at a time, hence it is safe
   * to track nextBatchId as an instance-level property. Should we ever relax this constraint we'll
   * need to revisit this.
   */
  private int nextBatchId;

  /**
   * A stream token that was previously sent by the server.
   *
   * <p>See StreamingWriteRequest in datastore.proto for more details about usage.
   *
   * <p>After sending this token, earlier tokens may not be used anymore so only a single stream
   * token is retained.
   */
  private ByteString lastStreamToken;

  /**
   * Creates a new mutation queue for the given user, in the SQLite database wrapped by the
   * persistence interface.
   *
   * @param persistence The SQLite database in which to create the queue.
   * @param user The user for which to create a mutation queue.
   */
  SQLiteMutationQueue(SQLitePersistence persistence, LocalSerializer serializer, User user) {
    this.db = persistence;
    this.serializer = serializer;
    this.uid = user.isAuthenticated() ? user.getUid() : "";
    this.lastStreamToken = WriteStream.EMPTY_STREAM_TOKEN;
  }

  // MutationQueue implementation

  @Override
  public void start() {
    loadNextBatchIdAcrossAllUsers();

    int rows =
        db.query("SELECT last_stream_token FROM mutation_queues WHERE uid = ?")
            .binding(uid)
            .first(row -> lastStreamToken = ByteString.copyFrom(row.getBlob(0)));

    if (rows == 0) {
      // Ensure we write a default entry in mutation_queues since loadNextBatchIdAcrossAllUsers()
      // depends upon every queue having an entry.
      writeMutationQueueMetadata();
    }
  }

  /**
   * Returns one larger than the largest batch ID that has been stored. If there are no mutations
   * returns 0. Note that batch IDs are global.
   */
  private void loadNextBatchIdAcrossAllUsers() {
    // The dependent query below turned out to be ~500x faster than any other technique, given just
    // the primary key index on (uid, batch_id).
    //
    // naive: SELECT MAX(batch_id) FROM mutations
    // group: SELECT uid, MAX(batch_id) FROM mutations GROUP BY uid
    // join:  SELECT q.uid, MAX(b.batch_id) FROM mutation_queues q, mutations b WHERE q.uid = b.uid
    //
    // Given 1E9 mutations divvied up among 10 queues, timings looked like this:
    //
    // method       seconds
    // join:        0.3187
    // group_max:   0.1985
    // naive_scan:  0.1041
    // dependent:   0.0002

    List<String> uids = new ArrayList<>();
    db.query("SELECT uid FROM mutation_queues").forEach(row -> uids.add(row.getString(0)));

    nextBatchId = 0;
    for (String uid : uids) {
      db.query("SELECT MAX(batch_id) FROM mutations WHERE uid = ?")
          .binding(uid)
          .forEach(row -> nextBatchId = Math.max(nextBatchId, row.getInt(0)));
    }

    nextBatchId += 1;
  }

  @Override
  public boolean isEmpty() {
    return db.query("SELECT batch_id FROM mutations WHERE uid = ? LIMIT 1").binding(uid).isEmpty();
  }

  @Override
  public void acknowledgeBatch(MutationBatch batch, ByteString streamToken) {
    lastStreamToken = checkNotNull(streamToken);
    writeMutationQueueMetadata();
  }

  @Override
  public ByteString getLastStreamToken() {
    return lastStreamToken;
  }

  @Override
  public void setLastStreamToken(ByteString streamToken) {
    lastStreamToken = checkNotNull(streamToken);
    writeMutationQueueMetadata();
  }

  private void writeMutationQueueMetadata() {
    db.execute(
        "INSERT OR REPLACE INTO mutation_queues "
            + "(uid, last_acknowledged_batch_id, last_stream_token) "
            + "VALUES (?, ?, ?)",
        uid,
        -1,
        lastStreamToken.toByteArray());
  }

  @Override
  public MutationBatch addMutationBatch(
      Timestamp localWriteTime, List<Mutation> baseMutations, List<Mutation> mutations) {
    int batchId = nextBatchId;
    nextBatchId += 1;

    MutationBatch batch = new MutationBatch(batchId, localWriteTime, baseMutations, mutations);
    MessageLite proto = serializer.encodeMutationBatch(batch);

    db.execute(
        "INSERT INTO mutations (uid, batch_id, mutations) VALUES (?, ?, ?)",
        uid,
        batchId,
        proto.toByteArray());

    // PORTING NOTE: Unlike LevelDB, these entries must be unique.
    // Since user and batchId are fixed within this function body, it's enough to track unique keys
    // added in this batch.
    Set<DocumentKey> inserted = new HashSet<>();

    SQLiteStatement indexInserter =
        db.prepare("INSERT INTO document_mutations (uid, path, batch_id) VALUES (?, ?, ?)");
    for (Mutation mutation : mutations) {
      DocumentKey key = mutation.getKey();
      if (!inserted.add(key)) {
        continue;
      }

      String path = EncodedPath.encode(key.getPath());
      db.execute(indexInserter, uid, path, batchId);

      db.getIndexManager().addToCollectionParentIndex(key.getPath().popLast());
    }

    return batch;
  }

  @Nullable
  @Override
  public MutationBatch lookupMutationBatch(int batchId) {
    return db.query("SELECT SUBSTR(mutations, 1, ?) FROM mutations WHERE uid = ? AND batch_id = ?")
        .binding(BLOB_MAX_INLINE_LENGTH, uid, batchId)
        .firstValue(row -> decodeInlineMutationBatch(batchId, row.getBlob(0)));
  }

  @Nullable
  @Override
  public MutationBatch getNextMutationBatchAfterBatchId(int batchId) {
    int nextBatchId = batchId + 1;

    return db.query(
            "SELECT batch_id, SUBSTR(mutations, 1, ?) FROM mutations "
                + "WHERE uid = ? AND batch_id >= ? "
                + "ORDER BY batch_id ASC LIMIT 1")
        .binding(BLOB_MAX_INLINE_LENGTH, uid, nextBatchId)
        .firstValue(row -> decodeInlineMutationBatch(row.getInt(0), row.getBlob(1)));
  }

  @Override
  public int getHighestUnacknowledgedBatchId() {
    return db.query("SELECT IFNULL(MAX(batch_id), ?) FROM mutations WHERE uid = ?")
        .binding(MutationBatch.UNKNOWN, uid)
        .firstValue(row -> row.getInt(0));
  }

  @Override
  public List<MutationBatch> getAllMutationBatches() {
    List<MutationBatch> result = new ArrayList<>();
    db.query(
            "SELECT batch_id, SUBSTR(mutations, 1, ?) "
                + "FROM mutations "
                + "WHERE uid = ? ORDER BY batch_id ASC")
        .binding(BLOB_MAX_INLINE_LENGTH, uid)
        .forEach(row -> result.add(decodeInlineMutationBatch(row.getInt(0), row.getBlob(1))));
    return result;
  }

  @Override
  public List<MutationBatch> getAllMutationBatchesAffectingDocumentKey(DocumentKey documentKey) {
    String path = EncodedPath.encode(documentKey.getPath());

    List<MutationBatch> result = new ArrayList<>();
    db.query(
            "SELECT m.batch_id, SUBSTR(m.mutations, 1, ?) "
                + "FROM document_mutations dm, mutations m "
                + "WHERE dm.uid = ? "
                + "AND dm.path = ? "
                + "AND dm.uid = m.uid "
                + "AND dm.batch_id = m.batch_id "
                + "ORDER BY dm.batch_id")
        .binding(BLOB_MAX_INLINE_LENGTH, uid, path)
        .forEach(row -> result.add(decodeInlineMutationBatch(row.getInt(0), row.getBlob(1))));

    return result;
  }

  @Override
  public List<MutationBatch> getAllMutationBatchesAffectingDocumentKeys(
      Iterable<DocumentKey> documentKeys) {
    List<Object> args = new ArrayList<>();
    for (DocumentKey key : documentKeys) {
      args.add(EncodedPath.encode(key.getPath()));
    }

    SQLitePersistence.LongQuery longQuery =
        new SQLitePersistence.LongQuery(
            db,
            "SELECT DISTINCT dm.batch_id, SUBSTR(m.mutations, 1, ?) "
                + "FROM document_mutations dm, mutations m "
                + "WHERE dm.uid = ? "
                + "AND dm.path IN (",
            Arrays.asList(BLOB_MAX_INLINE_LENGTH, uid),
            args,
            ") "
                + "AND dm.uid = m.uid "
                + "AND dm.batch_id = m.batch_id "
                + "ORDER BY dm.batch_id");

    List<MutationBatch> result = new ArrayList<>();
    Set<Integer> uniqueBatchIds = new HashSet<>();
    while (longQuery.hasMoreSubqueries()) {
      longQuery
          .performNextSubquery()
          .forEach(
              row -> {
                int batchId = row.getInt(0);
                if (!uniqueBatchIds.contains(batchId)) {
                  uniqueBatchIds.add(batchId);
                  result.add(decodeInlineMutationBatch(batchId, row.getBlob(1)));
                }
              });
    }

    // If more than one query was issued, batches might be in an unsorted order (batches are ordered
    // within one query's results, but not across queries). It's likely to be rare, so don't impose
    // performance penalty on the normal case.
    if (longQuery.getSubqueriesPerformed() > 1) {
      Collections.sort(
          result,
          (MutationBatch lhs, MutationBatch rhs) ->
              Util.compareIntegers(lhs.getBatchId(), rhs.getBatchId()));
    }
    return result;
  }

  @Override
  public List<MutationBatch> getAllMutationBatchesAffectingQuery(Query query) {
    hardAssert(
        !query.isCollectionGroupQuery(),
        "CollectionGroup queries should be handled in LocalDocumentsView");
    // Use the query path as a prefix for testing if a document matches the query.
    ResourcePath prefix = query.getPath();
    int immediateChildrenPathLength = prefix.length() + 1;

    // Scan the document_mutations table looking for documents whose path has a prefix that matches
    // the query path.
    //
    // The most obvious way to do this would be with a LIKE query with a trailing wildcard (e.g.
    // path LIKE 'foo/%'). Unfortunately SQLite does not convert a trailing wildcard like that into
    // the equivalent range scan so a LIKE query ends up being a table scan. The query below is
    // equivalent but hits the index on both uid and path, so it's much faster.

    // TODO: Actually implement a single-collection query
    //
    // This is actually executing an ancestor query, traversing the whole subtree below the
    // collection which can be horrifically inefficient for some structures. The right way to
    // solve this is to implement the full value index, but that's not in the cards in the near
    // future so this is the best we can do for the moment.
    String prefixPath = EncodedPath.encode(prefix);
    String prefixSuccessorPath = EncodedPath.prefixSuccessor(prefixPath);

    List<MutationBatch> result = new ArrayList<>();

    db.query(
            "SELECT dm.batch_id, dm.path, SUBSTR(m.mutations, 1, ?) "
                + "FROM document_mutations dm, mutations m "
                + "WHERE dm.uid = ? "
                + "AND dm.path >= ? "
                + "AND dm.path < ? "
                + "AND dm.uid = m.uid "
                + "AND dm.batch_id = m.batch_id "
                + "ORDER BY dm.batch_id")
        .binding(BLOB_MAX_INLINE_LENGTH, uid, prefixPath, prefixSuccessorPath)
        .forEach(
            row -> {
              // Ensure unique batches only. This works because the batches come out in order so
              // we only need to ensure that the batchId of this row is different from the
              // preceding one.
              int batchId = row.getInt(0);
              int size = result.size();
              if (size > 0 && batchId == result.get(size - 1).getBatchId()) {
                return;
              }

              // The query is actually returning any path that starts with the query path prefix
              // which may include documents in subcollections. For example, a query on 'rooms'
              // will return rooms/abc/messages/xyx but we shouldn't match it. Fix this by
              // discarding rows with document keys more than one segment longer than the query
              // path.
              ResourcePath path = EncodedPath.decodeResourcePath(row.getString(1));
              if (path.length() != immediateChildrenPathLength) {
                return;
              }

              result.add(decodeInlineMutationBatch(batchId, row.getBlob(2)));
            });

    return result;
  }

  @Override
  public void removeMutationBatch(MutationBatch batch) {
    SQLiteStatement mutationDeleter =
        db.prepare("DELETE FROM mutations WHERE uid = ? AND batch_id = ?");

    SQLiteStatement indexDeleter =
        db.prepare("DELETE FROM document_mutations WHERE uid = ? AND path = ? AND batch_id = ?");

    int batchId = batch.getBatchId();
    int deleted = db.execute(mutationDeleter, uid, batchId);
    hardAssert(deleted != 0, "Mutation batch (%s, %d) did not exist", uid, batch.getBatchId());

    for (Mutation mutation : batch.getMutations()) {
      DocumentKey key = mutation.getKey();
      String path = EncodedPath.encode(key.getPath());
      db.execute(indexDeleter, uid, path, batchId);
      db.getReferenceDelegate().removeMutationReference(key);
    }
  }

  @Override
  public void performConsistencyCheck() {
    if (!isEmpty()) {
      return;
    }

    // Verify that there are no entries in the document_mutations index if the queue is empty.
    List<ResourcePath> danglingMutationReferences = new ArrayList<>();
    db.query("SELECT path FROM document_mutations WHERE uid = ?")
        .binding(uid)
        .forEach(
            row -> {
              ResourcePath path = EncodedPath.decodeResourcePath(row.getString(0));
              danglingMutationReferences.add(path);
            });

    hardAssert(
        danglingMutationReferences.isEmpty(),
        "Document leak -- detected dangling mutation references when queue is empty. "
            + "Dangling keys: %s",
        danglingMutationReferences);
  }

  /**
   * Decodes mutation batch bytes obtained via substring. If the blob is smaller than
   * BLOB_MAX_INLINE_LENGTH, executes additional queries to load the rest of the blob.
   *
   * @param batchId The batch ID of the row containing the bytes, for fallback lookup if the value
   *     is too large.
   * @param bytes The bytes of the first chunk of the mutation batch. Should be obtained via
   *     SUBSTR(mutations, 1, BLOB_MAX_INLINE_LENGTH).
   */
  private MutationBatch decodeInlineMutationBatch(int batchId, byte[] bytes) {
    try {
      if (bytes.length < BLOB_MAX_INLINE_LENGTH) {
        return serializer.decodeMutationBatch(
            com.google.firebase.firestore.proto.WriteBatch.parseFrom(bytes));
      }

      BlobAccumulator accumulator = new BlobAccumulator(bytes);
      while (accumulator.more) {
        // As we read in chunks the start of the next chunk should be the total accumulated length
        // plus 1 (since SUBSTR() counts from 1). The second argument is not adjusted because it's
        // the length of the chunk, not the end index.
        int start = accumulator.numChunks() * BLOB_MAX_INLINE_LENGTH + 1;

        db.query("SELECT SUBSTR(mutations, ?, ?) FROM mutations WHERE uid = ? AND batch_id = ?")
            .binding(start, BLOB_MAX_INLINE_LENGTH, uid, batchId)
            .first(accumulator);
      }

      ByteString blob = accumulator.result();
      return serializer.decodeMutationBatch(
          com.google.firebase.firestore.proto.WriteBatch.parseFrom(blob));
    } catch (InvalidProtocolBufferException e) {
      throw fail("MutationBatch failed to parse: %s", e);
    }
  }

  /**
   * Explicit consumer of blob chunks, accumulating the chunks and wrapping them in a single
   * ByteString. Accepts a Cursor whose results include the blob in column 0.
   *
   * <p>(This is a named class here to allow decodeInlineMutationBlock to access the result of the
   * accumulation.)
   */
  private static class BlobAccumulator implements Consumer<Cursor> {
    private final ArrayList<ByteString> chunks = new ArrayList<>();
    private boolean more = true;

    BlobAccumulator(byte[] firstChunk) {
      addChunk(firstChunk);
    }

    int numChunks() {
      return chunks.size();
    }

    ByteString result() {
      // Not actually a copy; this creates a balanced rope-like structure that reuses the given
      // ByteStrings as a part of its representation.
      return ByteString.copyFrom(chunks);
    }

    @Override
    public void accept(Cursor row) {
      byte[] bytes = row.getBlob(0);
      addChunk(bytes);
      if (bytes.length < BLOB_MAX_INLINE_LENGTH) {
        more = false;
      }
    }

    private void addChunk(byte[] bytes) {
      ByteString wrapped = ByteString.copyFrom(bytes);
      chunks.add(wrapped);
    }
  }
}
