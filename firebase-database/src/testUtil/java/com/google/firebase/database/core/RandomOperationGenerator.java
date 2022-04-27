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

import com.google.firebase.database.core.operation.AckUserWrite;
import com.google.firebase.database.core.operation.ListenComplete;
import com.google.firebase.database.core.operation.Merge;
import com.google.firebase.database.core.operation.Operation;
import com.google.firebase.database.core.operation.OperationSource;
import com.google.firebase.database.core.operation.Overwrite;
import com.google.firebase.database.core.utilities.ImmutableTree;
import com.google.firebase.database.core.view.CacheNode;
import com.google.firebase.database.core.view.QueryParams;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.core.view.filter.IndexedFilter;
import com.google.firebase.database.core.view.filter.LimitedFilter;
import com.google.firebase.database.core.view.filter.NodeFilter;
import com.google.firebase.database.core.view.filter.RangedFilter;
import com.google.firebase.database.snapshot.BooleanNode;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.DoubleNode;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.KeyIndex;
import com.google.firebase.database.snapshot.LongNode;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.PathIndex;
import com.google.firebase.database.snapshot.PriorityIndex;
import com.google.firebase.database.snapshot.PriorityUtilities;
import com.google.firebase.database.snapshot.StringNode;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Generates random write, merge and ack operations for server and user operations based on a simple
 * random model. This class also tracks the expected state the client and the server is in to use as
 * a ground truth.
 */
public class RandomOperationGenerator {

  /**
   * MODEL PARAMETERS These are mostly chosen by a simple guess, but should be good enough to find
   * broken edge cases
   */
  private static final double SERVER_OP_PROBABILITY = 0.7;

  private static final double MERGE_PROBABILITY = 0.3;
  private static final double REVERT_PROBABILITY = 0.5;
  private static final double SERVER_ACK_PROBABILITY = 0.7;
  private static final double SERVER_LISTEN_RESPONSE_PROBABILITY = 0.7;

  private static final double DEFAULT_PARAMS_PROBABILITY = 0.5;

  private static final int MAX_MERGE_SIZE = 20;

  private static final int MAX_DEPTH = 3;
  private static final int MAX_TOP_CHILDREN = 10;
  private static final int MAX_OTHER_CHILDREN = 3;

  private static final double PRIORITY_KEY_PROBABILITY = 0.1;
  private static final double INDEX_KEY_PROBABILITY = 0.1;

  private static final int MAX_PRIO_VALUES = 10;
  private static final int MAX_KEY_VALUES = 100;

  private Node currentServerState;
  private long currentWriteId;
  private Queue<WriteOp> outstandingWrites;
  private WriteTree writeTree;
  private Queue<QuerySpec> outstandingListens;
  private Queue<QuerySpec> outstandingUnlistens;
  private QuerySpec completeDataJustSentForListen;
  private WriteOp writeOpForLastUpdate;
  private final Random random;
  private final long seed;

  public RandomOperationGenerator() {
    this(new Random().nextInt());
  }

  public RandomOperationGenerator(long seed) {
    this.random = new Random(seed);
    this.seed = seed;
    this.currentServerState = getRandomNode(0, MAX_DEPTH);
    this.outstandingWrites = new ArrayDeque<>();
    this.outstandingListens = new ArrayDeque<>();
    this.outstandingUnlistens = new ArrayDeque<>();
    this.writeTree = new WriteTree();
  }

  public void listen(QuerySpec query) {
    this.outstandingListens.add(query);
  }

