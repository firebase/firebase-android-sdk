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
  public static final String NAME_OP_TIMESTAMP = "timestamp";
  public static final String NAME_OP_INCREMENT = "increment";

  public static Map<String, Object> generateServerValues(Clock clock) {
    Map<String, Object> values = new HashMap<String, Object>();
    values.put(NAME_OP_TIMESTAMP, clock.millis());
    return values;
  }

  public static Object resolveDeferredValue(
      Object value, Node existing, Map<String, Object> serverValues) {
    if (!(value instanceof Map)) {
      return value;
    }
    Map mapValue = (Map) value;
    if (!mapValue.containsKey(NAME_SUBKEY_SERVERVALUE)) {
      return value;
    }

    Object op = mapValue.get(NAME_SUBKEY_SERVERVALUE);
    Object res = null;
    if (op instanceof String) {
      res = resolveScalarDeferredValue((String) op, existing, serverValues);
    } else if (op instanceof Map) {
      res = resolveComplexDeferredValue((Map) op, existing, serverValues);
    }
    if (res == null) {
      return value;
    }
    return res;
  }

  static Object resolveScalarDeferredValue(
      String op, Node existing, Map<String, Object> serverValues) {
    if (NAME_OP_TIMESTAMP.equals(op) && serverValues.containsKey(op)) {
      return serverValues.get(op);
    }
    return null;
  }

  static Object resolveComplexDeferredValue(
      Map<String, Object> op, Node existing, Map<String, Object> serverValues) {
    // Only supported complex op so far
    if (!op.containsKey(NAME_OP_INCREMENT)) {
      return null;
    }

    Object incrObject = op.get(NAME_OP_INCREMENT);
    if (!(incrObject instanceof Number)) {
      return null;
    }

    Number increment = (Number) incrObject;

    // Incrementing a non-number sets the value to the incremented amount
    if (!(existing.isLeafNode() && existing.getValue() instanceof Number)) {
      return increment;
    }

    Number existingVal = (Number) existing.getValue();
    if (canBeRepresentedAsLong(increment) && canBeRepresentedAsLong(existingVal)) {
      long x = increment.longValue();
      long y = existingVal.longValue();
      long r = x + y;

      // See "Hacker's Delight" 2-12: Overflow if both arguments have the opposite
      // sign of the result
      if (((x ^ r) & (y ^ r)) >= 0) {
        return r;
      }
    }
    return increment.doubleValue() + existingVal.doubleValue();
  }

  public static SparseSnapshotTree resolveDeferredValueTree(
      SparseSnapshotTree tree, Node existing, final Map<String, Object> serverValues) {
    final SparseSnapshotTree resolvedTree = new SparseSnapshotTree();
    tree.forEachTree(
        new Path(""),
        new SparseSnapshotTree.SparseSnapshotTreeVisitor() {
          @Override
          public void visitTree(Path prefixPath, Node tree) {
            resolvedTree.remember(
                prefixPath,
                resolveDeferredValueSnapshot(tree, existing.getChild(prefixPath), serverValues));
          }
        });
    return resolvedTree;
  }

  public static Node resolveDeferredValueSnapshot(
      Node data, Node existing, final Map<String, Object> serverValues) {
    Object priorityVal =
        resolveDeferredValue(data.getPriority().getValue(), existing.getPriority(), serverValues);
    Node priority = PriorityUtilities.parsePriority(priorityVal);

    if (data.isLeafNode()) {
      Object value = resolveDeferredValue(data.getValue(), existing, serverValues);
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
              Node newChildNode =
                  resolveDeferredValueSnapshot(
                      child, existing.getImmediateChild(name), serverValues);
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
      CompoundWrite merge, Node existing, final Map<String, Object> serverValues) {
    CompoundWrite write = CompoundWrite.emptyWrite();
    for (Map.Entry<Path, Node> entry : merge) {
      write =
          write.addWrite(
              entry.getKey(),
              resolveDeferredValueSnapshot(
                  entry.getValue(), existing.getChild(entry.getKey()), serverValues));
    }
    return write;
  }

  private static boolean canBeRepresentedAsLong(Number x) {
    // Ignoring types we should never see: BigFoo, AtomicFoo, FooAdder, and FooAccumulator
    return !(x instanceof Double || x instanceof Float);
  }
}
