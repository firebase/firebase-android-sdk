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

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.annotations.NotNull;
import com.google.firebase.database.annotations.Nullable;
import com.google.firebase.database.collection.LLRBNode;
import com.google.firebase.database.connection.ListenHashProvider;
import com.google.firebase.database.core.operation.AckUserWrite;
import com.google.firebase.database.core.operation.ListenComplete;
import com.google.firebase.database.core.operation.Merge;
import com.google.firebase.database.core.operation.Operation;
import com.google.firebase.database.core.operation.OperationSource;
import com.google.firebase.database.core.operation.Overwrite;
import com.google.firebase.database.core.persistence.PersistenceManager;
import com.google.firebase.database.core.utilities.Clock;
import com.google.firebase.database.core.utilities.ImmutableTree;
import com.google.firebase.database.core.utilities.NodeSizeEstimator;
import com.google.firebase.database.core.utilities.Pair;
import com.google.firebase.database.core.view.CacheNode;
import com.google.firebase.database.core.view.Change;
import com.google.firebase.database.core.view.DataEvent;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.core.view.View;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.CompoundHash;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.RangeMerge;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * SyncTree is the central class for managing event callback registration, data caching, views
 * (query processing), and event generation. There are typically two SyncTree instances for each
 * Repo, one for the normal Firebase data, and one for the .info data.
 *
 * <p>It has a number of responsibilities, including: - Tracking all user event callbacks
 * (registered via addEventRegistration() and removeEventRegistration()). - Applying and caching
 * data changes for user set(), transaction(), and update() calls (applyUserOverwrite(),
 * applyUserMerge()). - Applying and caching data changes for server data changes
 * (applyServerOverwrite(), applyServerMerge()). - Generating user-facing events for server and user
 * changes (all of the apply* methods return the set of events that need to be raised as a result).
 * - Maintaining the appropriate set of server listens to ensure we are always subscribed to the
 * correct set of paths and queries to satisfy the current set of user event callbacks (listens are
 * started/stopped using the provided listenProvider).
 *
 * <p>NOTE: Although SyncTree tracks event callbacks and calculates events to raise, the actual
 * events are returned to the caller rather than raised synchronously.
 */
public class SyncTree {

  // Size after which we start including the compound hash
  private static final long SIZE_THRESHOLD_FOR_COMPOUND_HASH = 1024;

  /** */
  public interface CompletionListener {
    public List<? extends Event> onListenComplete(DatabaseError error);
  }

  /** */
  public interface ListenProvider {
    public void startListening(
        QuerySpec query,
        Tag tag,
        final ListenHashProvider hash,
        final CompletionListener onListenComplete);

    public void stopListening(QuerySpec query, Tag tag);
  }

  private class ListenContainer implements ListenHashProvider, CompletionListener {
    private final View view;
    private final Tag tag;

    public ListenContainer(View view) {
      this.view = view;
      this.tag = SyncTree.this.tagForQuery(view.getQuery());
    }

    @Override
    public com.google.firebase.database.connection.CompoundHash getCompoundHash() {
      CompoundHash hash = CompoundHash.fromNode(view.getServerCache());
      List<Path> pathPosts = hash.getPosts();
      List<List<String>> posts = new ArrayList<List<String>>(pathPosts.size());
      for (Path path : pathPosts) {
        posts.add(path.asList());
      }
      return new com.google.firebase.database.connection.CompoundHash(posts, hash.getHashes());
    }

    @Override
    public String getSimpleHash() {
      return view.getServerCache().getHash();
    }

    @Override
    public boolean shouldIncludeCompoundHash() {
      return NodeSizeEstimator.estimateSerializedNodeSize(view.getServerCache())
          > SIZE_THRESHOLD_FOR_COMPOUND_HASH;
    }

    @Override
    public List<? extends Event> onListenComplete(DatabaseError error) {
      if (error == null) {
        QuerySpec query = this.view.getQuery();
        if (tag != null) {
          return SyncTree.this.applyTaggedListenComplete(tag);
        } else {
          return SyncTree.this.applyListenComplete(query.getPath());
        }
      } else {
        logger.warn("Listen at " + view.getQuery().getPath() + " failed: " + error.toString());

        // If a listen failed, kill all of the listeners here, not just the one that triggered the
        // error. Note that this may need to be scoped to just this listener if we change
        // permissions on filtered children
        return SyncTree.this.removeAllEventRegistrations(view.getQuery(), error);
      }
    }
  }

  /** Tree of SyncPoints. There's a SyncPoint at any location that has 1 or more views. */
  private ImmutableTree<SyncPoint> syncPointTree;