  public Operation nextOperation() {
    if (this.writeOpForLastUpdate != null) {
      return getAck();
    }
    if (random.nextDouble() < SERVER_OP_PROBABILITY) {
      if (this.completeDataJustSentForListen != null) {
        Path path = this.completeDataJustSentForListen.getPath();
        QueryParams params = this.completeDataJustSentForListen.getParams();
        OperationSource source;
        if (!params.loadsAllData()) {
          source =
              OperationSource.forServerTaggedQuery(this.completeDataJustSentForListen.getParams());
        } else {
          source = OperationSource.SERVER;
        }
        completeDataJustSentForListen = null;
        return new ListenComplete(source, path);
      } else if (!outstandingListens.isEmpty()
          && random.nextDouble() < SERVER_LISTEN_RESPONSE_PROBABILITY) {
        QuerySpec listen = outstandingListens.poll();
        QueryParams params = listen.getParams();
        Path path = listen.getPath();
        Node currentServerNode = this.currentServerState.getChild(path);
        if (!params.loadsAllData()) {
          IndexedNode emptyNode = IndexedNode.from(EmptyNode.Empty(), params.getIndex());
          IndexedNode node =
              params
                  .getNodeFilter()
                  .updateFullNode(
                      emptyNode, IndexedNode.from(currentServerNode, params.getIndex()), null);
          OperationSource source = OperationSource.forServerTaggedQuery(listen.getParams());
          return new Overwrite(source, path, node.getNode());
        } else {
          return new Overwrite(OperationSource.SERVER, path, currentServerNode);
        }
      } else if (!outstandingUnlistens.isEmpty()
          && random.nextDouble() < SERVER_LISTEN_RESPONSE_PROBABILITY) {
        throw new RuntimeException("NOT IMPL!");
      } else if (outstandingWrites.size() > 0 && random.nextDouble() < SERVER_ACK_PROBABILITY) {
        WriteOp op = outstandingWrites.peek();
        Path path = op.operation.getPath();
        boolean isEmptyPriorityError =
            !path.isEmpty()
                && path.getBack().isPriorityChildName()
                && this.currentServerState.getChild(path.getParent()).isEmpty();
        if (random.nextDouble() < REVERT_PROBABILITY || isEmptyPriorityError) {
          writeTree.removeWrite(op.writeId);
          outstandingWrites.remove();
          return getAckForWrite(op.operation, /*revert=*/ true);
        } else {
          // TODO: maybe ignore if write equals server data
          Operation serverOp = userOperationToServerOperation(op.operation);
          this.currentServerState = applyOperation(serverOp, this.currentServerState);
          this.writeOpForLastUpdate = op;
          return serverOp;
        }
      } else {
        if (random.nextDouble() < MERGE_PROBABILITY) {
          Merge merge = getRandomMerge(OperationSource.SERVER);
          this.currentServerState = applyOperation(merge, currentServerState);
          return merge;
        } else {
          Overwrite overwrite = getRandomOverwrite(OperationSource.SERVER);
          if (overwrite.getPath().getBack() != null
              && overwrite.getPath().getBack().isPriorityChildName()
              && this.currentServerState.getChild(overwrite.getPath().getParent()).isEmpty()) {
            // This is a case where we would overwrite a priority on an empty node which is an
            // illegal update
            return nextOperation();
          }
          this.currentServerState = applyOperation(overwrite, currentServerState);
          return overwrite;
        }
      }
    } else {
      if (random.nextDouble() < MERGE_PROBABILITY) {
        Merge merge = getRandomMerge(OperationSource.USER);
        WriteOp writeOp = new WriteOp(merge, currentWriteId++);
        outstandingWrites.add(writeOp);
        writeTree.addMerge(
            merge.getPath(), CompoundWrite.fromChildMerge(getMergeMap(merge)), writeOp.writeId);
        return merge;
      } else {
        Overwrite overwrite = getRandomOverwrite(OperationSource.USER);
        WriteOp writeOp = new WriteOp(overwrite, currentWriteId++);
        outstandingWrites.add(writeOp);
        writeTree.addOverwrite(overwrite.getPath(), overwrite.getSnapshot(), writeOp.writeId, true);
        return overwrite;
      }
    }
  }

  public QueryParams nextRandomParams() {
    return getRandomQueryParams();
  }

  public WriteTree getWriteTree() {
    return this.writeTree;
  }

  public long getSeed() {
    return this.seed;
  }

  public Node getCurrentServerNode(Path path) {
    return this.currentServerState.getChild(path);
  }

