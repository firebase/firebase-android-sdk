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

import com.google.firebase.database.core.Path;
import java.util.Iterator;

public interface Node extends Comparable<Node>, Iterable<NamedNode> {
  /** */
  public enum HashVersion {
    // V1 is the initial hashing schema used by Firebase Database
    V1,
    // V2 escapes and quotes strings and is used by compound hashing
    V2
  }

  public boolean isLeafNode();

  public Node getPriority();

  public Node getChild(Path path);

  public Node getImmediateChild(ChildKey name);

  public Node updateImmediateChild(ChildKey name, Node node);

  public ChildKey getPredecessorChildKey(ChildKey childKey);

  public ChildKey getSuccessorChildKey(ChildKey childKey);

  public Node updateChild(Path path, Node node);

  public Node updatePriority(Node priority);

  public boolean hasChild(ChildKey name);

  public boolean isEmpty();

  public int getChildCount();

  public Object getValue();

  public Object getValue(boolean useExportFormat);

  public String getHash();

  public String getHashRepresentation(HashVersion version);

  public Iterator<NamedNode> reverseIterator();

  public static ChildrenNode MAX_NODE =
      new ChildrenNode() {
        @Override
        public int compareTo(Node other) {
          return (other == this) ? 0 : 1;
        }

        @Override
        public boolean equals(Object other) {
          return other == this;
        }

        @Override
        public Node getPriority() {
          return this;
        }

        @Override
        public boolean isEmpty() {
          return false;
        }

        @Override
        public boolean hasChild(ChildKey childKey) {
          return false;
        }

        @Override
        public Node getImmediateChild(ChildKey name) {
          if (name.isPriorityChildName()) {
            return getPriority();
          } else {
            return EmptyNode.Empty();
          }
        }

        @Override
        public String toString() {
          return "<Max Node>";
        }
      };
}
