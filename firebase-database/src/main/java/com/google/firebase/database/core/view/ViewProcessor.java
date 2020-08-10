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

package com.google.firebase.database.core.view;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.google.firebase.database.core.CompoundWrite;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.WriteTreeRef;
import com.google.firebase.database.core.operation.AckUserWrite;
import com.google.firebase.database.core.operation.Merge;
import com.google.firebase.database.core.operation.Operation;
import com.google.firebase.database.core.operation.Overwrite;
import com.google.firebase.database.core.utilities.ImmutableTree;
import com.google.firebase.database.core.view.filter.ChildChangeAccumulator;
import com.google.firebase.database.core.view.filter.NodeFilter;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.ChildrenNode;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Index;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.KeyIndex;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ViewProcessor {

  private final NodeFilter filter;

  public ViewProcessor(NodeFilter filter) {
    this.filter = filter;
  }

  /** */
  public static class ProcessorResult {
    public final ViewCache viewCache;
    public final List<Change> changes;

    public ProcessorResult(ViewCache viewCache, List<Change> changes) {
      this.viewCache = viewCache;
      this.changes = changes;
    }
  }

  public ProcessorResult applyOperation(
      ViewCache oldViewCache,
      Operation operation,
      WriteTreeRef writesCache,
      Node optCompleteCache) {
    ChildChangeAccumulator accumulator = new ChildChangeAccumulator();
    ViewCache newViewCache;
    switch (operation.getType()) {
      case Overwrite:
        {
          Overwrite overwrite = (Overwrite) operation;
          if (overwrite.getSource().isFromUser()) {
            newViewCache =
                this.applyUserOverwrite(
                    oldViewCache,
                    overwrite.getPath(),
                    overwrite.getSnapshot(),
                    writesCache,
                    optCompleteCache,
                    accumulator);
          } else {
            hardAssert(overwrite.getSource().isFromServer());
            // We filter the node if it's a tagged update or the node has been previously filtered
            // and the update is not at the root in which case it is ok (and necessary) to mark the
            // node unfiltered again
            boolean filterServerNode =
                overwrite.getSource().isTagged()
                    || (oldViewCache.getServerCache().isFiltered()
                        && !overwrite.getPath().isEmpty());
            newViewCache =
                this.applyServerOverwrite(
                    oldViewCache,
                    overwrite.getPath(),
                    overwrite.getSnapshot(),
                    writesCache,
                    optCompleteCache,
                    filterServerNode,
                    accumulator);
          }
          break;
        }
      case Merge:
        {
          Merge merge = (Merge) operation;
          if (merge.getSource().isFromUser()) {
            newViewCache =
                this.applyUserMerge(
                    oldViewCache,
                    merge.getPath(),
                    merge.getChildren(),
                    writesCache,
                    optCompleteCache,
                    accumulator);
          } else {
            hardAssert(merge.getSource().isFromServer());
            // We filter the node if it's a tagged update or the node has been previously filtered
            boolean filterServerNode =
                merge.getSource().isTagged() || oldViewCache.getServerCache().isFiltered();
            newViewCache =
                this.applyServerMerge(
                    oldViewCache,
                    merge.getPath(),
                    merge.getChildren(),
                    writesCache,
                    optCompleteCache,
                    filterServerNode,
                    accumulator);
          }
          break;
        }
      case AckUserWrite:
        {
          AckUserWrite ackUserWrite = (AckUserWrite) operation;
          if (!ackUserWrite.isRevert()) {
            newViewCache =
                this.ackUserWrite(
                    oldViewCache,
                    ackUserWrite.getPath(),
                    ackUserWrite.getAffectedTree(),
                    writesCache,
                    optCompleteCache,
                    accumulator);
          } else {
            newViewCache =
                this.revertUserWrite(
                    oldViewCache,
                    ackUserWrite.getPath(),
                    writesCache,
                    optCompleteCache,
                    accumulator);
          }
          break;
        }
      case ListenComplete:
        {
          newViewCache =
              this.listenComplete(
                  oldViewCache, operation.getPath(), writesCache, optCompleteCache, accumulator);
          break;
        }
      default:
        {
          throw new AssertionError("Unknown operation: " + operation.getType());
        }
    }
    List<Change> changes = new ArrayList<Change>(accumulator.getChanges());
    maybeAddValueEvent(oldViewCache, newViewCache, changes);
    return new ProcessorResult(newViewCache, changes);
  }

  private void maybeAddValueEvent(
      ViewCache oldViewCache, ViewCache newViewCache, List<Change> accumulator) {
    CacheNode eventSnap = newViewCache.getEventCache();
    if (eventSnap.isFullyInitialized()) {
      boolean isLeafOrEmpty = eventSnap.getNode().isLeafNode() || eventSnap.getNode().isEmpty();
      if (!accumulator.isEmpty()
          || !oldViewCache.getEventCache().isFullyInitialized()
          || (isLeafOrEmpty && !eventSnap.getNode().equals(oldViewCache.getCompleteEventSnap()))
          || !eventSnap
              .getNode()
              .getPriority()
              .equals(oldViewCache.getCompleteEventSnap().getPriority())) {
        accumulator.add(Change.valueChange(eventSnap.getIndexedNode()));
      }
    }
  }

  private ViewCache generateEventCacheAfterServerEvent(
      ViewCache viewCache,
      Path changePath,
      WriteTreeRef writesCache,
      NodeFilter.CompleteChildSource source,
      ChildChangeAccumulator accumulator) {
    CacheNode oldEventSnap = viewCache.getEventCache();
    if (writesCache.shadowingWrite(changePath) != null) {
      // we have a shadowing write, ignore changes
      return viewCache;
    } else {
      IndexedNode newEventCache;
      if (changePath.isEmpty()) {
        // TODO: figure out how this plays with "sliding ack windows"
        hardAssert(
            viewCache.getServerCache().isFullyInitialized(),
            "If change path is empty, we must have complete server data");
        Node nodeWithLocalWrites;
        if (viewCache.getServerCache().isFiltered()) {
          // We need to special case this, because we need to only apply writes to complete
          // children, or we might end up raising events for incomplete children. If the server data
          // is filtered deep writes cannot be guaranteed to be complete
          Node serverCache = viewCache.getCompleteServerSnap();
          Node completeChildren =
              (serverCache instanceof ChildrenNode) ? serverCache : EmptyNode.Empty();
          nodeWithLocalWrites = writesCache.calcCompleteEventChildren(completeChildren);
        } else {
          nodeWithLocalWrites =
              writesCache.calcCompleteEventCache(viewCache.getCompleteServerSnap());
        }
        IndexedNode indexedNode = IndexedNode.from(nodeWithLocalWrites, this.filter.getIndex());
        newEventCache =
            this.filter.updateFullNode(
                viewCache.getEventCache().getIndexedNode(), indexedNode, accumulator);
      } else {
        ChildKey childKey = changePath.getFront();
        if (childKey.isPriorityChildName()) {
          hardAssert(
              changePath.size() == 1, "Can't have a priority with additional path components");
          Node oldEventNode = oldEventSnap.getNode();
          Node serverNode = viewCache.getServerCache().getNode();
          // we might have overwrites for this priority
          Node updatedPriority =
              writesCache.calcEventCacheAfterServerOverwrite(changePath, oldEventNode, serverNode);
          if (updatedPriority != null) {
            newEventCache =
                this.filter.updatePriority(oldEventSnap.getIndexedNode(), updatedPriority);
          } else {
            // priority didn't change, keep old node
            newEventCache = oldEventSnap.getIndexedNode();
          }
        } else {
          Path childChangePath = changePath.popFront();
          // update child
          Node newEventChild;
          if (oldEventSnap.isCompleteForChild(childKey)) {
            Node serverNode = viewCache.getServerCache().getNode();
            Node eventChildUpdate =
                writesCache.calcEventCacheAfterServerOverwrite(
                    changePath, oldEventSnap.getNode(), serverNode);
            if (eventChildUpdate != null) {
              newEventChild =
                  oldEventSnap
                      .getNode()
                      .getImmediateChild(childKey)
                      .updateChild(childChangePath, eventChildUpdate);
            } else {
              // Nothing changed, just keep the old child
              newEventChild = oldEventSnap.getNode().getImmediateChild(childKey);
            }
          } else {
            newEventChild = writesCache.calcCompleteChild(childKey, viewCache.getServerCache());
          }
          if (newEventChild != null) {
            newEventCache =
                this.filter.updateChild(
                    oldEventSnap.getIndexedNode(),
                    childKey,
                    newEventChild,
                    childChangePath,
                    source,
                    accumulator);
          } else {
            // no complete child available or no change
            newEventCache = oldEventSnap.getIndexedNode();
          }
        }
      }
      return viewCache.updateEventSnap(
          newEventCache,
          oldEventSnap.isFullyInitialized() || changePath.isEmpty(),
          this.filter.filtersNodes());
    }
  }

  private ViewCache applyServerOverwrite(
      ViewCache oldViewCache,
      Path changePath,
      Node changedSnap,
      WriteTreeRef writesCache,
      Node optCompleteCache,
      boolean filterServerNode,
      ChildChangeAccumulator accumulator) {
    CacheNode oldServerSnap = oldViewCache.getServerCache();
    IndexedNode newServerCache;
    NodeFilter serverFilter = filterServerNode ? this.filter : this.filter.getIndexedFilter();
    if (changePath.isEmpty()) {
      newServerCache =
          serverFilter.updateFullNode(
              oldServerSnap.getIndexedNode(),
              IndexedNode.from(changedSnap, serverFilter.getIndex()),
              null);
    } else if (serverFilter.filtersNodes() && !oldServerSnap.isFiltered()) {
      // we want to filter the server node, but we didn't filter the server node yet, so simulate a
      // full update
      hardAssert(
          !changePath.isEmpty(), "An empty path should have been caught in the other branch");
      ChildKey childKey = changePath.getFront();
      Path updatePath = changePath.popFront();
      Node newChild =
          oldServerSnap.getNode().getImmediateChild(childKey).updateChild(updatePath, changedSnap);
      IndexedNode newServerNode = oldServerSnap.getIndexedNode().updateChild(childKey, newChild);
      newServerCache =
          serverFilter.updateFullNode(oldServerSnap.getIndexedNode(), newServerNode, null);
    } else {
      ChildKey childKey = changePath.getFront();
      if (!oldServerSnap.isCompleteForPath(changePath) && changePath.size() > 1) {
        // We don't update incomplete nodes with updates intended for other listeners
        return oldViewCache;
      }
      Path childChangePath = changePath.popFront();
      Node childNode = oldServerSnap.getNode().getImmediateChild(childKey);
      Node newChildNode = childNode.updateChild(childChangePath, changedSnap);
      if (childKey.isPriorityChildName()) {
        newServerCache = serverFilter.updatePriority(oldServerSnap.getIndexedNode(), newChildNode);
      } else {
        newServerCache =
            serverFilter.updateChild(
                oldServerSnap.getIndexedNode(),
                childKey,
                newChildNode,
                childChangePath,
                NO_COMPLETE_SOURCE,
                null);
      }
    }
    ViewCache newViewCache =
        oldViewCache.updateServerSnap(
            newServerCache,
            oldServerSnap.isFullyInitialized() || changePath.isEmpty(),
            serverFilter.filtersNodes());
    NodeFilter.CompleteChildSource source =
        new WriteTreeCompleteChildSource(writesCache, newViewCache, optCompleteCache);
    return generateEventCacheAfterServerEvent(
        newViewCache, changePath, writesCache, source, accumulator);
  }

  private ViewCache applyUserOverwrite(
      ViewCache oldViewCache,
      Path changePath,
      Node changedSnap,
      WriteTreeRef writesCache,
      Node optCompleteCache,
      ChildChangeAccumulator accumulator) {
    CacheNode oldEventSnap = oldViewCache.getEventCache();
    ViewCache newViewCache;
    NodeFilter.CompleteChildSource source =
        new WriteTreeCompleteChildSource(writesCache, oldViewCache, optCompleteCache);
    if (changePath.isEmpty()) {
      IndexedNode newIndexed = IndexedNode.from(changedSnap, this.filter.getIndex());
      IndexedNode newEventCache =
          this.filter.updateFullNode(
              oldViewCache.getEventCache().getIndexedNode(), newIndexed, accumulator);
      newViewCache = oldViewCache.updateEventSnap(newEventCache, true, this.filter.filtersNodes());
    } else {
      ChildKey childKey = changePath.getFront();
      if (childKey.isPriorityChildName()) {
        IndexedNode newEventCache =
            this.filter.updatePriority(oldViewCache.getEventCache().getIndexedNode(), changedSnap);
        newViewCache =
            oldViewCache.updateEventSnap(
                newEventCache, oldEventSnap.isFullyInitialized(), oldEventSnap.isFiltered());
      } else {
        Path childChangePath = changePath.popFront();
        Node oldChild = oldEventSnap.getNode().getImmediateChild(childKey);
        Node newChild;
        if (childChangePath.isEmpty()) {
          // Child overwrite, we can replace the child
          newChild = changedSnap;
        } else {
          Node childNode = source.getCompleteChild(childKey);
          if (childNode != null) {
            if (childChangePath.getBack().isPriorityChildName()
                && childNode.getChild(childChangePath.getParent()).isEmpty()) {
              // This is a priority update on an empty node. If this node exists on the server, the
              // server will send down the priority in the update, so ignore for now
              newChild = childNode;
            } else {
              newChild = childNode.updateChild(childChangePath, changedSnap);
            }
          } else {
            // There is no complete child node available
            newChild = EmptyNode.Empty();
          }
        }
        if (!oldChild.equals(newChild)) {
          IndexedNode newEventSnap =
              this.filter.updateChild(
                  oldEventSnap.getIndexedNode(),
                  childKey,
                  newChild,
                  childChangePath,
                  source,
                  accumulator);
          newViewCache =
              oldViewCache.updateEventSnap(
                  newEventSnap, oldEventSnap.isFullyInitialized(), this.filter.filtersNodes());
        } else {
          newViewCache = oldViewCache;
        }
      }
    }
    return newViewCache;
  }

  private static boolean cacheHasChild(ViewCache viewCache, ChildKey childKey) {
    return viewCache.getEventCache().isCompleteForChild(childKey);
  }

  private ViewCache applyUserMerge(
      final ViewCache viewCache,
      final Path path,
      CompoundWrite changedChildren,
      final WriteTreeRef writesCache,
      final Node serverCache,
      final ChildChangeAccumulator accumulator) {
    // HACK: In the case of a limit query, there may be some changes that bump things out of the
    // window leaving room for new items.  It's important we process these changes first, so we
    // iterate the changes twice, first processing any that affect items currently in view.
    // TODO: I consider an item "in view" if cacheHasChild is true, which checks both the server
    // and event snap.  I'm not sure if this will result in edge cases when a child is in one but
    // not the other.
    hardAssert(changedChildren.rootWrite() == null, "Can't have a merge that is an overwrite");
    ViewCache currentViewCache = viewCache;
    for (Map.Entry<Path, Node> entry : changedChildren) {
      Path writePath = path.child(entry.getKey());
      if (ViewProcessor.cacheHasChild(viewCache, writePath.getFront())) {
        currentViewCache =
            applyUserOverwrite(
                currentViewCache,
                writePath,
                entry.getValue(),
                writesCache,
                serverCache,
                accumulator);
      }
    }

    for (Map.Entry<Path, Node> entry : changedChildren) {
      Path writePath = path.child(entry.getKey());
      if (!ViewProcessor.cacheHasChild(viewCache, writePath.getFront())) {
        currentViewCache =
            applyUserOverwrite(
                currentViewCache,
                writePath,
                entry.getValue(),
                writesCache,
                serverCache,
                accumulator);
      }
    }
    return currentViewCache;
  }

  private ViewCache applyServerMerge(
      final ViewCache viewCache,
      final Path path,
      CompoundWrite changedChildren,
      final WriteTreeRef writesCache,
      final Node serverCache,
      final boolean filterServerNode,
      final ChildChangeAccumulator accumulator) {
    // If we don't have a cache yet, this merge was intended for a previously listen in the same
    // location. Ignore it and wait for the complete data update coming soon.
    if (viewCache.getServerCache().getNode().isEmpty()
        && !viewCache.getServerCache().isFullyInitialized()) {
      return viewCache;
    }

    // HACK: In the case of a limit query, there may be some changes that bump things out of the
    // window leaving room for new items.  It's important we process these changes first, so we
    // iterate the changes twice, first processing any that affect items currently in view.
    // TODO: I consider an item "in view" if cacheHasChild is true, which checks both the server
    // and event snap.  I'm not sure if this will result in edge cases when a child is in one but
    // not the other.
    ViewCache curViewCache = viewCache;
    hardAssert(changedChildren.rootWrite() == null, "Can't have a merge that is an overwrite");
    CompoundWrite actualMerge;
    if (path.isEmpty()) {
      actualMerge = changedChildren;
    } else {
      actualMerge = CompoundWrite.emptyWrite().addWrites(path, changedChildren);
    }
    Node serverNode = viewCache.getServerCache().getNode();
    Map<ChildKey, CompoundWrite> childCompoundWrites = actualMerge.childCompoundWrites();
    for (Map.Entry<ChildKey, CompoundWrite> childMerge : childCompoundWrites.entrySet()) {
      ChildKey childKey = childMerge.getKey();
      if (serverNode.hasChild(childKey)) {
        Node serverChild = serverNode.getImmediateChild(childKey);
        Node newChild = childMerge.getValue().apply(serverChild);
        curViewCache =
            applyServerOverwrite(
                curViewCache,
                new Path(childKey),
                newChild,
                writesCache,
                serverCache,
                filterServerNode,
                accumulator);
      }
    }
    for (Map.Entry<ChildKey, CompoundWrite> childMerge : childCompoundWrites.entrySet()) {
      ChildKey childKey = childMerge.getKey();
      CompoundWrite childCompoundWrite = childMerge.getValue();
      boolean isUnknownDeepMerge =
          !viewCache.getServerCache().isCompleteForChild(childKey)
              && childCompoundWrite.rootWrite() == null;
      if (!serverNode.hasChild(childKey) && !isUnknownDeepMerge) {
        Node serverChild = serverNode.getImmediateChild(childKey);
        Node newChild = childMerge.getValue().apply(serverChild);
        curViewCache =
            applyServerOverwrite(
                curViewCache,
                new Path(childKey),
                newChild,
                writesCache,
                serverCache,
                filterServerNode,
                accumulator);
      }
    }

    return curViewCache;
  }

  private ViewCache ackUserWrite(
      ViewCache viewCache,
      Path ackPath,
      ImmutableTree<Boolean> affectedTree,
      WriteTreeRef writesCache,
      Node optCompleteCache,
      ChildChangeAccumulator accumulator) {
    if (writesCache.shadowingWrite(ackPath) != null) {
      return viewCache;
    }

    // Only filter server node if it is currently filtered
    boolean filterServerNode = viewCache.getServerCache().isFiltered();

    // Essentially we'll just get our existing server cache for the affected paths and re-apply it
    // as a server update now that it won't be shadowed.
    CacheNode serverCache = viewCache.getServerCache();
    if (affectedTree.getValue() != null) {
      // This is an overwrite.
      if ((ackPath.isEmpty() && serverCache.isFullyInitialized())
          || serverCache.isCompleteForPath(ackPath)) {
        return applyServerOverwrite(
            viewCache,
            ackPath,
            serverCache.getNode().getChild(ackPath),
            writesCache,
            optCompleteCache,
            filterServerNode,
            accumulator);
      } else if (ackPath.isEmpty()) {
        // This is a goofy edge case where we are acking data at this location but don't have full
        // data. We should just re-apply whatever we have in our cache as a merge.
        CompoundWrite changedChildren = CompoundWrite.emptyWrite();
        for (NamedNode child : serverCache.getNode()) {
          changedChildren = changedChildren.addWrite(child.getName(), child.getNode());
        }
        return applyServerMerge(
            viewCache,
            ackPath,
            changedChildren,
            writesCache,
            optCompleteCache,
            filterServerNode,
            accumulator);
      } else {
        return viewCache;
      }
    } else {
      // This is a merge.
      CompoundWrite changedChildren = CompoundWrite.emptyWrite();
      for (Map.Entry<Path, Boolean> entry : affectedTree) {
        Path mergePath = entry.getKey();
        Path serverCachePath = ackPath.child(mergePath);
        if (serverCache.isCompleteForPath(serverCachePath)) {
          changedChildren =
              changedChildren.addWrite(mergePath, serverCache.getNode().getChild(serverCachePath));
        }
      }
      return applyServerMerge(
          viewCache,
          ackPath,
          changedChildren,
          writesCache,
          optCompleteCache,
          filterServerNode,
          accumulator);
    }
  }

  public ViewCache revertUserWrite(
      ViewCache viewCache,
      Path path,
      WriteTreeRef writesCache,
      Node optCompleteServerCache,
      ChildChangeAccumulator accumulator) {
    if (writesCache.shadowingWrite(path) != null) {
      return viewCache;
    } else {
      NodeFilter.CompleteChildSource source =
          new WriteTreeCompleteChildSource(writesCache, viewCache, optCompleteServerCache);
      IndexedNode oldEventCache = viewCache.getEventCache().getIndexedNode();
      IndexedNode newEventCache;
      if (path.isEmpty() || path.getFront().isPriorityChildName()) {
        Node newNode;
        if (viewCache.getServerCache().isFullyInitialized()) {
          newNode = writesCache.calcCompleteEventCache(viewCache.getCompleteServerSnap());
        } else {
          newNode = writesCache.calcCompleteEventChildren(viewCache.getServerCache().getNode());
        }
        IndexedNode indexedNode = IndexedNode.from(newNode, this.filter.getIndex());
        newEventCache = this.filter.updateFullNode(oldEventCache, indexedNode, accumulator);
      } else {
        ChildKey childKey = path.getFront();
        Node newChild = writesCache.calcCompleteChild(childKey, viewCache.getServerCache());
        if (newChild == null && viewCache.getServerCache().isCompleteForChild(childKey)) {
          newChild = oldEventCache.getNode().getImmediateChild(childKey);
        }
        if (newChild != null) {
          newEventCache =
              this.filter.updateChild(
                  oldEventCache, childKey, newChild, path.popFront(), source, accumulator);
        } else if (newChild == null && viewCache.getEventCache().getNode().hasChild(childKey)) {
          // No complete child available, delete the existing one, if any
          newEventCache =
              this.filter.updateChild(
                  oldEventCache, childKey, EmptyNode.Empty(), path.popFront(), source, accumulator);
        } else {
          newEventCache = oldEventCache;
        }
        if (newEventCache.getNode().isEmpty() && viewCache.getServerCache().isFullyInitialized()) {
          // We might have reverted all child writes. Maybe the old event was a leaf node
          Node complete = writesCache.calcCompleteEventCache(viewCache.getCompleteServerSnap());
          if (complete.isLeafNode()) {
            IndexedNode indexedNode = IndexedNode.from(complete, this.filter.getIndex());
            newEventCache = this.filter.updateFullNode(newEventCache, indexedNode, accumulator);
          }
        }
      }
      boolean complete =
          viewCache.getServerCache().isFullyInitialized()
              || writesCache.shadowingWrite(Path.getEmptyPath()) != null;
      return viewCache.updateEventSnap(newEventCache, complete, this.filter.filtersNodes());
    }
  }

  private ViewCache listenComplete(
      ViewCache viewCache,
      Path path,
      WriteTreeRef writesCache,
      Node serverCache,
      ChildChangeAccumulator accumulator) {
    CacheNode oldServerNode = viewCache.getServerCache();
    ViewCache newViewCache =
        viewCache.updateServerSnap(
            oldServerNode.getIndexedNode(),
            oldServerNode.isFullyInitialized() || path.isEmpty(),
            oldServerNode.isFiltered());
    return generateEventCacheAfterServerEvent(
        newViewCache, path, writesCache, NO_COMPLETE_SOURCE, accumulator);
  }

  /** An implementation of CompleteChildSource that never returns any additional children */
  private static NodeFilter.CompleteChildSource NO_COMPLETE_SOURCE =
      new NodeFilter.CompleteChildSource() {
        @Override
        public Node getCompleteChild(ChildKey childKey) {
          return null;
        }

        @Override
        public NamedNode getChildAfterChild(Index index, NamedNode child, boolean reverse) {
          return null;
        }
      };

  /**
   * An implementation of CompleteChildSource that uses a WriteTree in addition to any other server
   * data or old event caches available to calculate complete children.
   */
  private static class WriteTreeCompleteChildSource implements NodeFilter.CompleteChildSource {
    private final WriteTreeRef writes;
    private final ViewCache viewCache;
    private final Node optCompleteServerCache;

    public WriteTreeCompleteChildSource(
        WriteTreeRef writes, ViewCache viewCache, Node optCompleteServerCache) {
      this.writes = writes;
      this.viewCache = viewCache;
      this.optCompleteServerCache = optCompleteServerCache;
    }

    @Override
    public Node getCompleteChild(ChildKey childKey) {
      CacheNode node = viewCache.getEventCache();
      if (node.isCompleteForChild(childKey)) {
        return node.getNode().getImmediateChild(childKey);
      } else {
        CacheNode serverNode;
        if (this.optCompleteServerCache != null) {
          // Since we're only ever getting child nodes, we can use the key index here
          serverNode =
              new CacheNode(
                  IndexedNode.from(this.optCompleteServerCache, KeyIndex.getInstance()),
                  true,
                  false);
        } else {
          serverNode = viewCache.getServerCache();
        }
        return this.writes.calcCompleteChild(childKey, serverNode);
      }
    }

    @Override
    public NamedNode getChildAfterChild(Index index, NamedNode child, boolean reverse) {
      Node completeServerData =
          optCompleteServerCache != null
              ? optCompleteServerCache
              : viewCache.getCompleteServerSnap();
      return writes.calcNextNodeAfterPost(completeServerData, child, reverse, index);
    }
  }
}