  public CacheNode getExpectedClientState(QueryParams params) {
    CacheNode cacheNode;
    // TODO: this is broken for multiple listens
    if (!outstandingListens.isEmpty() && !(writeTree.shadowingWrite(Path.getEmptyPath()) != null)) {
      // special case when data has not been sent, maybe have incomplete user writes
      Node node = EmptyNode.Empty();
      // first: gather any complete children
      Set<ChildKey> completeChildren = new HashSet<ChildKey>();
      for (WriteOp op : outstandingWrites) {
        if (op.operation.getType() == Operation.OperationType.Overwrite) {
          Overwrite overwrite = (Overwrite) op.operation;
          if (overwrite.getPath().isEmpty()) {
            for (NamedNode childEntry : overwrite.getSnapshot()) {
              completeChildren.add(childEntry.getName());
            }
          } else if (op.operation.getPath().size() == 1) {
            completeChildren.add(op.operation.getPath().getFront());
          }
        } else if (op.operation.getType() == Operation.OperationType.Merge) {
          Merge merge = (Merge) op.operation;
          if (merge.getPath().isEmpty()) {
            for (NamedNode completeChild : merge.getChildren().getCompleteChildren()) {
              completeChildren.add(completeChild.getName());
            }
          }
        } else {
          throw new IllegalStateException("Unknown user write operation: " + op.operation);
        }
      }
      // second: apply all overwrites for complete children
      for (WriteOp op : outstandingWrites) {
        Path writePath = op.operation.getPath();
        if (writePath.isEmpty() || completeChildren.contains(writePath.getFront())) {
          node = applyOperation(op.operation, node);
        }
      }
      cacheNode = new CacheNode(IndexedNode.from(node, params.getIndex()), false, false);
    } else {
      Node node = this.currentServerState;
      // TODO: somehow filter out incomplete children
      for (WriteOp op : outstandingWrites) {
        node = applyOperation(op.operation, node);
      }
      cacheNode = new CacheNode(IndexedNode.from(node, params.getIndex()), true, false);
    }
    NodeFilter filter;
    if (params.hasLimit()) {
      filter = new LimitedFilter(params);
    } else if (params.hasStart() || params.hasEnd()) {
      filter = new RangedFilter(params);
    } else {
      filter = new IndexedFilter(params.getIndex());
    }
    if (params.loadsAllData()) {
      return cacheNode;
    } else {
      // Maybe write custom filter?
      IndexedNode newNode =
          filter.updateFullNode(
              IndexedNode.from(EmptyNode.Empty()), cacheNode.getIndexedNode(), null);
      return new CacheNode(newNode, cacheNode.isFullyInitialized(), filter.filtersNodes());
    }
  }

  private Operation getAck() {
    WriteOp op = this.writeOpForLastUpdate;
    WriteOp frontOp = outstandingWrites.remove();
    hardAssert(op == frontOp, "The write op should be the front of the queue");
    writeTree.removeWrite(op.writeId);
    this.writeOpForLastUpdate = null;
    return getAckForWrite(op.operation, /*revert=*/ false);
  }

  private Operation getAckForWrite(Operation writeOp, boolean revert) {
    if (writeOp.getType() == Operation.OperationType.Overwrite) {
      Overwrite overwrite = (Overwrite) writeOp;
      return new AckUserWrite(overwrite.getPath(), new ImmutableTree<Boolean>(true), revert);
    } else {
      hardAssert(writeOp.getType() == Operation.OperationType.Merge);
      Merge merge = (Merge) writeOp;
      ImmutableTree<Boolean> affectedTree = ImmutableTree.emptyInstance();
      for (Map.Entry<Path, Node> entry : merge.getChildren()) {
        Path path = entry.getKey();
        affectedTree = affectedTree.set(path, true);
      }
      return new AckUserWrite(merge.getPath(), affectedTree, revert);
    }
  }

  private static class WriteOp {
    private final long writeId;
    private final Operation operation;

    public WriteOp(Operation operation, long writeId) {
      this.writeId = writeId;
      this.operation = operation;
    }
  }

