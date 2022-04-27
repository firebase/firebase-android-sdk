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

package com.google.firebase.database.core;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.google.firebase.database.core.utilities.Predicate;
import com.google.firebase.database.core.view.CacheNode;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Index;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Defines a single user-initiated write operation. May be the result of a set(), transaction(), or
 * update() call. In the case of a set() or transaction, snap wil be non-null. In the case of an
 * update(), children will be non-null.
 */
public class WriteTree {

  /**
   * A tree tracking the result of applying all visible writes. This does not include transactions
   * with applyLocally=false or writes that are completely shadowed by other writes.
   */
  private CompoundWrite visibleWrites;

  /**
   * A list of all pending writes, regardless of visibility and shadowed-ness. Used to calculate
   * arbitrary sets of the changed data, such as hidden writes (from transactions) or changes with
   * certain writes excluded (also used by transactions).
   */
  private List<UserWriteRecord> allWrites;

  private Long lastWriteId;

  /**
   * WriteTree tracks all pending user-initiated writes and has methods to calculate the result of
   * merging them with underlying server data (to create "event cache" data). Pending writes are
   * added with addOverwrite() and addMerge(), and removed with removeWrite().
   */
  public WriteTree() {
    this.visibleWrites = CompoundWrite.emptyWrite();
    this.allWrites = new ArrayList<UserWriteRecord>();
    this.lastWriteId = -1L;
  }

  /**
   * Create a new WriteTreeRef for the given path. For use with a new sync point at the given path.
   */
  public WriteTreeRef childWrites(Path path) {
    return new WriteTreeRef(path, this);
  }

  /** Record a new overwrite from user code. */
  public void addOverwrite(Path path, Node snap, Long writeId, boolean visible) {
    hardAssert(writeId > this.lastWriteId); // Stacking an older write on top of newer ones
    this.allWrites.add(new UserWriteRecord(writeId, path, snap, visible));
    if (visible) {
      this.visibleWrites = this.visibleWrites.addWrite(path, snap);
    }
    this.lastWriteId = writeId;
  }

  /** Record a new merge from user code. */
  public void addMerge(Path path, CompoundWrite changedChildren, Long writeId) {
    hardAssert(writeId > this.lastWriteId); // Stacking an older write on top of newer ones
    this.allWrites.add(new UserWriteRecord(writeId, path, changedChildren));
    this.visibleWrites = this.visibleWrites.addWrites(path, changedChildren);
    this.lastWriteId = writeId;
  }

  public UserWriteRecord getWrite(long writeId) {
    for (UserWriteRecord record : this.allWrites) {
      if (record.getWriteId() == writeId) {
        return record;
      }
    }
    return null;
  }

  public List<UserWriteRecord> purgeAllWrites() {
    List<UserWriteRecord> purgedWrites = new ArrayList<UserWriteRecord>(this.allWrites);
    // Reset everything
    this.visibleWrites = CompoundWrite.emptyWrite();
    this.allWrites = new ArrayList<UserWriteRecord>();
    return purgedWrites;
  }