  /**
   * A tree of all pending user writes (user-initiated set()'s, transaction()'s, update()'s, etc.).
   */
  private final WriteTree pendingWriteTree;

  private final Map<Tag, QuerySpec> tagToQueryMap;
  private final Map<QuerySpec, Tag> queryToTagMap;
  private final Set<QuerySpec> keepSyncedQueries;
  private final ListenProvider listenProvider;
  private final PersistenceManager persistenceManager;
  private final LogWrapper logger;

  public SyncTree(
      Context context, PersistenceManager persistenceManager, ListenProvider listenProvider) {
    this.syncPointTree = ImmutableTree.emptyInstance();
    this.pendingWriteTree = new WriteTree();
    this.tagToQueryMap = new HashMap<Tag, QuerySpec>();
    this.queryToTagMap = new HashMap<QuerySpec, Tag>();
    this.keepSyncedQueries = new HashSet<QuerySpec>();
    this.listenProvider = listenProvider;
    this.persistenceManager = persistenceManager;
    this.logger = context.getLogger("SyncTree");
  }

  public boolean isEmpty() {
    return this.syncPointTree.isEmpty();
  }

  /** Apply the data changes for a user-generated set() or transaction() call. */
  public List<? extends Event> applyUserOverwrite(
      final Path path,
      final Node newDataUnresolved,
      final Node newData,
      final long writeId,
      final boolean visible,
      final boolean persist) {
    hardAssert(visible || !persist, "We shouldn't be persisting non-visible writes.");
    return persistenceManager.runInTransaction(
        new Callable<List<? extends Event>>() {
          @Override
          public List<? extends Event> call() {
            if (persist) {
              persistenceManager.saveUserOverwrite(path, newDataUnresolved, writeId);
            }

            pendingWriteTree.addOverwrite(path, newData, writeId, visible);
            if (!visible) {
              return Collections.emptyList();
            } else {
              return applyOperationToSyncPoints(new Overwrite(OperationSource.USER, path, newData));
            }
          }
        });
  }

  /** Apply the data from a user-generated update() call. */
  public List<? extends Event> applyUserMerge(
      final Path path,
      final CompoundWrite unresolvedChildren,
      final CompoundWrite children,
      final long writeId,
      final boolean persist) {
    return this.persistenceManager.runInTransaction(
        new Callable<List<? extends Event>>() {
          @Override
          public List<? extends Event> call() throws Exception {
            if (persist) {
              persistenceManager.saveUserMerge(path, unresolvedChildren, writeId);
            }
            pendingWriteTree.addMerge(path, children, writeId);

            return applyOperationToSyncPoints(new Merge(OperationSource.USER, path, children));
          }
        });
  }

  /**
   * Acknowledge a pending user write that was previously registered with applyUserOverwrite() or
   * applyUserMerge().
   */
  // TODO[persistence]: Taking a serverClock here is awkward, but server values are awkward. :-(
  public List<? extends Event> ackUserWrite(
      final long writeId, final boolean revert, final boolean persist, final Clock serverClock) {
    return this.persistenceManager.runInTransaction(
        new Callable<List<? extends Event>>() {
          @Override
          public List<? extends Event> call() {
            if (persist) {
              persistenceManager.removeUserWrite(writeId);
            }
            UserWriteRecord write = pendingWriteTree.getWrite(writeId);
            boolean needToReevaluate = pendingWriteTree.removeWrite(writeId);
            if (write.isVisible()) {
              if (!revert) {
                Map<String, Object> serverValues = ServerValues.generateServerValues(serverClock);
                if (write.isOverwrite()) {
                  Node resolvedNode =
                      ServerValues.resolveDeferredValueSnapshot(
                          write.getOverwrite(), SyncTree.this, write.getPath(), serverValues);
                  persistenceManager.applyUserWriteToServerCache(write.getPath(), resolvedNode);
                } else {
                  CompoundWrite resolvedMerge =
                      ServerValues.resolveDeferredValueMerge(
                          write.getMerge(), SyncTree.this, write.getPath(), serverValues);
                  persistenceManager.applyUserWriteToServerCache(write.getPath(), resolvedMerge);
                }
              }
            }
            if (!needToReevaluate) {
              return Collections.emptyList();
            } else {
              ImmutableTree<Boolean> affectedTree = ImmutableTree.emptyInstance();
              if (write.isOverwrite()) {
                affectedTree = affectedTree.set(Path.getEmptyPath(), true);
              } else {
                for (Map.Entry<Path, Node> entry : write.getMerge()) {
                  affectedTree = affectedTree.set(entry.getKey(), true);
                }
              }
              return applyOperationToSyncPoints(
                  new AckUserWrite(write.getPath(), affectedTree, revert));
            }
          }
        });
  }

