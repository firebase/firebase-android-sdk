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

import android.database.sqlite.SQLiteStatement;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.Consumer;
import com.google.protobuf.InvalidProtocolBufferException;

/** Cached Queries backed by SQLite. */
final class SQLiteTargetCache implements TargetCache {

  private final SQLitePersistence db;
  private final LocalSerializer localSerializer;

  private int highestTargetId;
  private long lastListenSequenceNumber;
  private SnapshotVersion lastRemoteSnapshotVersion = SnapshotVersion.NONE;
  private long targetCount;

  SQLiteTargetCache(SQLitePersistence db, LocalSerializer localSerializer) {
    this.db = db;
    this.localSerializer = localSerializer;
  }

  void start() {
    // Store exactly one row in the table. If the row exists at all, it's the global metadata.
    int found =
        db.query(
                "SELECT highest_target_id, highest_listen_sequence_number, "
                    + "last_remote_snapshot_version_seconds, last_remote_snapshot_version_nanos, "
                    + "target_count FROM target_globals LIMIT 1")
            .first(
                row -> {
                  highestTargetId = row.getInt(0);
                  lastListenSequenceNumber = row.getInt(1);
                  lastRemoteSnapshotVersion =
                      new SnapshotVersion(new Timestamp(row.getLong(2), row.getInt(3)));
                  targetCount = row.getLong(4);
                });
    hardAssert(found == 1, "Missing target_globals entry");
  }

  @Override
  public int getHighestTargetId() {
    return highestTargetId;
  }

  @Override
  public long getHighestListenSequenceNumber() {
    return lastListenSequenceNumber;
  }

  @Override
  public long getTargetCount() {
    return targetCount;
  }

  @Override
  public void forEachTarget(Consumer<TargetData> consumer) {
    db.query("SELECT target_proto FROM targets")
        .forEach(row -> consumer.accept(decodeTargetData(row.getBlob(0))));
  }

  @Override
  public SnapshotVersion getLastRemoteSnapshotVersion() {
    return lastRemoteSnapshotVersion;
  }

  @Override
  public void setLastRemoteSnapshotVersion(SnapshotVersion snapshotVersion) {
    lastRemoteSnapshotVersion = snapshotVersion;
    writeMetadata();
  }

  private void saveTargetData(TargetData targetData) {
    int targetId = targetData.getTargetId();
    String canonicalId = targetData.getTarget().getCanonicalId();
    Timestamp version = targetData.getSnapshotVersion().getTimestamp();

    com.google.firebase.firestore.proto.Target targetProto =
        localSerializer.encodeTargetData(targetData);

    db.execute(
        "INSERT OR REPLACE INTO targets ("
            + "target_id, "
            + "canonical_id, "
            + "snapshot_version_seconds, "
            + "snapshot_version_nanos, "
            + "resume_token, "
            + "last_listen_sequence_number, "
            + "target_proto) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
        targetId,
        canonicalId,
        version.getSeconds(),
        version.getNanoseconds(),
        targetData.getResumeToken().toByteArray(),
        targetData.getSequenceNumber(),
        targetProto.toByteArray());
  }

  private boolean updateMetadata(TargetData targetData) {
    boolean wasUpdated = false;

    if (targetData.getTargetId() > highestTargetId) {
      highestTargetId = targetData.getTargetId();
      wasUpdated = true;
    }

    if (targetData.getSequenceNumber() > lastListenSequenceNumber) {
      lastListenSequenceNumber = targetData.getSequenceNumber();
      wasUpdated = true;
    }

    return wasUpdated;
  }

  @Override
  public void addTargetData(TargetData targetData) {
    saveTargetData(targetData);
    // PORTING NOTE: The query_targets index is maintained by SQLite.

    updateMetadata(targetData);
    targetCount++;
    writeMetadata();
  }

  @Override
  public void updateTargetData(TargetData targetData) {
    saveTargetData(targetData);

    if (updateMetadata(targetData)) {
      writeMetadata();
    }
  }

  private void writeMetadata() {
    db.execute(
        "UPDATE target_globals SET highest_target_id = ?, highest_listen_sequence_number = ?, "
            + "last_remote_snapshot_version_seconds = ?, last_remote_snapshot_version_nanos = ?, "
            + "target_count = ?",
        highestTargetId,
        lastListenSequenceNumber,
        lastRemoteSnapshotVersion.getTimestamp().getSeconds(),
        lastRemoteSnapshotVersion.getTimestamp().getNanoseconds(),
        targetCount);
  }

  private void removeTarget(int targetId) {
    removeMatchingKeysForTargetId(targetId);
    db.execute("DELETE FROM targets WHERE target_id = ?", targetId);
    targetCount--;
  }

  @Override
  public void removeTargetData(TargetData targetData) {
    int targetId = targetData.getTargetId();
    removeTarget(targetId);
    writeMetadata();
  }

