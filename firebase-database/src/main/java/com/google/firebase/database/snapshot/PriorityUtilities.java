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
import com.google.firebase.database.core.Path;

public class PriorityUtilities {

  public static Node NullPriority() {
    return EmptyNode.Empty();
  }

  public static boolean isValidPriority(Node priority) {
    return priority.getPriority().isEmpty()
        && (priority.isEmpty()
            || priority instanceof DoubleNode
            || priority instanceof StringNode
            || priority instanceof DeferredValueNode);
  }

  public static Node parsePriority(Object value) {
    return parsePriority(null, value);
  }

  public static Node parsePriority(Path nodePath, Object value) {
    Node priority = NodeUtilities.NodeFromJSON(value);
    if (priority instanceof LongNode) {
      priority =
          new DoubleNode(
              Double.valueOf((Long) priority.getValue()), PriorityUtilities.NullPriority());
    }
    if (!isValidPriority(priority)) {
      throw new DatabaseException(
          (nodePath != null ? "Path '" + nodePath + "'" : "Node")
              + " contains invalid priority: Must be a string, double, ServerValue, or null");
    }
    return priority;
  }
}