  /** Removes all local writes */
  public List<? extends Event> removeAllWrites() {
    return this.persistenceManager.runInTransaction(
        new Callable<List<? extends Event>>() {
          @Override
          public List<? extends Event> call() throws Exception {
            persistenceManager.removeAllUserWrites();
            List<UserWriteRecord> purgedWrites = pendingWriteTree.purgeAllWrites();
            if (purgedWrites.isEmpty()) {
              return Collections.emptyList();
            } else {
              ImmutableTree<Boolean> affectedTree = new ImmutableTree<Boolean>(true);
              return applyOperationToSyncPoints(
                  new AckUserWrite(Path.getEmptyPath(), affectedTree, /*revert=*/ true));
            }
          }
        });
  }

  /** Apply new server data for the specified path. */
  public List<? extends Event> applyServerOverwrite(final Path path, final Node newData) {
    return persistenceManager.runInTransaction(
        new Callable<List<? extends Event>>() {
          @Override
          public List<? extends Event> call() {
            persistenceManager.updateServerCache(QuerySpec.defaultQueryAtPath(path), newData);
            return applyOperationToSyncPoints(new Overwrite(OperationSource.SERVER, path, newData));
          }
        });
  }

  /** Apply new server data to be merged in at the specified path. */
  public List<? extends Event> applyServerMerge(
      final Path path, final Map<Path, Node> changedChildren) {
    return persistenceManager.runInTransaction(
        new Callable<List<? extends Event>>() {
          @Override
          public List<? extends Event> call() {
            CompoundWrite merge = CompoundWrite.fromPathMerge(changedChildren);
            persistenceManager.updateServerCache(path, merge);
            return applyOperationToSyncPoints(new Merge(OperationSource.SERVER, path, merge));
          }
        });
  }

  /** Apply a range merge */
  public List<? extends Event> applyServerRangeMerges(
      final Path path, List<RangeMerge> rangeMerges) {
    SyncPoint syncPoint = syncPointTree.get(path);
    if (syncPoint == null) {
      // Removed view, so it's safe to just ignore this update
      return Collections.emptyList();
    } else {
      // This could be for any "complete" (unfiltered) view, and if there is more than one complete
      // view, they should each have the same cache so it doesn't matter which one we use.
      View view = syncPoint.getCompleteView();
      if (view != null) {
        Node serverNode = view.getServerCache();
        for (RangeMerge merge : rangeMerges) {
          serverNode = merge.applyTo(serverNode);
        }
        return applyServerOverwrite(path, serverNode);
      } else {
        // There doesn't exist a view for this update, so it was removed and it's safe to just
        // ignore this range merge
        return Collections.emptyList();
      }
    }
  }

  public List<? extends Event> applyTaggedRangeMerges(
      Path path, List<RangeMerge> rangeMerges, Tag tag) {
    QuerySpec query = queryForTag(tag);
    if (query != null) {
      hardAssert(path.equals(query.getPath()));
      SyncPoint syncPoint = syncPointTree.get(query.getPath());
      hardAssert(syncPoint != null, "Missing sync point for query tag that we're tracking");
      View view = syncPoint.viewForQuery(query);
      hardAssert(view != null, "Missing view for query tag that we're tracking");
      Node serverNode = view.getServerCache();
      for (RangeMerge merge : rangeMerges) {
        serverNode = merge.applyTo(serverNode);
      }
      return this.applyTaggedQueryOverwrite(path, serverNode, tag);
    } else {
      // We've already removed the query. No big deal, ignore the update
      return Collections.emptyList();
    }
  }

  /** Apply a listen complete to a path */
  public List<? extends Event> applyListenComplete(final Path path) {
    return persistenceManager.runInTransaction(
        new Callable<List<? extends Event>>() {
          @Override
          public List<? extends Event> call() {
            persistenceManager.setQueryComplete(QuerySpec.defaultQueryAtPath(path));
            return applyOperationToSyncPoints(new ListenComplete(OperationSource.SERVER, path));
          }
        });
  }

  /** Apply a listen complete to a path */
  public List<? extends Event> applyTaggedListenComplete(final Tag tag) {
    return persistenceManager.runInTransaction(
        new Callable<List<? extends Event>>() {
          @Override
          public List<? extends Event> call() {
            QuerySpec query = queryForTag(tag);
            if (query != null) {
              persistenceManager.setQueryComplete(query);
              Operation op =
                  new ListenComplete(
                      OperationSource.forServerTaggedQuery(query.getParams()), Path.getEmptyPath());
              return applyTaggedOperation(query, op);
            } else {
              // We've already removed the query. No big deal, ignore the update
              return Collections.emptyList();
            }
          }
        });
  }

