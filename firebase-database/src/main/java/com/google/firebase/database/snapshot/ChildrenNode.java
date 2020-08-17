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

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.LLRBNode;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.utilities.Utilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ChildrenNode implements Node {

  public static Comparator<ChildKey> NAME_ONLY_COMPARATOR =
      new Comparator<ChildKey>() {
        @Override
        public int compare(ChildKey o1, ChildKey o2) {
          return o1.compareTo(o2);
        }
      };

  private final ImmutableSortedMap<ChildKey, Node> children;
  private final Node priority;

  private String lazyHash = null;

  private static class NamedNodeIterator implements Iterator<NamedNode> {

    private final Iterator<Map.Entry<ChildKey, Node>> iterator;

    public NamedNodeIterator(Iterator<Map.Entry<ChildKey, Node>> iterator) {
      this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public NamedNode next() {
      Map.Entry<ChildKey, Node> entry = iterator.next();
      return new NamedNode(entry.getKey(), entry.getValue());
    }

    @Override
    public void remove() {
      iterator.remove();
    }
  }

  /** */
  public abstract static class ChildVisitor extends LLRBNode.NodeVisitor<ChildKey, Node> {

    @Override
    public void visitEntry(ChildKey key, Node value) {
      visitChild(key, value);
    }

    public abstract void visitChild(ChildKey name, Node child);
  }

  protected ChildrenNode() {
    this.children = ImmutableSortedMap.Builder.emptyMap(NAME_ONLY_COMPARATOR);
    this.priority = PriorityUtilities.NullPriority();
  }

  protected ChildrenNode(ImmutableSortedMap<ChildKey, Node> children, Node priority) {
    if (children.isEmpty() && !priority.isEmpty()) {
      throw new IllegalArgumentException("Can't create empty ChildrenNode with priority!");
    }
    this.priority = priority;
    this.children = children;
  }

  @Override
  public boolean hasChild(ChildKey name) {
    return !this.getImmediateChild(name).isEmpty();
  }

  @Override
  public boolean isEmpty() {
    return children.isEmpty();
  }

  @Override
  public int getChildCount() {
    return children.size();
  }

  @Override
  public Object getValue() {
    return getValue(false);
  }

  @Override
  public Object getValue(boolean useExportFormat) {
    if (isEmpty()) {
      return null;
    }

    int numKeys = 0;
    int maxKey = 0;
    boolean allIntegerKeys = true;
    Map<String, Object> result = new HashMap<String, Object>();
    for (Map.Entry<ChildKey, Node> entry : children) {
      String key = entry.getKey().asString();
      result.put(key, entry.getValue().getValue(useExportFormat));
      numKeys++;
      // If we already found a string key, don't bother with any of this
      if (allIntegerKeys) {
        if (key.length() > 1 && key.charAt(0) == '0') {
          allIntegerKeys = false;
        } else {
          Integer keyAsInt = Utilities.tryParseInt(key);
          if (keyAsInt != null && keyAsInt >= 0) {
            if (keyAsInt > maxKey) {
              maxKey = keyAsInt;
            }
          } else {
            allIntegerKeys = false;
          }
        }
      }
    }

    if (!useExportFormat && allIntegerKeys && maxKey < 2 * numKeys) {
      // convert to an array
      List<Object> arrayResult = new ArrayList<Object>(maxKey + 1);
      for (int i = 0; i <= maxKey; ++i) {
        // Map.get will return null for non-existent values, so we don't have to worry about
        // filling them in manually
        arrayResult.add(result.get("" + i));
      }
      return arrayResult;
    } else {
      if (useExportFormat && !priority.isEmpty()) {
        result.put(".priority", priority.getValue());
      }
      return result;
    }
  }

  @Override
  public ChildKey getPredecessorChildKey(ChildKey childKey) {
    return this.children.getPredecessorKey(childKey);
  }

  @Override
  public ChildKey getSuccessorChildKey(ChildKey childKey) {
    return this.children.getSuccessorKey(childKey);
  }

  @Override
  public String getHashRepresentation(HashVersion version) {
    if (version != HashVersion.V1) {
      throw new IllegalArgumentException("Hashes on children nodes only supported for V1");
    }
    final StringBuilder toHash = new StringBuilder();
    if (!priority.isEmpty()) {
      toHash.append("priority:");
      toHash.append(priority.getHashRepresentation(HashVersion.V1));
      toHash.append(":");
    }
    List<NamedNode> nodes = new ArrayList<NamedNode>();
    boolean sawPriority = false;
    for (NamedNode node : this) {
      nodes.add(node);
      sawPriority = sawPriority || !node.getNode().getPriority().isEmpty();
    }
    if (sawPriority) {
      Collections.sort(nodes, PriorityIndex.getInstance());
    }
    for (NamedNode node : nodes) {
      String hashString = node.getNode().getHash();
      if (!hashString.equals("")) {
        toHash.append(":");
        toHash.append(node.getName().asString());
        toHash.append(":");
        toHash.append(hashString);
      }
    }
    return toHash.toString();
  }

  @Override
  public String getHash() {
    if (this.lazyHash == null) {
      String hashString = getHashRepresentation(HashVersion.V1);
      this.lazyHash = hashString.isEmpty() ? "" : Utilities.sha1HexDigest(hashString);
    }
    return this.lazyHash;
  }

  @Override
  public boolean isLeafNode() {
    return false;
  }

  @Override
  public Node getPriority() {
    return priority;
  }

  @Override
  public Node updatePriority(Node priority) {
    if (this.children.isEmpty()) {
      return EmptyNode.Empty();
    } else {
      return new ChildrenNode(this.children, priority);
    }
  }

  @Override
  public Node getImmediateChild(ChildKey name) {
    // Hack to treat priority as a regular child
    if (name.isPriorityChildName() && !this.priority.isEmpty()) {
      return this.priority;
    } else if (children.containsKey(name)) {
      return children.get(name);
    } else {
      return EmptyNode.Empty();
    }
  }

  @Override
  public Node getChild(Path path) {
    ChildKey front = path.getFront();
    if (front == null) {
      return this;
    } else {
      return getImmediateChild(front).getChild(path.popFront());
    }
  }

  public void forEachChild(final ChildVisitor visitor) {
    forEachChild(visitor, /*includePriority=*/ false);
  }

  public void forEachChild(final ChildVisitor visitor, boolean includePriority) {
    if (!includePriority || this.getPriority().isEmpty()) {
      children.inOrderTraversal(visitor);
    } else {
      children.inOrderTraversal(
          new LLRBNode.NodeVisitor<ChildKey, Node>() {
            boolean passedPriorityKey = false;

            @Override
            public void visitEntry(ChildKey key, Node value) {
              if (!passedPriorityKey && key.compareTo(ChildKey.getPriorityKey()) > 0) {
                passedPriorityKey = true;
                visitor.visitChild(ChildKey.getPriorityKey(), getPriority());
              }
              visitor.visitChild(key, value);
            }
          });
    }
  }

  public ChildKey getFirstChildKey() {
    return children.getMinKey();
  }

  public ChildKey getLastChildKey() {
    return children.getMaxKey();
  }

  @Override
  public Node updateChild(Path path, Node newChildNode) {
    ChildKey front = path.getFront();
    if (front == null) {
      return newChildNode;
    } else if (front.isPriorityChildName()) {
      hardAssert(PriorityUtilities.isValidPriority(newChildNode));
      return updatePriority(newChildNode);
    } else {
      Node newImmediateChild = getImmediateChild(front).updateChild(path.popFront(), newChildNode);
      return updateImmediateChild(front, newImmediateChild);
    }
  }

  @Override
  public Iterator<NamedNode> iterator() {
    return new NamedNodeIterator(children.iterator());
  }

  @Override
  public Iterator<NamedNode> reverseIterator() {
    return new NamedNodeIterator(children.reverseIterator());
  }

  @Override
  public Node updateImmediateChild(ChildKey key, Node newChildNode) {
    if (key.isPriorityChildName()) {
      return updatePriority(newChildNode);
    } else {
      ImmutableSortedMap<ChildKey, Node> newChildren = children;
      if (newChildren.containsKey(key)) {
        newChildren = newChildren.remove(key);
      }
      if (!newChildNode.isEmpty()) {
        newChildren = newChildren.insert(key, newChildNode);
      }
      if (newChildren.isEmpty()) {
        // Ignore priorities on empty nodes
        return EmptyNode.Empty();
      } else {
        return new ChildrenNode(newChildren, this.priority);
      }
    }
  }

  @Override
  public int compareTo(Node o) {
    if (this.isEmpty()) {
      if (o.isEmpty()) {
        return 0;
      } else {
        return -1;
      }
    } else if (o.isLeafNode()) {
      // Children nodes are greater than all leaf nodes
      return 1;
    } else if (o.isEmpty()) {
      return 1;
    } else if (o == Node.MAX_NODE) {
      return -1;
    } else {
      // Must be another Children node
      return 0;
    }
  }

  @Override
  public boolean equals(Object otherObj) {
    if (otherObj == null) {
      return false;
    }
    if (otherObj == this) {
      return true;
    }
    if (!(otherObj instanceof ChildrenNode)) {
      return false;
    }
    ChildrenNode other = (ChildrenNode) otherObj;
    if (!this.getPriority().equals(other.getPriority())) {
      return false;
    } else if (this.children.size() != other.children.size()) {
      return false;
    } else {
      Iterator<Map.Entry<ChildKey, Node>> thisIterator = this.children.iterator();
      Iterator<Map.Entry<ChildKey, Node>> otherIterator = other.children.iterator();
      while (thisIterator.hasNext() && otherIterator.hasNext()) {
        Map.Entry<ChildKey, Node> thisNameNode = thisIterator.next();
        Map.Entry<ChildKey, Node> otherNamedNode = otherIterator.next();
        if (!thisNameNode.getKey().equals(otherNamedNode.getKey())
            || !thisNameNode.getValue().equals(otherNamedNode.getValue())) {
          return false;
        }
      }
      if (thisIterator.hasNext() || otherIterator.hasNext()) {
        throw new IllegalStateException("Something went wrong internally.");
      }
      return true;
    }
  }

  @Override
  public int hashCode() {
    int hashCode = 0;
    for (NamedNode entry : this) {
      hashCode = 31 * hashCode + entry.getName().hashCode();
      hashCode = 17 * hashCode + entry.getNode().hashCode();
    }
    return hashCode;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    toString(builder, 0);
    return builder.toString();
  }

  private static void addIndentation(StringBuilder builder, int indentation) {
    for (int i = 0; i < indentation; i++) {
      builder.append(" ");
    }
  }

  private void toString(StringBuilder builder, int indentation) {
    if (this.children.isEmpty() && this.priority.isEmpty()) {
      builder.append("{ }");
    } else {
      builder.append("{\n");
      for (Map.Entry<ChildKey, Node> childEntry : this.children) {
        addIndentation(builder, indentation + 2);
        builder.append(childEntry.getKey().asString());
        builder.append("=");
        if (childEntry.getValue() instanceof ChildrenNode) {
          ChildrenNode childrenNode = (ChildrenNode) childEntry.getValue();
          childrenNode.toString(builder, indentation + 2);
        } else {
          builder.append(childEntry.getValue().toString());
        }
        builder.append("\n");
      }
      if (!this.priority.isEmpty()) {
        addIndentation(builder, indentation + 2);
        builder.append(".priority=");
        builder.append(this.priority.toString());
        builder.append("\n");
      }
      addIndentation(builder, indentation);
      builder.append("}");
    }
  }
}
