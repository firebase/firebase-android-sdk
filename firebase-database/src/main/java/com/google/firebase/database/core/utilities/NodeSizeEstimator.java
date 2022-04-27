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

package com.google.firebase.database.core.utilities;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.google.firebase.database.snapshot.BooleanNode;
import com.google.firebase.database.snapshot.ChildrenNode;
import com.google.firebase.database.snapshot.DoubleNode;
import com.google.firebase.database.snapshot.LeafNode;
import com.google.firebase.database.snapshot.LongNode;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.StringNode;

public class NodeSizeEstimator {

  /**
   * Account for extra overhead due to the extra JSON object and the ".value" and ".priority" keys,
   * colons, and comma
   */
  private static final int LEAF_PRIORITY_OVERHEAD = 2 + 8 + 11 + 2 + 1;

  private static long estimateLeafNodeSize(LeafNode<?> node) {
    // These values are somewhat arbitrary, but we don't need an exact value so prefer performance
    // over exact value
    long valueSize;
    if (node instanceof DoubleNode) {
      valueSize = 8; // estimate each float with 8 bytes
    } else if (node instanceof LongNode) {
      valueSize = 8;
    } else if (node instanceof BooleanNode) {
      valueSize = 4; // true or false need roughly 4 bytes
    } else if (node instanceof StringNode) {
      valueSize = 2L + ((String) node.getValue()).length(); // add 2 for quotes
    } else {
      throw new IllegalArgumentException("Unknown leaf node type: " + node.getClass());
    }
    if (node.getPriority().isEmpty()) {
      return valueSize;
    } else {
      return LEAF_PRIORITY_OVERHEAD
          + valueSize
          + estimateLeafNodeSize((LeafNode<?>) node.getPriority());
    }
  }

  public static long estimateSerializedNodeSize(Node node) {
    if (node.isEmpty()) {
      return 4; // null keyword
    } else if (node.isLeafNode()) {
      return estimateLeafNodeSize((LeafNode<?>) node);
    } else {
      hardAssert(node instanceof ChildrenNode, "Unexpected node type: " + node.getClass());
      long sum = 1; // opening brackets
      for (NamedNode entry : node) {
        sum += entry.getName().asString().length(); // key
        sum += 4; // quotes around key and colon and (comma or closing bracket)
        sum += estimateSerializedNodeSize(entry.getNode());
      }
      if (!node.getPriority().isEmpty()) {
        sum += 12; // "overhead for ".priority", key and colon and comma
        sum += estimateLeafNodeSize((LeafNode<?>) node.getPriority());
      }
      return sum;
    }
  }

  public static int nodeCount(Node node) {
    if (node.isEmpty()) {
      return 0;
    } else if (node.isLeafNode()) {
      return 1;
    } else {
      hardAssert(node instanceof ChildrenNode, "Unexpected node type: " + node.getClass());
      int sum = 0;
      for (NamedNode entry : node) {
        sum += nodeCount(entry.getNode());
      }
      return sum;
    }
  }
}