  private List<? extends Event> applyTaggedOperation(QuerySpec query, Operation operation) {
    Path queryPath = query.getPath();
    SyncPoint syncPoint = syncPointTree.get(queryPath);
    hardAssert(syncPoint != null, "Missing sync point for query tag that we're tracking");
    WriteTreeRef writesCache = pendingWriteTree.childWrites(queryPath);
    return syncPoint.applyOperation(operation, writesCache, /*serverCache*/ null);
  }

  /** Apply new server data for the specified tagged query. */
  public List<? extends Event> applyTaggedQueryOverwrite(
      final Path path, final Node snap, final Tag tag) {
    return persistenceManager.runInTransaction(
        new Callable<List<? extends Event>>() {
          @Override
          public List<? extends Event> call() {
            QuerySpec query = queryForTag(tag);
            if (query != null) {
              Path relativePath = Path.getRelative(query.getPath(), path);
              QuerySpec queryToOverwrite =
                  relativePath.isEmpty() ? query : QuerySpec.defaultQueryAtPath(path);
              persistenceManager.updateServerCache(queryToOverwrite, snap);
              Operation op =
                  new Overwrite(
                      OperationSource.forServerTaggedQuery(query.getParams()), relativePath, snap);
              return applyTaggedOperation(query, op);
            } else {
              // Query must have been removed already
              return Collections.emptyList();
            }
          }
        });
  }

  /** Apply server data to be merged in for the specified tagged query. */
  public List<? extends Event> applyTaggedQueryMerge(
      final Path path, final Map<Path, Node> changedChildren, final Tag tag) {
    return persistenceManager.runInTransaction(
        new Callable<List<? extends Event>>() {
          @Override
          public List<? extends Event> call() {
            QuerySpec query = queryForTag(tag);
            if (query != null) {
              Path relativePath = Path.getRelative(query.getPath(), path);
              CompoundWrite merge = CompoundWrite.fromPathMerge(changedChildren);
              persistenceManager.updateServerCache(path, merge);
              Operation op =
                  new Merge(
                      OperationSource.forServerTaggedQuery(query.getParams()), relativePath, merge);
              return applyTaggedOperation(query, op);
            } else {
              // We've already removed the query. No big deal, ignore the update
              return Collections.emptyList();
            }
          }
        });
  }

  public void setQueryActive(QuerySpec query) {
    persistenceManager.runInTransaction(
        new Callable<Void>() {
          @Override
          public Void call() {
            persistenceManager.setQueryActive(query);
            return null;
          }
        });
  }

  public void setQueryInactive(QuerySpec query) {
    persistenceManager.runInTransaction(
        new Callable<Void>() {
          @Override
          public Void call() {
            persistenceManager.setQueryInactive(query);
            return null;
          }
        });
  }

