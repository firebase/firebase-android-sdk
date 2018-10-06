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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.database.sqlite.SQLiteStatement;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.firebase.firestore.remote.WriteStream;
import com.google.firebase.firestore.util.Util;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** A mutation queue for a specific user, backed by SQLite. */
final class SQLiteMutationQueue implements MutationQueue {

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
   * An identifier for the highest numbered batch that has been acknowledged by the server. All
   * MutationBatches in this queue with batch_ids less than or equal to this value are considered to
   * have been acknowledged by the server.
   */
  private int lastAcknowledgedBatchId;

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

    // On restart, nextBatchId may end up lower than lastAcknowledgedBatchId since it's computed
    // from the queue contents, and there may be no mutations in the queue. In this case, we need
    // to reset lastAcknowledgedBatchId (which is safe since the queue must be empty).
    lastAcknowledgedBatchId = MutationBatch.UNKNOWN;
    int rows =
        db.query(
                "SELECT last_acknowledged_batch_id, last_stream_token "
                    + "FROM mutation_queues WHERE uid = ?")
            .binding(uid)
            .first(
                row -> {
                  lastAcknowledgedBatchId = row.getInt(0);
                  lastStreamToken = ByteString.copyFrom(row.getBlob(1));
                });

    if (rows == 0) {
      // Ensure we write a default entry in mutation_queues since loadNextBatchIdAcrossAllUsers()
      // depends upon every queue having an entry.
      writeMutationQueueMetadata();

    } else if (lastAcknowledgedBatchId >= nextBatchId) {
      hardAssert(isEmpty(), "Reset nextBatchId is only possible when the queue is empty");
      lastAcknowledgedBatchId = MutationBatch.UNKNOWN;
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
  public int getNextBatchId() {
    return nextBatchId;
  }

  @Override
  public void acknowledgeBatch(MutationBatch batch, ByteString streamToken) {
    int batchId = batch.getBatchId();
    hardAssert(
        batchId > lastAcknowledgedBatchId, "Mutation batchIds must be acknowledged in order");

    lastAcknowledgedBatchId = batchId;
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
        lastAcknowledgedBatchId,
        lastStreamToken.toByteArray());
  }

  @Override
  public MutationBatch addMutationBatch(Timestamp localWriteTime, List<Mutation> mutations) {
    int batchId = nextBatchId;
    nextBatchId += 1;

    MutationBatch batch = new MutationBatch(batchId, localWriteTime, mutations);
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
    }

