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

import com.google.firebase.database.core.utilities.Clock;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.ChildrenNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import com.google.firebase.database.snapshot.PriorityUtilities;
import java.util.HashMap;
import java.util.Map;

public class ServerValues {
  public static final String NAME_SUBKEY_SERVERVALUE = ".sv";

  public static Map<String, Object> generateServerValues(Clock clock) {
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("timestamp", clock.millis());
    return values;
  }

  public static Object resolveDeferredValue(Object value, Map<String, Object> serverValues) {
    if (value instanceof Map) {
      Map mapValue = (Map) value;
      if (mapValue.containsKey(NAME_SUBKEY_SERVERVALUE)) {
        String serverValueKey = (String) mapValue.get(NAME_SUBKEY_SERVERVALUE);
        if (serverValues.containsKey(serverValueKey)) {
          return serverValues.get(serverValueKey);
        }
      }
    }
    return value;
  }

  public static SparseSnapshotTree resolveDeferredValueTree(
      SparseSnapshotTree tree, final Map<String, Object> serverValues) {
    final SparseSnapshotTree resolvedTree = new SparseSnapshotTree();
    tree.forEachTree(
        new Path(""),
        new SparseSnapshotTree.SparseSnapshotTreeVisitor() {
          @Override
          public void visitTree(Path prefixPath, Node tree) {
            resolvedTree.remember(prefixPath, resolveDeferredValueSnapshot(tree, serverValues));
          }
        });
    return resolvedTree;
  }

  public static Node resolveDeferredValueSnapshot(
      Node data, final Map<String, Object> serverValues) {
    Object priorityVal = data.getPriority().getValue();
    if (priorityVal instanceof Map) {
      Map priorityMapValue = (Map) priorityVal;
      if (priorityMapValue.containsKey(NAME_SUBKEY_SERVERVALUE)) {
        String serverValueKey = (String) priorityMapValue.get(NAME_SUBKEY_SERVERVALUE);
        priorityVal = serverValues.get(serverValueKey);
      }
    }
    Node priority = PriorityUtilities.parsePriority(priorityVal);

    if (data.isLeafNode()) {
      Object value = resolveDeferredValue(data.getValue(), serverValues);
      if (!value.equals(data.getValue()) || !priority.equals(data.getPriority())) {
        return NodeUtilities.NodeFromJSON(value, priority);
      }
      return data;
    } else if (data.isEmpty()) {
      return data;
    } else {
      ChildrenNode childNode = (ChildrenNode) data;
      final SnapshotHolder holder = new SnapshotHolder(childNode);
      childNode.forEachChild(
          new ChildrenNode.ChildVisitor() {
            @Override
            public void visitChild(ChildKey name, Node child) {
              Node newChildNode = resolveDeferredValueSnapshot(child, serverValues);
              if (newChildNode != child) {
                holder.update(new Path(name.asString()), newChildNode);
              }
            }
          });
      if (!holder.getRootNode().getPriority().equals(priority)) {
        return holder.getRootNode().updatePriority(priority);
      } else {
        return holder.getRootNode();
      }
    }
  }

  public static CompoundWrite resolveDeferredValueMerge(
      CompoundWrite merge, final Map<String, Object> serverValues) {
    CompoundWrite write = CompoundWrite.emptyWrite();
    for (Map.Entry<Path, Node> entry : merge) {
      write =
          write.addWrite(
              entry.getKey(), resolveDeferredValueSnapshot(entry.getValue(), serverValues));
    }
    return write;
  }
}