  /**
   * Remove a write (either an overwrite or merge) that has been successfully acknowledge by the
   * server. Recalculates the tree if necessary. We return whether the write may have been visible,
   * meaning views need to reevaluate.
   *
   * @return true if the write may have been visible (meaning we'll need to reevaluate / raise
   *     events as a result).
   */
  public boolean removeWrite(long writeId) {
    // Note: disabling this check. It could be a transaction that preempted another transaction, and
    // thus was applied out of order.
    // var validClear = revert || this.allWrites_.length === 0 ||
    //      writeId <= this.allWrites_[0].writeId;
    // fb.core.util.assert(validClear, "Either we don't have this write, or it's the first one in
    //      the queue");

    // TODO: maybe use hashmap
    UserWriteRecord writeToRemove = null;
    int idx = 0;
    for (UserWriteRecord record : this.allWrites) {
      if (record.getWriteId() == writeId) {
        writeToRemove = record;
        break;
      }
      idx++;
    }
    hardAssert(writeToRemove != null, "removeWrite called with nonexistent writeId");

    this.allWrites.remove(writeToRemove);

    boolean removedWriteWasVisible = writeToRemove.isVisible();
    boolean removedWriteOverlapsWithOtherWrites = false;
    int i = this.allWrites.size() - 1;

    while (removedWriteWasVisible && i >= 0) {
      UserWriteRecord currentWrite = this.allWrites.get(i);
      if (currentWrite.isVisible()) {
        if (i >= idx && this.recordContainsPath(currentWrite, writeToRemove.getPath())) {
          // The removed write was completely shadowed by a subsequent write.
          removedWriteWasVisible = false;
        } else if (writeToRemove.getPath().contains(currentWrite.getPath())) {
          // Either we're covering some writes or they're covering part of us (depending on which
          // came first).
          removedWriteOverlapsWithOtherWrites = true;
        }
      }
      i--;
    }

    if (!removedWriteWasVisible) {
      return false;
    } else if (removedWriteOverlapsWithOtherWrites) {
      // There's some shadowing going on. Just rebuild the visible writes from scratch.
      this.resetTree();
      return true;
    } else {
      // There's no shadowing.  We can safely just remove the write(s) from visibleWrites.
      if (writeToRemove.isOverwrite()) {
        this.visibleWrites = this.visibleWrites.removeWrite(writeToRemove.getPath());
      } else {
        for (Map.Entry<Path, Node> entry : writeToRemove.getMerge()) {
          Path path = entry.getKey();
          this.visibleWrites = this.visibleWrites.removeWrite(writeToRemove.getPath().child(path));
        }
      }
      return true;
    }
  }

  /**
   * Return a complete snapshot for the given path if there's visible write data at that path, else
   * null. No server data is considered.
   */
  public Node getCompleteWriteData(Path path) {
    return this.visibleWrites.getCompleteNode(path);
  }

  /**
   * Given optional, underlying server data, and an optional set of constraints (exclude some sets,
   * include hidden writes), attempt to calculate a complete snapshot for the given path
   */
  public Node calcCompleteEventCache(Path treePath, Node completeServerCache) {
    return this.calcCompleteEventCache(treePath, completeServerCache, new ArrayList<Long>());
  }

  public Node calcCompleteEventCache(
      Path treePath, Node completeServerCache, List<Long> writeIdsToExclude) {
    return this.calcCompleteEventCache(treePath, completeServerCache, writeIdsToExclude, false);
  }

  public Node calcCompleteEventCache(
      final Path treePath,
      Node completeServerCache,
      final List<Long> writeIdsToExclude,
      final boolean includeHiddenWrites) {
    if (writeIdsToExclude.isEmpty() && !includeHiddenWrites) {
      Node shadowingNode = this.visibleWrites.getCompleteNode(treePath);
      if (shadowingNode != null) {
        return shadowingNode;
      } else {
        CompoundWrite subMerge = this.visibleWrites.childCompoundWrite(treePath);
        if (subMerge.isEmpty()) {
          return completeServerCache;
        } else if (completeServerCache == null && !subMerge.hasCompleteWrite(Path.getEmptyPath())) {
          // We wouldn't have a complete snapshot, since there's no underlying data and no complete
          // shadow
          return null;
        } else {
          Node layeredCache;
          if (completeServerCache != null) {
            layeredCache = completeServerCache;
          } else {
            layeredCache = EmptyNode.Empty();
          }
          return subMerge.apply(layeredCache);
        }
      }
    } else {
      CompoundWrite merge = this.visibleWrites.childCompoundWrite(treePath);
      if (!includeHiddenWrites && merge.isEmpty()) {
        return completeServerCache;
      } else {
        // If the server cache is null, and we don't have a complete cache, we need to return null
        if (!includeHiddenWrites
            && completeServerCache == null
            && !merge.hasCompleteWrite(Path.getEmptyPath())) {
          return null;
        } else {
          Predicate<UserWriteRecord> filter =
              new Predicate<UserWriteRecord>() {
                @Override
                public boolean evaluate(UserWriteRecord write) {
                  return (write.isVisible() || includeHiddenWrites)
                      && !writeIdsToExclude.contains(write.getWriteId())
                      && (write.getPath().contains(treePath) || treePath.contains(write.getPath()));
                }
              };
          Node layeredCache;
          CompoundWrite mergeAtPath = WriteTree.layerTree(this.allWrites, filter, treePath);
          layeredCache = completeServerCache != null ? completeServerCache : EmptyNode.Empty();
          return mergeAtPath.apply(layeredCache);
        }
      }
    }
  }