  private ChildKey getRandomKey(boolean allowPriority) {
    double randValue = random.nextDouble();
    if (allowPriority && randValue < PRIORITY_KEY_PROBABILITY) {
      return ChildKey.getPriorityKey();
    } else if (random.nextDouble() < INDEX_KEY_PROBABILITY) {
      return ChildKey.fromString("index-key");
    } else {
      return ChildKey.fromString("key-" + random.nextInt(MAX_KEY_VALUES));
    }
  }

  private Node getRandomPriority() {
    if (random.nextDouble() < 0.9) {
      return EmptyNode.Empty();
    } else if (random.nextDouble() < 0.5) {
      return new StringNode("prio-" + random.nextInt(MAX_PRIO_VALUES), EmptyNode.Empty());
    } else {
      return new DoubleNode((double) random.nextInt(MAX_PRIO_VALUES), EmptyNode.Empty());
    }
  }

  private Node getRandomLeafNodeWithoutLongs() {
    return getRandomLeafNodeFromRandom(random.nextDouble() * 0.8);
  }

  private Node getRandomLeafNode() {
    return getRandomLeafNodeFromRandom(random.nextDouble());
  }

  private Node getRandomLeafNodeFromRandom(Double randValue) {
    if (randValue < 0.2) {
      return EmptyNode.Empty();
    } else if (randValue < 0.4) {
      return new BooleanNode(random.nextBoolean(), getRandomPriority());
    } else if (randValue < 0.6) {
      return new StringNode("string-" + random.nextInt(), getRandomPriority());
    } else if (randValue < 0.8) {
      return new DoubleNode(random.nextDouble(), getRandomPriority());
    } else {
      return new LongNode(random.nextLong(), getRandomPriority());
    }
  }

  private Node getRandomNode(int curDepth, int maxDepth) {
    if (curDepth >= maxDepth) {
      return getRandomLeafNode();
    } else {
      double randValue = random.nextDouble();
      if (randValue < 0.2) {
        return EmptyNode.Empty();
      } else if (randValue < 0.4) {
        return getRandomLeafNode();
      } else {
        int numChildren = 1 + random.nextInt(curDepth == 0 ? MAX_TOP_CHILDREN : MAX_OTHER_CHILDREN);
        Node currentNode = EmptyNode.Empty();
        for (int i = 0; i < numChildren; i++) {
          ChildKey randomKey = getRandomKey(false);
          Node randomChildNode = getRandomNode(curDepth + 1, maxDepth);
          currentNode = currentNode.updateImmediateChild(randomKey, randomChildNode);
        }
        if (currentNode.isEmpty()) {
          return EmptyNode.Empty();
        } else {
          return currentNode.updatePriority(getRandomPriority());
        }
      }
    }
  }

  private QueryParams getRandomQueryParams() {
    if (random.nextDouble() < DEFAULT_PARAMS_PROBABILITY) {
      return QueryParams.DEFAULT_PARAMS;
    } else {
      double type = random.nextDouble();
      boolean hasEnd = random.nextBoolean();
      boolean hasStart = random.nextBoolean();
      boolean hasLimit = random.nextBoolean();
      QueryParams params = QueryParams.DEFAULT_PARAMS;
      Node start;
      Node end;
      if (type < 0.4) {
        params = params.orderBy(new PathIndex(new Path("index-key")));
        start = getRandomLeafNodeWithoutLongs();
        end = getRandomLeafNodeWithoutLongs();
      } else if (type < 0.8) {
        // Order by key
        params = params.orderBy(KeyIndex.getInstance());
        ChildKey startKey = getRandomKey(false);
        ChildKey endKey = getRandomKey(false);
        start = new StringNode(startKey.asString(), PriorityUtilities.NullPriority());
        end = new StringNode(endKey.asString(), PriorityUtilities.NullPriority());
      } else {
        // Order by Priority
        params = params.orderBy(PriorityIndex.getInstance());
        start = getRandomPriority();
        end = getRandomPriority();
      }
      if (hasStart && hasEnd && start.compareTo(end) > 0) {
        Node tmp = start;
        start = end;
        end = tmp;
      }
      if (hasStart) {
        params = params.startAt(start, null);
      }
      if (hasEnd) {
        params = params.endAt(end, null);
      }
      if (hasLimit) {
        int limit = random.nextInt(30) + 1;
        boolean limitToFirst = random.nextBoolean();
        if (limitToFirst) {
          params = params.limitToFirst(limit);
        } else {
          params = params.limitToLast(limit);
        }
      }
      return params;
    }
  }

