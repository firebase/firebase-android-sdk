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

import com.google.firebase.database.core.ValueProvider.DeferredValueProvider;
import com.google.firebase.database.core.ValueProvider.ExistingValueProvider;
import com.google.firebase.database.core.utilities.Clock;
import com.google.firebase.database.core.utilities.Utilities;
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

  public static Object resolveDeferredLeafValue(
      Object value, ValueProvider existing, Map<String, Object> serverValues) {
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
      res = resolveScalarDeferredValue((String) op, serverValues);
    } else if (op instanceof Map) {
      res = resolveComplexDeferredValue((Map) op, existing, serverValues);
    }
    if (res == null) {
      return value;
    }
    return res;
  }

  static Object resolveScalarDeferredValue(String op, Map<String, Object> serverValues) {
    if (NAME_OP_TIMESTAMP.equals(op) && serverValues.containsKey(op)) {
      return serverValues.get(op);
    }
    return null;
  }

  static Object resolveComplexDeferredValue(
      Map<String, Object> op, ValueProvider existing, Map<String, Object> serverValues) {
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
    Node existingNode = existing.node();
    if (!(existingNode.isLeafNode() && existingNode.getValue() instanceof Number)) {
      return increment;
    }

    Number existingVal = (Number) existingNode.getValue();
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

  public static Node resolveDeferredValueSnapshot(
      Node data, Node existing, final Map<String, Object> serverValues) {
    return resolveDeferredValueSnapshot(data, new ExistingValueProvider(existing), serverValues);
  }

  public static Node resolveDeferredValueSnapshot(
      Node data, SyncTree syncTree, Path path, final Map<String, Object> serverValues) {
    return resolveDeferredValueSnapshot(
        data, new DeferredValueProvider(syncTree, path), serverValues);
  }

  private static Node resolveDeferredValueSnapshot(
      Node data, ValueProvider existing, final Map<String, Object> serverValues) {
    Object rawPriority = data.getPriority().getValue();
    Object priority =
        resolveDeferredLeafValue(
            rawPriority,
            existing.getImmediateChild(ChildKey.fromString(".priority")),
            serverValues);
    if (data.isLeafNode()) {
      Object value = resolveDeferredLeafValue(data.getValue(), existing, serverValues);
      if (!value.equals(data.getValue()) || !Utilities.equals(priority, rawPriority)) {
        return NodeUtilities.NodeFromJSON(value, PriorityUtilities.parsePriority(priority));
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
        return holder.getRootNode().updatePriority(PriorityUtilities.parsePriority(priority));
      } else {
        return holder.getRootNode();
      }
    }
  }

  public static CompoundWrite resolveDeferredValueMerge(
      CompoundWrite merge, SyncTree syncTree, Path path, final Map<String, Object> serverValues) {
    CompoundWrite write = CompoundWrite.emptyWrite();
    for (Map.Entry<Path, Node> entry : merge) {
      ValueProvider deferredValue = new DeferredValueProvider(syncTree, path.child(entry.getKey()));
      write =
          write.addWrite(
              entry.getKey(),
              resolveDeferredValueSnapshot(entry.getValue(), deferredValue, serverValues));
    }
    return write;
  }

  private static boolean canBeRepresentedAsLong(Number x) {
    // Ignoring types we should never see: BigFoo, AtomicFoo, FooAdder, and FooAccumulator
    return !(x instanceof Double || x instanceof Float);
  }
}