  /**
   * With underlying server data, attempt to return a children node of children that we have
   * complete data for. Used when creating new views, to pre-fill their complete event children
   * snapshot.
   */
  public Node calcCompleteEventChildren(Path treePath, Node completeServerChildren) {
    Node completeChildren = EmptyNode.Empty();
    Node topLevelSet = this.visibleWrites.getCompleteNode(treePath);
    if (topLevelSet != null) {
      if (!topLevelSet.isLeafNode()) {
        // we're shadowing everything. Return the children.
        for (NamedNode childEntry : topLevelSet) {
          completeChildren =
              completeChildren.updateImmediateChild(childEntry.getName(), childEntry.getNode());
        }
      }
      return completeChildren;
    } else {
      // Layer any children we have on top of this
      // We know we don't have a top-level set, so just enumerate existing children, and apply any
      // updates
      CompoundWrite merge = this.visibleWrites.childCompoundWrite(treePath);
      for (NamedNode entry : completeServerChildren) {
        Node node = merge.childCompoundWrite(new Path(entry.getName())).apply(entry.getNode());
        completeChildren = completeChildren.updateImmediateChild(entry.getName(), node);
      }
      // Add any complete children we have from the set
      for (NamedNode node : merge.getCompleteChildren()) {
        completeChildren = completeChildren.updateImmediateChild(node.getName(), node.getNode());
      }
      return completeChildren;
    }
  }

  /**
   * Given that the underlying server data has updated, determine what, if anything, needs to be
   * applied to the event cache.
   *
   * <p>Possibilities:
   *
   * <p>1. No writes are shadowing. Events should be raised, the snap to be applied comes from the
   * server data
   *
   * <p>2. Some write is completely shadowing. No events to be raised
   *
   * <p>3. Is partially shadowed. Events
   *
   * <p>Either existingEventSnap or existingServerSnap must exist
   */
  public Node calcEventCacheAfterServerOverwrite(
      Path treePath,
      final Path childPath,
      final Node existingEventSnap,
      final Node existingServerSnap) {
    hardAssert(
        existingEventSnap != null || existingServerSnap != null,
        "Either existingEventSnap or existingServerSnap must exist");
    Path path = treePath.child(childPath);
    if (this.visibleWrites.hasCompleteWrite(path)) {
      // At this point we can probably guarantee that we're in case 2, meaning no events
      // May need to check visibility while doing the findRootMostValueAndPath call
      return null;
    } else {
      // No complete shadowing. We're either partially shadowing or not shadowing at all.
      CompoundWrite childMerge = this.visibleWrites.childCompoundWrite(path);
      if (childMerge.isEmpty()) {
        // We're not shadowing at all. Case 1
        return existingServerSnap.getChild(childPath);
      } else {
        // This could be more efficient if the serverNode + updates doesn't change the eventSnap
        // However this is tricky to find out, since user updates don't necessary change the server
        // snap, e.g. priority updates on empty nodes, or deep deletes. Another special case is if
        // the server adds nodes, but doesn't change any existing writes. It is therefore not enough
        // to only check if the updates change the serverNode.
        // Maybe check if the merge tree contains these special cases and only do a full overwrite
        // in that case?
        return childMerge.apply(existingServerSnap.getChild(childPath));
      }
    }
  }

  /**
   * Returns a complete child for a given server snap after applying all user writes or null if
   * there is no complete child for this ChildKey.
   */
  public Node calcCompleteChild(Path treePath, ChildKey childKey, CacheNode existingServerSnap) {
    Path path = treePath.child(childKey);
    Node shadowingNode = this.visibleWrites.getCompleteNode(path);
    if (shadowingNode != null) {
      return shadowingNode;
    } else {
      if (existingServerSnap.isCompleteForChild(childKey)) {
        CompoundWrite childMerge = this.visibleWrites.childCompoundWrite(path);
        return childMerge.apply(existingServerSnap.getNode().getImmediateChild(childKey));
      } else {
        return null;
      }
    }
  }

  /**
   * Returns a node if there is a complete overwrite for this path. More specifically, if there is a
   * write at a higher path, this will return the child of that write relative to the write and this
   * path. Returns null if there is no write at this path.
   */
  public Node shadowingWrite(Path path) {
    return this.visibleWrites.getCompleteNode(path);
  }