  /** Add an event callback for the specified query. */
  public List<? extends Event> addEventRegistration(
      @NotNull final EventRegistration eventRegistration) {
    return persistenceManager.runInTransaction(
        new Callable<List<? extends Event>>() {
          @Override
          public List<? extends Event> call() {
            final QuerySpec query = eventRegistration.getQuerySpec();
            Path path = query.getPath();

            Node serverCacheNode = null;
            boolean foundAncestorDefaultView = false;
            // Any covering writes will necessarily be at the root, so really all we need to find is
            // the server cache. Consider optimizing this once there's a better understanding of
            // what actual behavior will be.
            // for (Map.Entry<QuerySpec, View> entry: views.entrySet()) {
            {
              ImmutableTree<SyncPoint> tree = syncPointTree;
              Path currentPath = path;
              while (!tree.isEmpty()) {
                SyncPoint currentSyncPoint = tree.getValue();
                if (currentSyncPoint != null) {
                  serverCacheNode =
                      serverCacheNode != null
                          ? serverCacheNode
                          : currentSyncPoint.getCompleteServerCache(currentPath);
                  foundAncestorDefaultView =
                      foundAncestorDefaultView || currentSyncPoint.hasCompleteView();
                }
                ChildKey front =
                    currentPath.isEmpty() ? ChildKey.fromString("") : currentPath.getFront();
                tree = tree.getChild(front);
                currentPath = currentPath.popFront();
              }
            }

            SyncPoint syncPoint = syncPointTree.get(path);
            if (syncPoint == null) {
              syncPoint = new SyncPoint(persistenceManager);
              syncPointTree = syncPointTree.set(path, syncPoint);
            } else {
              foundAncestorDefaultView = foundAncestorDefaultView || syncPoint.hasCompleteView();
              serverCacheNode =
                  serverCacheNode != null
                      ? serverCacheNode
                      : syncPoint.getCompleteServerCache(Path.getEmptyPath());
            }

            persistenceManager.setQueryActive(query);

            CacheNode serverCache;
            if (serverCacheNode != null) {
              serverCache =
                  new CacheNode(IndexedNode.from(serverCacheNode, query.getIndex()), true, false);
            } else {
              // Hit persistence
              CacheNode persistentServerCache = persistenceManager.serverCache(query);
              if (persistentServerCache.isFullyInitialized()) {
                serverCache = persistentServerCache;
              } else {
                serverCacheNode = EmptyNode.Empty();
                ImmutableTree<SyncPoint> subtree = syncPointTree.subtree(path);
                for (Map.Entry<ChildKey, ImmutableTree<SyncPoint>> child : subtree.getChildren()) {
                  SyncPoint childSyncPoint = child.getValue().getValue();
                  if (childSyncPoint != null) {
                    Node completeCache = childSyncPoint.getCompleteServerCache(Path.getEmptyPath());
                    if (completeCache != null) {
                      serverCacheNode =
                          serverCacheNode.updateImmediateChild(child.getKey(), completeCache);
                    }
                  }
                }
                // Fill the node with any available children we have
                for (NamedNode child : persistentServerCache.getNode()) {
                  if (!serverCacheNode.hasChild(child.getName())) {
                    serverCacheNode =
                        serverCacheNode.updateImmediateChild(child.getName(), child.getNode());
                  }
                }
                serverCache =
                    new CacheNode(
                        IndexedNode.from(serverCacheNode, query.getIndex()), false, false);
              }
            }

            boolean viewAlreadyExists = syncPoint.viewExistsForQuery(query);
            if (!viewAlreadyExists && !query.loadsAllData()) {
              // We need to track a tag for this query
              hardAssert(
                  !queryToTagMap.containsKey(query), "View does not exist but we have a tag");
              Tag tag = getNextQueryTag();
              queryToTagMap.put(query, tag);
              tagToQueryMap.put(tag, query);
            }
            WriteTreeRef writesCache = pendingWriteTree.childWrites(path);
            List<? extends Event> events =
                syncPoint.addEventRegistration(eventRegistration, writesCache, serverCache);
            if (!viewAlreadyExists && !foundAncestorDefaultView) {
              View view = syncPoint.viewForQuery(query);
              setupListener(query, view);
            }
            return events;
          }
        });
  }

  /**
   * Remove event callback(s).
   *
   * <p>If query is the default query, we'll check all queries for the specified eventRegistration.
   */
  public List<Event> removeEventRegistration(@NotNull EventRegistration eventRegistration) {
    return this.removeEventRegistration(eventRegistration.getQuerySpec(), eventRegistration, null);
  }

  /**
   * Remove all event callback(s).
   *
   * <p>If query is the default query, we'll check all queries for the specified eventRegistration.
   */
  public List<Event> removeAllEventRegistrations(
      @NotNull QuerySpec query, @NotNull DatabaseError error) {
    return this.removeEventRegistration(query, null, error);
  }