    return batch;
  }

  @Nullable
  @Override
  public MutationBatch lookupMutationBatch(int batchId) {
    return db.query("SELECT mutations FROM mutations WHERE uid = ? AND batch_id = ?")
        .binding(uid, batchId)
        .firstValue(row -> decodeMutationBatch(row.getBlob(0)));
  }

  @Nullable
  @Override
  public MutationBatch getNextMutationBatchAfterBatchId(int batchId) {
    // All batches with batchId <= lastAcknowledgedBatchId have been acknowledged so the first
    // unacknowledged batch after batchID will have a batchID larger than both of these values.
    int nextBatchId = Math.max(batchId, lastAcknowledgedBatchId) + 1;

    return db.query(
            "SELECT mutations FROM mutations "
                + "WHERE uid = ? AND batch_id >= ? "
                + "ORDER BY batch_id ASC LIMIT 1")
        .binding(uid, nextBatchId)
        .firstValue(row -> decodeMutationBatch(row.getBlob(0)));
  }

  @Override
  public List<MutationBatch> getAllMutationBatches() {
    List<MutationBatch> result = new ArrayList<>();
    db.query("SELECT mutations FROM mutations WHERE uid = ? ORDER BY batch_id ASC")
        .binding(uid)
        .forEach(row -> result.add(decodeMutationBatch(row.getBlob(0))));
    return result;
  }

  @Override
  public List<MutationBatch> getAllMutationBatchesAffectingDocumentKey(DocumentKey documentKey) {
    String path = EncodedPath.encode(documentKey.getPath());

    List<MutationBatch> result = new ArrayList<>();
    db.query(
            "SELECT m.mutations FROM document_mutations dm, mutations m "
                + "WHERE dm.uid = ? "
                + "AND dm.path = ? "
                + "AND dm.uid = m.uid "
                + "AND dm.batch_id = m.batch_id "
                + "ORDER BY dm.batch_id")
        .binding(uid, path)
        .forEach(row -> result.add(decodeMutationBatch(row.getBlob(0))));
    return result;
  }

  @Override
  public List<MutationBatch> getAllMutationBatchesAffectingDocumentKeys(
      Iterable<DocumentKey> documentKeys) {
    List<MutationBatch> result = new ArrayList<>();
    if (!documentKeys.iterator().hasNext()) {
      return result;
    }

    // SQLite limits maximum number of host parameters to 999 (see
    // https://www.sqlite.org/limits.html). To work around this, split the given keys into several
    // smaller sets and issue a separate query for each.
    int limit = 900;
    Iterator<DocumentKey> keyIter = documentKeys.iterator();
    Set<Integer> uniqueBatchIds = new HashSet<>();
    int queriesPerformed = 0;
    while (keyIter.hasNext()) {
      ++queriesPerformed;
      StringBuilder placeholdersBuilder = new StringBuilder();
      List<String> args = new ArrayList<>();
      args.add(uid);

      for (int i = 0; keyIter.hasNext() && i < limit; i++) {
        DocumentKey key = keyIter.next();

        if (i > 0) {
          placeholdersBuilder.append(", ");
        }
        placeholdersBuilder.append("?");

        args.add(EncodedPath.encode(key.getPath()));
      }
      String placeholders = placeholdersBuilder.toString();

      db.query(
              "SELECT DISTINCT dm.batch_id, m.mutations FROM document_mutations dm, mutations m "
                  + "WHERE dm.uid = ? "
                  + "AND dm.path IN ("
                  + placeholders
                  + ") "
                  + "AND dm.uid = m.uid "
                  + "AND dm.batch_id = m.batch_id "
                  + "ORDER BY dm.batch_id")
          .binding(args.toArray())
          .forEach(
              row -> {
                int batchId = row.getInt(0);
                if (!uniqueBatchIds.contains(batchId)) {
                  uniqueBatchIds.add(batchId);
                  result.add(decodeMutationBatch(row.getBlob(1)));
                }
              });
    }

    // If more than one query was issued, batches might be in an unsorted order (batches are ordered
    // within one query's results, but not across queries). It's likely to be rare, so don't impose
    // performance penalty on the normal case.
    if (queriesPerformed > 1) {
      Collections.sort(
          result,
          (MutationBatch lhs, MutationBatch rhs) ->
              Util.compareInts(lhs.getBatchId(), rhs.getBatchId()));
    }
    return result;
  }

  @Override
  public List<MutationBatch> getAllMutationBatchesAffectingQuery(Query query) {
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
            "SELECT dm.batch_id, dm.path, m.mutations FROM document_mutations dm, mutations m "
                + "WHERE dm.uid = ? "
                + "AND dm.path >= ? "
                + "AND dm.path < ? "
                + "AND dm.uid = m.uid "
                + "AND dm.batch_id = m.batch_id "
                + "ORDER BY dm.batch_id")
        .binding(uid, prefixPath, prefixSuccessorPath)
        .forEach(
            row -> {
              // Ensure unique batches only. This works because the batches come out in order so we
              // only need to ensure that the batchId of this row is different from the preceding
              // one.
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

              result.add(decodeMutationBatch(row.getBlob(2)));
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

  private MutationBatch decodeMutationBatch(byte[] bytes) {
    try {
      return serializer.decodeMutationBatch(
          com.google.firebase.firestore.proto.WriteBatch.parseFrom(bytes));
    } catch (InvalidProtocolBufferException e) {
      throw fail("MutationBatch failed to parse: %s", e);
    }
  }
}