  /**
   * This method is used when processing child remove events on a query. If we can, we pull in
   * children that were outside the window, but may now be in the window.
   */
  public NamedNode calcNextNodeAfterPost(
      Path treePath, Node completeServerData, NamedNode post, boolean reverse, Index index) {
    Node toIterate;
    CompoundWrite merge = this.visibleWrites.childCompoundWrite(treePath);
    Node shadowingNode = merge.getCompleteNode(Path.getEmptyPath());
    if (shadowingNode != null) {
      toIterate = shadowingNode;
    } else if (completeServerData != null) {
      toIterate = merge.apply(completeServerData);
    } else {
      // no children to iterate on
      return null;
    }
    NamedNode currentNext = null;
    for (NamedNode node : toIterate) {
      if (index.compare(node, post, reverse) > 0
          && (currentNext == null || index.compare(node, currentNext, reverse) < 0)) {
        currentNext = node;
      }
    }
    return currentNext;
  }

  private boolean recordContainsPath(UserWriteRecord writeRecord, Path path) {
    if (writeRecord.isOverwrite()) {
      return writeRecord.getPath().contains(path);
    } else {
      for (Map.Entry<Path, Node> entry : writeRecord.getMerge()) {
        if (writeRecord.getPath().child(entry.getKey()).contains(path)) {
          return true;
        }
      }
      return false;
    }
  }

  /** Re-layer the writes and merges into a tree so we can efficiently calculate event snapshots */
  private void resetTree() {
    this.visibleWrites =
        WriteTree.layerTree(this.allWrites, WriteTree.DEFAULT_FILTER, Path.getEmptyPath());
    if (this.allWrites.size() > 0) {
      this.lastWriteId = this.allWrites.get(this.allWrites.size() - 1).getWriteId();
    } else {
      this.lastWriteId = -1L;
    }
  }

  /** The default filter used when constructing the tree. Keep everything that's visible. */
  private static final Predicate<UserWriteRecord> DEFAULT_FILTER =
      new Predicate<UserWriteRecord>() {
        @Override
        public boolean evaluate(UserWriteRecord write) {
          return write.isVisible();
        }
      };

  /**
   * Static method. Given an array of WriteRecords, a filter for which ones to include, and a path,
   * construct a merge at that path.
   */
  private static CompoundWrite layerTree(
      List<UserWriteRecord> writes, Predicate<UserWriteRecord> filter, Path treeRoot) {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    for (UserWriteRecord write : writes) {
      // Theory, a later set will either:
      // a) abort a relevant transaction, so no need to worry about excluding it from calculating
      //    that transaction
      // b) not be relevant to a transaction (separate branch), so again will not affect the data
      //     for that transaction
      if (filter.evaluate(write)) {
        Path writePath = write.getPath();
        if (write.isOverwrite()) {
          if (treeRoot.contains(writePath)) {
            Path relativePath = Path.getRelative(treeRoot, writePath);
            compoundWrite = compoundWrite.addWrite(relativePath, write.getOverwrite());
          } else if (writePath.contains(treeRoot)) {
            compoundWrite =
                compoundWrite.addWrite(
                    Path.getEmptyPath(),
                    write.getOverwrite().getChild(Path.getRelative(writePath, treeRoot)));
          } else {
            // There is no overlap between root path and write path, ignore write
          }
        } else {
          if (treeRoot.contains(writePath)) {
            Path relativePath = Path.getRelative(treeRoot, writePath);
            compoundWrite = compoundWrite.addWrites(relativePath, write.getMerge());
          } else if (writePath.contains(treeRoot)) {
            Path relativePath = Path.getRelative(writePath, treeRoot);
            if (relativePath.isEmpty()) {
              compoundWrite = compoundWrite.addWrites(Path.getEmptyPath(), write.getMerge());
            } else {
              Node deepNode = write.getMerge().getCompleteNode(relativePath);
              if (deepNode != null) {
                compoundWrite = compoundWrite.addWrite(Path.getEmptyPath(), deepNode);
              }
            }
          } else {
            // There is no overlap between root path and write path, ignore write
          }
        }
      }
    }
    return compoundWrite;
  }
}
