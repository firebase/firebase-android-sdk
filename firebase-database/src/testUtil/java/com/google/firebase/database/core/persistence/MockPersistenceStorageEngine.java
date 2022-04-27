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

package com.google.firebase.database.core.persistence;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.database.core.CompoundWrite;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.UserWriteRecord;
import com.google.firebase.database.core.utilities.ImmutableTree;
import com.google.firebase.database.core.utilities.Utilities;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MockPersistenceStorageEngine implements PersistenceStorageEngine {

  private final Map<Long, UserWriteRecord> writes;
  private final Map<Long, TrackedQuery> trackedQueries;
  private final Map<Long, Set<ChildKey>> trackedQueryKeys;
  private CompoundWrite serverCache = CompoundWrite.emptyWrite();
  private boolean insideTransaction = false;

  // Minor hack for testing purposes.
  boolean disableTransactionCheck = false;

  public MockPersistenceStorageEngine() {
    this(Collections.<UserWriteRecord>emptyList());
  }

  public MockPersistenceStorageEngine(List<UserWriteRecord> writes) {
    this.writes = new HashMap<Long, UserWriteRecord>();
    this.trackedQueries = new HashMap<Long, TrackedQuery>();
    this.trackedQueryKeys = new HashMap<Long, Set<ChildKey>>();
    for (UserWriteRecord record : writes) {
      this.writes.put(record.getWriteId(), record);
    }
  }

  @Override
  public Node serverCache(Path path) {
    return getCurrentNode(path);
  }

  @Override
  public void saveUserOverwrite(Path path, Node node, long writeId) {
    verifyInsideTransaction();
    this.writes.put(writeId, new UserWriteRecord(writeId, path, node, true));
  }

  @Override
  public void saveUserMerge(Path path, CompoundWrite children, long writeId) {
    verifyInsideTransaction();
    this.writes.put(writeId, new UserWriteRecord(writeId, path, children));
  }

  @Override
  public List<UserWriteRecord> loadUserWrites() {
    List<UserWriteRecord> list = new ArrayList<UserWriteRecord>(this.writes.values());
    Collections.sort(
        list,
        new Comparator<UserWriteRecord>() {
          @Override
          public int compare(UserWriteRecord r1, UserWriteRecord r2) {
            return Utilities.compareLongs(r1.getWriteId(), r2.getWriteId());
          }
        });
    return list;
  }

  @Override
  public void removeAllUserWrites() {
    verifyInsideTransaction();
    this.writes.clear();
  }

  @Override
  public void removeUserWrite(long writeId) {
    verifyInsideTransaction();
    hardAssert(this.writes.containsKey(writeId), "Tried to remove write that doesn't exist.");
    this.writes.remove(writeId);
  }

  @Override
  public void overwriteServerCache(Path path, Node node) {
    verifyInsideTransaction();
    serverCache = serverCache.addWrite(path, node);
  }

  @Override
  public void mergeIntoServerCache(Path path, Node node) {
    verifyInsideTransaction();
    CompoundWrite.emptyWrite();
    for (NamedNode child : node) {
      serverCache = serverCache.addWrite(path.child(child.getName()), child.getNode());
    }
  }

  @Override
  public void mergeIntoServerCache(Path path, CompoundWrite children) {
    verifyInsideTransaction();
    serverCache = serverCache.addWrites(path, children);
  }

  @Override
  public long serverCacheEstimatedSizeInBytes() {
    final ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.writeValueAsString(serverCache.getValue(true)).length();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void saveTrackedQuery(TrackedQuery trackedQuery) {
    verifyInsideTransaction();
    // Sanity check: If we're using the same id, it should be the same query.
    TrackedQuery existing = trackedQueries.get(trackedQuery.id);
    hardAssert(existing == null || existing.querySpec.equals(trackedQuery.querySpec));

    // Sanity check: If this queryspec already exists, it should be the same id.
    for (TrackedQuery query : trackedQueries.values()) {
      if (query.querySpec.equals(trackedQuery.querySpec)) {
        hardAssert(query.id == trackedQuery.id);
      }
    }

    this.trackedQueries.put(trackedQuery.id, trackedQuery);
  }

  @Override
  public void deleteTrackedQuery(long trackedQueryId) {
    verifyInsideTransaction();
    this.trackedQueries.remove(trackedQueryId);
    this.trackedQueryKeys.remove(trackedQueryId);
  }

  @Override
  public List<TrackedQuery> loadTrackedQueries() {
    ArrayList<TrackedQuery> queries = new ArrayList<>(this.trackedQueries.values());
    Collections.sort(
        queries,
        new Comparator<TrackedQuery>() {
          @Override
          public int compare(TrackedQuery o1, TrackedQuery o2) {
            return Utilities.compareLongs(o1.id, o2.id);
          }
        });
    return queries;
  }

  @Override
  public void resetPreviouslyActiveTrackedQueries(long lastUse) {
    for (Map.Entry<Long, TrackedQuery> entry : this.trackedQueries.entrySet()) {
      Long id = entry.getKey();
      TrackedQuery query = entry.getValue();
      if (query.active) {
        query = query.setActiveState(false).updateLastUse(lastUse);
        this.trackedQueries.put(id, query);
      }
    }
  }

  @Override
  public void saveTrackedQueryKeys(long trackedQueryId, Set<ChildKey> keys) {
    verifyInsideTransaction();
    hardAssert(
        this.trackedQueries.containsKey(trackedQueryId),
        "Can't track keys for an untracked query.");
    this.trackedQueryKeys.put(trackedQueryId, new HashSet<ChildKey>(keys));
  }

  @Override
  public void updateTrackedQueryKeys(
      long trackedQueryId, Set<ChildKey> added, Set<ChildKey> removed) {
    verifyInsideTransaction();
    hardAssert(
        this.trackedQueries.containsKey(trackedQueryId),
        "Can't track keys for an untracked query.");
    Set<ChildKey> trackedKeys = this.trackedQueryKeys.get(trackedQueryId);
    hardAssert(trackedKeys != null || removed.isEmpty(), "Can't remove keys that don't exist.");
    if (trackedKeys == null) {
      trackedKeys = new HashSet<ChildKey>();
      this.trackedQueryKeys.put(trackedQueryId, trackedKeys);
    }

    hardAssert(trackedKeys.containsAll(removed), "Can't remove keys that don't exist.");

    trackedKeys.removeAll(removed);
    trackedKeys.addAll(added);
  }

  @Override
  public Set<ChildKey> loadTrackedQueryKeys(long trackedQueryId) {
    hardAssert(
        this.trackedQueries.containsKey(trackedQueryId),
        "Can't track keys for an untracked query.");
    Set<ChildKey> trackedKeys = this.trackedQueryKeys.get(trackedQueryId);
    return trackedKeys != null
        ? new HashSet<ChildKey>(trackedKeys)
        : Collections.<ChildKey>emptySet();
  }

  @Override
  public Set<ChildKey> loadTrackedQueryKeys(Set<Long> trackedQueryIds) {
    HashSet<ChildKey> keys = new HashSet<ChildKey>();
    for (Long id : trackedQueryIds) {
      hardAssert(this.trackedQueries.containsKey(id), "Can't track keys for an untracked query.");
      keys.addAll(loadTrackedQueryKeys(id));
    }
    return keys;
  }

  @Override
  public void pruneCache(final Path prunePath, PruneForest pruneForest) {
    verifyInsideTransaction();

    for (Map.Entry<Path, Node> write : serverCache) {
      Path absoluteDataPath = write.getKey();
      hardAssert(
          prunePath.equals(absoluteDataPath) || !absoluteDataPath.contains(prunePath),
          "Pruning at " + prunePath + " but we found data higher up.");
      if (prunePath.contains(absoluteDataPath)) {
        final Path dataPath = Path.getRelative(prunePath, absoluteDataPath);
        final Node dataNode = write.getValue();
        if (pruneForest.shouldPruneUnkeptDescendants(dataPath)) {
          CompoundWrite newCache =
              pruneForest
                  .child(dataPath)
                  .foldKeptNodes(
                      CompoundWrite.emptyWrite(),
                      new ImmutableTree.TreeVisitor<Void, CompoundWrite>() {
                        @Override
                        public CompoundWrite onNodeValue(
                            Path keepPath, Void value, CompoundWrite accum) {
                          return accum.addWrite(keepPath, dataNode.getChild(keepPath));
                        }
                      });
          serverCache =
              serverCache.removeWrite(absoluteDataPath).addWrites(absoluteDataPath, newCache);
        } else {
          // NOTE: This is technically a valid scenario (e.g. you ask to prune at / but only want to
          // prune 'foo' and 'bar' and ignore everything else).  But currently our pruning will
          // explicitly prune or keep everything we know about, so if we hit this it means our
          // tracked queries and the server cache are out of sync.
          hardAssert(
              pruneForest.shouldKeep(dataPath),
              "We have data at " + dataPath + " that is neither pruned nor kept.");
        }
      }
    }
  }

  @Override
  public void beginTransaction() {
    hardAssert(
        !insideTransaction,
        "runInTransaction called when an existing transaction is already in progress.");
    insideTransaction = true;
  }

  @Override
  public void endTransaction() {
    insideTransaction = false;
  }

  @Override
  public void setTransactionSuccessful() {}

  @Override
  public void close() {}

  private void verifyInsideTransaction() {
    hardAssert(
        this.disableTransactionCheck || this.insideTransaction,
        "Transaction expected to already be in progress.");
  }

  public Node getCurrentNode(Path path) {
    return serverCache.childCompoundWrite(path).apply(EmptyNode.Empty());
  }
}