  /**
   * Drops any targets with sequence number less than or equal to the upper bound, excepting those
   * present in `activeTargetIds`. Document associations for the removed targets are also removed.
   * Returns the number of targets removed.
   */
  int removeQueries(long upperBound, SparseArray<?> activeTargetIds) {
    int[] count = new int[1];
    // SQLite has a max sql statement size, so there is technically a possibility that including a
    // an IN clause in this query to filter `activeTargetIds` could overflow. Rather than deal with
    // that, we filter out live targets from the result set.
    db.query("SELECT target_id FROM targets WHERE last_listen_sequence_number <= ?")
        .binding(upperBound)
        .forEach(
            row -> {
              int targetId = row.getInt(0);
              if (activeTargetIds.get(targetId) == null) {
                removeTarget(targetId);
                count[0]++;
              }
            });
    writeMetadata();
    return count[0];
  }

  @Nullable
  @Override
  public TargetData getTargetData(Target target) {
    // Querying the targets table by canonical_id may yield more than one result because
    // canonical_id values are not required to be unique per target. This query depends on the
    // query_targets index to be efficient.
    String canonicalId = target.getCanonicalId();
    TargetDataHolder result = new TargetDataHolder();
    db.query("SELECT target_proto FROM targets WHERE canonical_id = ?")
        .binding(canonicalId)
        .forEach(
            row -> {
              // TODO: break out early if found.
              TargetData found = decodeTargetData(row.getBlob(0));

              // After finding a potential match, check that the query is actually equal to the
              // requested query.
              if (target.equals(found.getTarget())) {
                result.targetData = found;
              }
            });
    return result.targetData;
  }

  private static class TargetDataHolder {
    TargetData targetData;
  }

  private TargetData decodeTargetData(byte[] bytes) {
    try {
      return localSerializer.decodeTargetData(
          com.google.firebase.firestore.proto.Target.parseFrom(bytes));
    } catch (InvalidProtocolBufferException e) {
      throw fail("TargetData failed to parse: %s", e);
    }
  }

  // Matching key tracking

  @Override
  public void addMatchingKeys(ImmutableSortedSet<DocumentKey> keys, int targetId) {
    // PORTING NOTE: The reverse index (document_targets) is maintained by SQLite.

    // When updates come in we treat those as added keys, which means these inserts won't
    // necessarily be unique between invocations. This INSERT statement uses the IGNORE conflict
    // resolution strategy to avoid failing on any attempts to add duplicate entries. This works
    // because there's no additional information in the row. If we want to track additional data
    // this will probably need to become INSERT OR REPLACE instead.
    SQLiteStatement inserter =
        db.prepare("INSERT OR IGNORE INTO target_documents (target_id, path) VALUES (?, ?)");

    ReferenceDelegate delegate = db.getReferenceDelegate();
    for (DocumentKey key : keys) {
      String path = EncodedPath.encode(key.getPath());
      db.execute(inserter, targetId, path);
      delegate.addReference(key);
    }
  }

  @Override
  public void removeMatchingKeys(ImmutableSortedSet<DocumentKey> keys, int targetId) {
    // PORTING NOTE: The reverse index (document_targets) is maintained by SQLite.
    SQLiteStatement deleter =
        db.prepare("DELETE FROM target_documents WHERE target_id = ? AND path = ?");

    ReferenceDelegate delegate = db.getReferenceDelegate();
    for (DocumentKey key : keys) {
      String path = EncodedPath.encode(key.getPath());
      db.execute(deleter, targetId, path);
      delegate.removeReference(key);
    }
  }

  private void removeMatchingKeysForTargetId(int targetId) {
    db.execute("DELETE FROM target_documents WHERE target_id = ?", targetId);
  }

  @Override
  public ImmutableSortedSet<DocumentKey> getMatchingKeysForTargetId(int targetId) {
    final DocumentKeysHolder holder = new DocumentKeysHolder();
    db.query("SELECT path FROM target_documents WHERE target_id = ?")
        .binding(targetId)
        .forEach(
            row -> {
              String path = row.getString(0);
              DocumentKey key = DocumentKey.fromPath(EncodedPath.decodeResourcePath(path));
              holder.keys = holder.keys.insert(key);
            });
    return holder.keys;
  }

  // A holder that can accumulate changes to the key set within the closure
  private static class DocumentKeysHolder {
    ImmutableSortedSet<DocumentKey> keys = DocumentKey.emptyKeySet();
  }

  @Override
  public boolean containsKey(DocumentKey key) {
    String path = EncodedPath.encode(key.getPath());
    return !db.query(
            "SELECT target_id FROM target_documents WHERE path = ? AND target_id != 0 LIMIT 1")
        .binding(path)
        .isEmpty();
  }
}
