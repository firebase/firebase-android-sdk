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

import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.core.ServerValues;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class NodeUtilities {

  public static Node NodeFromJSON(Object value) throws DatabaseException {
    return NodeFromJSON(value, PriorityUtilities.NullPriority());
  }

  public static Node NodeFromJSON(Object value, Node priority) throws DatabaseException {
    try {
      if (value instanceof Map) {
        Map mapValue = (Map) value;
        if (mapValue.containsKey(".priority")) {
          priority = PriorityUtilities.parsePriority(mapValue.get(".priority"));
        }

        if (mapValue.containsKey(".value")) {
          value = mapValue.get(".value");
        }
      }

      if (value == null) {
        return EmptyNode.Empty();
      } else if (value instanceof String) {
        return new StringNode((String) value, priority);
      } else if (value instanceof Long) {
        return new LongNode((Long) value, priority);
      } else if (value instanceof Integer) {
        return new LongNode((long) (Integer) value, priority);
      } else if (value instanceof Double) {
        return new DoubleNode((Double) value, priority);
      } else if (value instanceof Boolean) {
        return new BooleanNode((Boolean) value, priority);
      } else if (value instanceof Map || value instanceof List) {
        Map<ChildKey, Node> childData;
        // TODO: refine this and use same code to iterate over array and map by building
        // List<NamedNode>
        if (value instanceof Map) {
          Map mapValue = (Map) value;
          if (mapValue.containsKey(ServerValues.NAME_SUBKEY_SERVERVALUE)) {
            @SuppressWarnings("unchecked")
            Node node = new DeferredValueNode(mapValue, priority);
            return node;
          } else {
            childData = new HashMap<ChildKey, Node>(mapValue.size());
            @SuppressWarnings("unchecked")
            Iterator<String> keyIter = (Iterator<String>) mapValue.keySet().iterator();
            while (keyIter.hasNext()) {
              String key = keyIter.next();
              if (!key.startsWith(".")) {
                Node childNode = NodeFromJSON(mapValue.get(key));
                if (!childNode.isEmpty()) {
                  ChildKey childKey = ChildKey.fromString(key);
                  childData.put(childKey, childNode);
                }
              }
            }
          }
        } else { // List
          List listValue = (List) value;
          childData = new HashMap<ChildKey, Node>(listValue.size());
          for (int i = 0; i < listValue.size(); ++i) {
            String key = "" + i;
            Node childNode = NodeFromJSON(listValue.get(i));
            if (!childNode.isEmpty()) {
              ChildKey childKey = ChildKey.fromString(key);
              childData.put(childKey, childNode);
            }
          }
        }

        if (childData.isEmpty()) {
          return EmptyNode.Empty();
        } else {
          ImmutableSortedMap<ChildKey, Node> childSet =
              ImmutableSortedMap.Builder.fromMap(childData, ChildrenNode.NAME_ONLY_COMPARATOR);
          return new ChildrenNode(childSet, priority);
        }
      } else {
        throw new DatabaseException(
            "Failed to parse node with class " + value.getClass().toString());
      }
    } catch (ClassCastException e) {
      throw new DatabaseException("Failed to parse node", e);
    }
  }

  public static int nameAndPriorityCompare(
      ChildKey aKey, Node aPriority, ChildKey bKey, Node bPriority) {

    int priCmp = aPriority.compareTo(bPriority);
    if (priCmp != 0) {
      return priCmp;
    } else {
      return aKey.compareTo(bKey);
    }
  }
}