  public Path nextRandomPath(int maxDepth) {
    int depth = random.nextInt(maxDepth);
    Path path = Path.getEmptyPath();
    for (int i = 0; i < depth; i++) {
      ChildKey key = getRandomKey(false);
      path = path.child(key);
    }
    return path;
  }

  private Merge getRandomMerge(OperationSource source) {
    Path path = nextRandomPath(MAX_DEPTH);
    int numMergeNodes = random.nextInt(MAX_MERGE_SIZE) + 1;
    CompoundWrite write = CompoundWrite.emptyWrite();
    for (int i = 0; i < numMergeNodes; i++) {
      write =
          write.addWrite(new Path(getRandomKey(false)), getRandomNode(path.size() + 1, MAX_DEPTH));
    }
    return new Merge(source, path, write);
  }

  private Map<ChildKey, Node> getMergeMap(Merge merge) {
    Map<ChildKey, Node> map = new HashMap<ChildKey, Node>();
    for (Map.Entry<ChildKey, CompoundWrite> entry :
        merge.getChildren().childCompoundWrites().entrySet()) {
      ChildKey key = entry.getKey();
      CompoundWrite childWrite = entry.getValue();
      hardAssert(
          childWrite.rootWrite() != null, "This is a deep overwrite, which is not supported");
      map.put(key, childWrite.rootWrite());
    }
    return map;
  }

  private Overwrite getRandomOverwrite(OperationSource source) {
    Path path = nextRandomPath(MAX_DEPTH);
    Node node;
    if (random.nextDouble() < PRIORITY_KEY_PROBABILITY) {
      path = path.child(ChildKey.getPriorityKey());
      node = getRandomPriority();
    } else {
      int maxNodeDepth = MAX_DEPTH - path.size();
      node = getRandomNode(path.size(), maxNodeDepth);
    }
    return new Overwrite(source, path, node);
  }

  private static Operation userOperationToServerOperation(Operation operation) {
    hardAssert(operation.getSource().isFromUser());
    switch (operation.getType()) {
      case Overwrite:
        {
          Overwrite overwrite = (Overwrite) operation;
          return new Overwrite(
              OperationSource.SERVER, overwrite.getPath(), overwrite.getSnapshot());
        }
      case Merge:
        {
          Merge merge = (Merge) operation;
          return new Merge(OperationSource.SERVER, merge.getPath(), merge.getChildren());
        }
      default:
        {
          throw new IllegalArgumentException(
              "Can't convert operation of type " + operation.getType() + " to node");
        }
    }
  }

  private static Node applyOperation(Operation operation, Node node) {
    switch (operation.getType()) {
      case Overwrite:
        {
          Overwrite overwrite = (Overwrite) operation;
          Path path = overwrite.getPath();
          if (!path.isEmpty()
              && path.getBack().isPriorityChildName()
              && node.getChild(path.getParent()).isEmpty()) {
            // Don't update priorities on empty nodes
            return node;
          } else {
            return node.updateChild(overwrite.getPath(), overwrite.getSnapshot());
          }
        }
      case Merge:
        {
          Merge merge = (Merge) operation;
          Path path = merge.getPath();
          Node child = node.getChild(path);
          return node.updateChild(path, merge.getChildren().apply(child));
        }
      case ListenComplete:
        {
          // No-op
          return node;
        }
      default:
        {
          throw new IllegalArgumentException(
              "Can't apply operation of type " + operation.getType() + " to node");
        }
    }
  }
}
