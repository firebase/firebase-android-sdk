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

package com.google.firebase.database.snapshot;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.google.firebase.database.core.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies a merge of a snap for a given interval of paths. Each leaf in the current node which the
 * relative path lies *after* optExclusiveStart and lies *before or at* optInclusiveEnd will be
 * deleted. Each leaf in snap that lies in the interval will be added to the resulting node. Nods
 * outside of the range are ignored. null for start and end are sentinel values that represent
 * -infinity and infinity respectively (aka includes any path). Priorities of children nodes are
 * treated as leaf children of that node.
 */
public class RangeMerge {

  private final Path optExclusiveStart;
  private final Path optInclusiveEnd;
  private final Node snap;

  public RangeMerge(Path optExclusiveStart, Path optInclusiveEnd, Node snap) {
    this.optExclusiveStart = optExclusiveStart;
    this.optInclusiveEnd = optInclusiveEnd;
    this.snap = snap;
  }

  public RangeMerge(com.google.firebase.database.connection.RangeMerge rangeMerge) {
    List<String> optStartPathList = rangeMerge.getOptExclusiveStart();
    this.optExclusiveStart = (optStartPathList != null) ? new Path(optStartPathList) : null;
    List<String> optEndPathList = rangeMerge.getOptInclusiveEnd();
    this.optInclusiveEnd = (optEndPathList != null) ? new Path(optEndPathList) : null;
    this.snap = NodeUtilities.NodeFromJSON(rangeMerge.getSnap());
  }

  public Node applyTo(Node node) {
    return updateRangeInNode(Path.getEmptyPath(), node, this.snap);
  }

  Path getStart() {
    return optExclusiveStart;
  }

  Path getEnd() {
    return optInclusiveEnd;
  }

  private Node updateRangeInNode(Path currentPath, Node node, Node updateNode) {
    int startComparison =
        (optExclusiveStart == null) ? 1 : currentPath.compareTo(optExclusiveStart);
    int endComparison = (optInclusiveEnd == null) ? -1 : currentPath.compareTo(optInclusiveEnd);
    boolean startInNode = optExclusiveStart != null && currentPath.contains(optExclusiveStart);
    boolean endInNode = optInclusiveEnd != null && currentPath.contains(optInclusiveEnd);
    if (startComparison > 0 && endComparison < 0 && !endInNode) {
      // child is completely contained
      return updateNode;
    } else if (startComparison > 0 && endInNode && updateNode.isLeafNode()) {
      return updateNode;
    } else if (startComparison > 0 && endComparison == 0) {
      hardAssert(endInNode);
      hardAssert(!updateNode.isLeafNode());
      if (node.isLeafNode()) {
        // Update node was not a leaf node, so we can delete it
        return EmptyNode.Empty();
      } else {
        // Unaffected by range, ignore
        return node;
      }
    } else if (startInNode || endInNode) {
      // There is a partial update we need to do
      // Collect all relevant children
      Set<ChildKey> allChildren = new HashSet<ChildKey>();
      for (NamedNode child : node) {
        allChildren.add(child.getName());
      }
      for (NamedNode child : updateNode) {
        allChildren.add(child.getName());
      }
      List<ChildKey> inOrder = new ArrayList<ChildKey>(allChildren.size() + 1);
      inOrder.addAll(allChildren);
      // Add priority last, so the node is not empty when applying
      if (!updateNode.getPriority().isEmpty() || !node.getPriority().isEmpty()) {
        inOrder.add(ChildKey.getPriorityKey());
      }
      Node newNode = node;
      for (ChildKey key : inOrder) {
        Node currentChild = node.getImmediateChild(key);
        Node updatedChild =
            updateRangeInNode(
                currentPath.child(key),
                node.getImmediateChild(key),
                updateNode.getImmediateChild(key));
        // Only need to update if the node changed
        if (updatedChild != currentChild) {
          newNode = newNode.updateImmediateChild(key, updatedChild);
        }
      }
      return newNode;
    } else {
      // Unaffected by this range
      hardAssert(endComparison > 0 || startComparison <= 0);
      return node;
    }
  }

  @Override
  public String toString() {
    return "RangeMerge{"
        + "optExclusiveStart="
        + optExclusiveStart
        + ", optInclusiveEnd="
        + optInclusiveEnd
        + ", snap="
        + snap
        + '}';
  }
}