  private List<Event> removeEventRegistration(
      final @NotNull QuerySpec query,
      final @Nullable EventRegistration eventRegistration,
      final @Nullable DatabaseError cancelError) {
    return persistenceManager.runInTransaction(
        new Callable<List<Event>>() {
          @Override
          public List<Event> call() {
            // Find the syncPoint first. Then deal with whether or not it has matching listeners
            Path path = query.getPath();
            SyncPoint maybeSyncPoint = syncPointTree.get(path);
            List<Event> cancelEvents = new ArrayList<Event>();
            // A removal on a default query affects all queries at that location. A removal on an
            // indexed query, even one without other query constraints, does *not* affect all
            // queries at that location. So this check must be for 'default', and not
            // loadsAllData().
            if (maybeSyncPoint != null
                && (query.isDefault() || maybeSyncPoint.viewExistsForQuery(query))) {
              // @type {{removed: !Array.<!fb.api.Query>, events: !Array.<!fb.core.view.Event>}}

              Pair<List<QuerySpec>, List<Event>> removedAndEvents =
                  maybeSyncPoint.removeEventRegistration(query, eventRegistration, cancelError);
              if (maybeSyncPoint.isEmpty()) {
                syncPointTree = syncPointTree.remove(path);
              }
              List<QuerySpec> removed = removedAndEvents.getFirst();
              cancelEvents = removedAndEvents.getSecond();
              // We may have just removed one of many listeners and can short-circuit this whole
              // process. We may also not have removed a default listener, in which case all of the
              // descendant listeners should already be properly set up.
              //
              // Since indexed queries can shadow if they don't have other query constraints, check
              // for loadsAllData(), instead of isDefault().
              boolean removingDefault = false;
              for (QuerySpec queryRemoved : removed) {
                persistenceManager.setQueryInactive(query);
                removingDefault = removingDefault || queryRemoved.loadsAllData();
              }
              ImmutableTree<SyncPoint> currentTree = syncPointTree;
              boolean covered =
                  currentTree.getValue() != null && currentTree.getValue().hasCompleteView();
              for (ChildKey component : path) {
                currentTree = currentTree.getChild(component);
                covered =
                    covered
                        || (currentTree.getValue() != null
                            && currentTree.getValue().hasCompleteView());
                if (covered || currentTree.isEmpty()) {
                  break;
                }
              }

              if (removingDefault && !covered) {
                ImmutableTree<SyncPoint> subtree = syncPointTree.subtree(path);
                // There are potentially child listeners. Determine what if any listens we need to
                // send before executing the removal.
                if (!subtree.isEmpty()) {
                  // We need to fold over our subtree and collect the listeners to send
                  List<View> newViews = collectDistinctViewsForSubTree(subtree);

                  // Ok, we've collected all the listens we need. Set them up.
                  for (View view : newViews) {
                    ListenContainer container = new ListenContainer(view);
                    QuerySpec newQuery = view.getQuery();
                    listenProvider.startListening(
                        queryForListening(newQuery), container.tag, container, container);
                  }
                } else {
                  // There's nothing below us, so nothing we need to start listening on
                }
              }
              // If we removed anything and we're not covered by a higher up listen, we need to stop
              // listening on this query. The above block has us covered in terms of making sure
              // we're set up on listens lower in the tree.
              // Also, note that if we have a cancelError, it's already been removed at the provider
              // level.
              if (!covered && !removed.isEmpty() && cancelError == null) {
                // If we removed a default, then we weren't listening on any of the other queries
                // here. Just cancel the one default. Otherwise, we need to iterate through and
                // cancel each individual query
                if (removingDefault) {
                  listenProvider.stopListening(queryForListening(query), null);
                } else {
                  for (QuerySpec queryToRemove : removed) {
                    Tag tag = tagForQuery(queryToRemove);
                    hardAssert(tag != null);
                    listenProvider.stopListening(queryForListening(queryToRemove), tag);
                  }
                }
              }
              // Now, clear all of the tags we're tracking for the removed listens
              removeTags(removed);
            } else {
              // No-op, this listener must've been already removed
            }
            return cancelEvents;
          }
        });
  }

  private static class KeepSyncedEventRegistration extends EventRegistration {
    private QuerySpec spec;

    public KeepSyncedEventRegistration(@NotNull QuerySpec spec) {
      this.spec = spec;
    }

    @Override
    public boolean respondsTo(Event.EventType eventType) {
      return false;
    }

    @Override
    public DataEvent createEvent(Change change, QuerySpec query) {
      return null;
    }

    @Override
    public void fireEvent(DataEvent dataEvent) {}

    @Override
    public void fireCancelEvent(DatabaseError error) {}

    @Override
    public EventRegistration clone(QuerySpec newQuery) {
      return new KeepSyncedEventRegistration(newQuery);
    }

    @Override
    public boolean isSameListener(EventRegistration other) {
      return other instanceof KeepSyncedEventRegistration;
    }

    @NotNull
    @Override
    public QuerySpec getQuerySpec() {
      return spec;
    }

    @Override
    public boolean equals(Object other) {
      return (other instanceof KeepSyncedEventRegistration
          && ((KeepSyncedEventRegistration) other).spec.equals(spec));
    }

    @Override
    public int hashCode() {
      return spec.hashCode();
    }
  };

  public void keepSynced(final QuerySpec query, final boolean keep) {
    if (keep && !keepSyncedQueries.contains(query)) {
      // TODO[persistence]: Find better / more efficient way to do keep-synced listeners.
      addEventRegistration(new KeepSyncedEventRegistration(query));
      keepSyncedQueries.add(query);
    } else if (!keep && keepSyncedQueries.contains(query)) {
      removeEventRegistration(new KeepSyncedEventRegistration(query));
      keepSyncedQueries.remove(query);
    }
  }

