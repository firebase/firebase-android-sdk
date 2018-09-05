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
import java.util.Collections;
import java.util.Iterator;

public class EmptyNode extends ChildrenNode implements Node {

  private static final EmptyNode empty = new EmptyNode();

  private EmptyNode() {
    // prevent instantiation
  }

  public static EmptyNode Empty() {
    return empty;
  }

  @Override
  public boolean isLeafNode() {
    return false;
  }

  @Override
  public Node getPriority() {
    return this;
  }

  @Override
  public Node getChild(Path path) {
    return this;
  }

  @Override
  public Node getImmediateChild(ChildKey name) {
    return this;
  }

  @Override
  public Node updateImmediateChild(ChildKey name, Node node) {
    if (node.isEmpty()) {
      return this;
    } else if (name.isPriorityChildName()) {
      // Don't allow priorities on empty nodes
      return this;
    } else {
      return new ChildrenNode().updateImmediateChild(name, node);
    }
  }

  @Override
  public Node updateChild(Path path, Node node) {
    if (path.isEmpty()) {
      return node;
    } else {
      ChildKey name = path.getFront();
      Node newImmediateChild = getImmediateChild(name).updateChild(path.popFront(), node);
      return updateImmediateChild(name, newImmediateChild);
    }
  }

  @Override
  public EmptyNode updatePriority(Node priority) {
    // Don't allow priorities on empty nodes
    return this;
  }

  @Override
  public boolean hasChild(ChildKey name) {
    return false;
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public int getChildCount() {
    return 0;
  }

  @Override
  public Object getValue() {
    return null;
  }

  @Override
  public Object getValue(boolean useExportFormat) {
    return null;
  }

  @Override
  public ChildKey getPredecessorChildKey(ChildKey childKey) {
    return null;
  }

  @Override
  public ChildKey getSuccessorChildKey(ChildKey childKey) {
    return null;
  }

  @Override
  public String getHash() {
    return "";
  }

  @Override
  public String getHashRepresentation(HashVersion version) {
    return "";
  }

  @Override
  public Iterator<NamedNode> iterator() {
    return Collections.<NamedNode>emptyList().iterator();
  }

  @Override
  public Iterator<NamedNode> reverseIterator() {
    return Collections.<NamedNode>emptyList().iterator();
  }

  @Override
  public int compareTo(Node o) {
    return o.isEmpty() ? 0 : -1;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof EmptyNode) {
      // We don't have a priority, so we know were always equal
      return true;
    } else {
      // have to check for an empty ChildrenNode, aka isEmpty is true
      return (o instanceof Node)
          && ((Node) o).isEmpty()
          && getPriority().equals(((Node) o).getPriority());
    }
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public String toString() {
    return "<Empty Node>";
  }
}