  /**
   * This collapses multiple unfiltered views into a single view, since we only need a single
   * listener for them.
   */
  private List<View> collectDistinctViewsForSubTree(ImmutableTree<SyncPoint> subtree) {
    ArrayList<View> accumulator = new ArrayList<View>();
    collectDistinctViewsForSubTree(subtree, accumulator);
    return accumulator;
  }

  private void collectDistinctViewsForSubTree(
      ImmutableTree<SyncPoint> subtree, List<View> accumulator) {
    SyncPoint maybeSyncPoint = subtree.getValue();
    if (maybeSyncPoint != null && maybeSyncPoint.hasCompleteView()) {
      accumulator.add(maybeSyncPoint.getCompleteView());
    } else {
      if (maybeSyncPoint != null) {
        accumulator.addAll(maybeSyncPoint.getQueryViews());
      }
      for (Map.Entry<ChildKey, ImmutableTree<SyncPoint>> entry : subtree.getChildren()) {
        collectDistinctViewsForSubTree(entry.getValue(), accumulator);
      }
    }
  }

  private void removeTags(List<QuerySpec> queries) {
    for (QuerySpec removedQuery : queries) {
      if (!removedQuery.loadsAllData()) {
        // We should have a tag for this
        Tag tag = this.tagForQuery(removedQuery);
        hardAssert(tag != null);
        this.queryToTagMap.remove(removedQuery);
        this.tagToQueryMap.remove(tag);
      }
    }
  }

  private QuerySpec queryForListening(QuerySpec query) {
    if (query.loadsAllData() && !query.isDefault()) {
      // We treat queries that load all data as default queries
      return QuerySpec.defaultQueryAtPath(query.getPath());
    } else {
      return query;
    }
  }

  /** For a given new listen, manage the de-duplication of outstanding subscriptions. */
  private void setupListener(QuerySpec query, View view) {
    Path path = query.getPath();
    Tag tag = this.tagForQuery(query);
    ListenContainer container = new ListenContainer(view);

    this.listenProvider.startListening(queryForListening(query), tag, container, container);

    ImmutableTree<SyncPoint> subtree = this.syncPointTree.subtree(path);
    // The root of this subtree has our query. We're here because we definitely need to send a
    // listen for that, but we may need to shadow other listens as well.
    if (tag != null) {
      hardAssert(
          !subtree.getValue().hasCompleteView(),
          "If we're adding a query, it shouldn't be shadowed");
    } else {
      // Shadow everything at or below this location, this is a default listener.
      subtree.foreach(
          new ImmutableTree.TreeVisitor<SyncPoint, Void>() {
            @Override
            public Void onNodeValue(Path relativePath, SyncPoint maybeChildSyncPoint, Void accum) {
              if (!relativePath.isEmpty() && maybeChildSyncPoint.hasCompleteView()) {
                QuerySpec query = maybeChildSyncPoint.getCompleteView().getQuery();
                listenProvider.stopListening(queryForListening(query), tagForQuery(query));
              } else {
                // No default listener here
                for (View syncPointView : maybeChildSyncPoint.getQueryViews()) {
                  QuerySpec childQuery = syncPointView.getQuery();
                  listenProvider.stopListening(
                      queryForListening(childQuery), tagForQuery(childQuery));
                }
              }
              return null;
            }
          });
    }
  }

  /** Return the query associated with the given tag, if we have one */
  private QuerySpec queryForTag(Tag tag) {
    return this.tagToQueryMap.get(tag);
  }

  /** Return the tag associated with the given query. */
  private Tag tagForQuery(QuerySpec query) {
    return this.queryToTagMap.get(query);
  }

  /**
   * Returns a complete cache, if we have one, of the data at a particular path. The location must
   * have a listener above it, but as this is only used by transaction code, that should always be
   * the case anyways.
   *
   * <p>Note: this method will *include* hidden writes from transaction with applyLocally set to
   * false.
   */
  public Node calcCompleteEventCache(Path path, List<Long> writeIdsToExclude) {
    ImmutableTree<SyncPoint> tree = this.syncPointTree;
    SyncPoint currentSyncPoint = tree.getValue();
    Node serverCache = null;
    Path pathToFollow = path;
    Path pathSoFar = Path.getEmptyPath();
    do {
      ChildKey front = pathToFollow.getFront();
      pathToFollow = pathToFollow.popFront();
      pathSoFar = pathSoFar.child(front);
      Path relativePath = Path.getRelative(pathSoFar, path);
      tree = front != null ? tree.getChild(front) : ImmutableTree.<SyncPoint>emptyInstance();
      currentSyncPoint = tree.getValue();
      if (currentSyncPoint != null) {
        serverCache = currentSyncPoint.getCompleteServerCache(relativePath);
      }
    } while (!pathToFollow.isEmpty() && serverCache == null);
    return this.pendingWriteTree.calcCompleteEventCache(path, serverCache, writeIdsToExclude, true);
  }

  /** Static tracker for next query tag. */
  private long nextQueryTag = 1L;

  /** Static accessor for query tags. */
  private Tag getNextQueryTag() {
    return new Tag(nextQueryTag++);
  }

  /**
   * A helper method that visits all descendant and ancestor SyncPoints, applying the operation.
   *
   * <p>NOTES: - Descendant SyncPoints will be visited first (since we raise events depth-first).
   *
   * <p>- We call applyOperation() on each SyncPoint passing three things: 1. A version of the
   * Operation that has been made relative to the SyncPoint location. 2. A WriteTreeRef of any
   * writes we have cached at the SyncPoint location. 3. A snapshot Node with cached server data, if
   * we have it.
   *
   * <p>- We concatenate all of the events returned by each SyncPoint and return the result.
   */
  private List<Event> applyOperationToSyncPoints(Operation operation) {
    return this.applyOperationHelper(
        operation,
        this.syncPointTree, /* serverCache */
        null,
        this.pendingWriteTree.childWrites(Path.getEmptyPath()));
  }

  /** Recursive helper for applyOperationToSyncPoints */
  private List<Event> applyOperationHelper(
      Operation operation,
      ImmutableTree<SyncPoint> syncPointTree,
      Node serverCache,
      WriteTreeRef writesCache) {
    if (operation.getPath().isEmpty()) {
      return this.applyOperationDescendantsHelper(
          operation, syncPointTree, serverCache, writesCache);
    } else {
      SyncPoint syncPoint = syncPointTree.getValue();

      // If we don't have cached server data, see if we can get it from this SyncPoint.
      if (serverCache == null && syncPoint != null) {
        serverCache = syncPoint.getCompleteServerCache(Path.getEmptyPath());
      }

      List<Event> events = new ArrayList<Event>();
      ChildKey childKey = operation.getPath().getFront();
      Operation childOperation = operation.operationForChild(childKey);
      ImmutableTree<SyncPoint> childTree = syncPointTree.getChildren().get(childKey);
      if (childTree != null && childOperation != null) {
        Node childServerCache =
            (serverCache != null) ? serverCache.getImmediateChild(childKey) : null;
        WriteTreeRef childWritesCache = writesCache.child(childKey);
        events.addAll(
            this.applyOperationHelper(
                childOperation, childTree, childServerCache, childWritesCache));
      }

      if (syncPoint != null) {
        events.addAll(syncPoint.applyOperation(operation, writesCache, serverCache));
      }

      return events;
    }
  }

  /** Recursive helper for applyOperationToSyncPoints */
  private List<Event> applyOperationDescendantsHelper(
      final Operation operation,
      ImmutableTree<SyncPoint> syncPointTree,
      Node serverCache,
      final WriteTreeRef writesCache) {
    SyncPoint syncPoint = syncPointTree.getValue();

    // If we don't have cached server data, see if we can get it from this SyncPoint.
    final Node resolvedServerCache;
    if (serverCache == null && syncPoint != null) {
      resolvedServerCache = syncPoint.getCompleteServerCache(Path.getEmptyPath());
    } else {
      resolvedServerCache = serverCache;
    }

    final List<Event> events = new ArrayList<Event>();
    syncPointTree
        .getChildren()
        .inOrderTraversal(
            new LLRBNode.NodeVisitor<ChildKey, ImmutableTree<SyncPoint>>() {
              @Override
              public void visitEntry(ChildKey key, ImmutableTree<SyncPoint> childTree) {
                Node childServerCache = null;
                if (resolvedServerCache != null) {
                  childServerCache = resolvedServerCache.getImmediateChild(key);
                }
                WriteTreeRef childWritesCache = writesCache.child(key);
                Operation childOperation = operation.operationForChild(key);
                if (childOperation != null) {
                  events.addAll(
                      applyOperationDescendantsHelper(
                          childOperation, childTree, childServerCache, childWritesCache));
                }
              }
            });

    if (syncPoint != null) {
      events.addAll(syncPoint.applyOperation(operation, writesCache, resolvedServerCache));
    }

    return events;
  }

  // Package private for testing purposes only
  ImmutableTree<SyncPoint> getSyncPointTree() {
    return syncPointTree;
  }
}
