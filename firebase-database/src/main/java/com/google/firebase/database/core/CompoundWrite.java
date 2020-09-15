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

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.google.firebase.database.core.utilities.ImmutableTree;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class holds a collection of writes that can be applied to nodes in unison. It abstracts away
 * the logic with dealing with priority writes and multiple nested writes. At any given path there
 * is only allowed to be one write modifying that path. Any write to an existing path or shadowing
 * an existing path will modify that existing write to reflect the write added.
 */
public final class CompoundWrite implements Iterable<Map.Entry<Path, Node>> {
  private static final CompoundWrite EMPTY = new CompoundWrite(new ImmutableTree<Node>(null));

  private final ImmutableTree<Node> writeTree;

  private CompoundWrite(ImmutableTree<Node> writeTree) {
    this.writeTree = writeTree;
  }

  public static CompoundWrite emptyWrite() {
    return EMPTY;
  }

  public static CompoundWrite fromValue(Map<String, Object> merge) {
    ImmutableTree<Node> writeTree = ImmutableTree.emptyInstance();
    for (Map.Entry<String, Object> entry : merge.entrySet()) {
      ImmutableTree<Node> tree =
          new ImmutableTree<Node>(NodeUtilities.NodeFromJSON(entry.getValue()));
      writeTree = writeTree.setTree(new Path(entry.getKey()), tree);
    }
    return new CompoundWrite(writeTree);
  }

  public static CompoundWrite fromChildMerge(Map<ChildKey, Node> merge) {
    ImmutableTree<Node> writeTree = ImmutableTree.emptyInstance();
    for (Map.Entry<ChildKey, Node> entry : merge.entrySet()) {
      ImmutableTree<Node> tree = new ImmutableTree<Node>(entry.getValue());
      writeTree = writeTree.setTree(new Path(entry.getKey()), tree);
    }
    return new CompoundWrite(writeTree);
  }

  public static CompoundWrite fromPathMerge(Map<Path, Node> merge) {
    ImmutableTree<Node> writeTree = ImmutableTree.emptyInstance();
    for (Map.Entry<Path, Node> entry : merge.entrySet()) {
      ImmutableTree<Node> tree = new ImmutableTree<Node>(entry.getValue());
      writeTree = writeTree.setTree(entry.getKey(), tree);
    }
    return new CompoundWrite(writeTree);
  }

  public CompoundWrite addWrite(Path path, Node node) {
    if (path.isEmpty()) {
      return new CompoundWrite(new ImmutableTree<Node>(node));
    } else {
      Path rootMostPath = this.writeTree.findRootMostPathWithValue(path);
      if (rootMostPath != null) {
        Path relativePath = Path.getRelative(rootMostPath, path);
        Node value = this.writeTree.get(rootMostPath);
        ChildKey back = relativePath.getBack();
        if (back != null
            && back.isPriorityChildName()
            && value.getChild(relativePath.getParent()).isEmpty()) {
          // Ignore priority updates on empty nodes
          return this;
        } else {
          value = value.updateChild(relativePath, node);
          return new CompoundWrite(this.writeTree.set(rootMostPath, value));
        }
      } else {
        ImmutableTree<Node> subtree = new ImmutableTree<Node>(node);
        ImmutableTree<Node> newWriteTree = this.writeTree.setTree(path, subtree);
        return new CompoundWrite(newWriteTree);
      }
    }
  }

  public CompoundWrite addWrite(ChildKey key, Node node) {
    return addWrite(new Path(key), node);
  }

  public CompoundWrite addWrites(final Path path, CompoundWrite updates) {
    return updates.writeTree.fold(
        this,
        new ImmutableTree.TreeVisitor<Node, CompoundWrite>() {
          @Override
          public CompoundWrite onNodeValue(Path relativePath, Node value, CompoundWrite accum) {
            return accum.addWrite(path.child(relativePath), value);
          }
        });
  }

  /**
   * Will remove a write at the given path and deeper paths. This will <em>not</em> modify a write
   * at a higher location, which must be removed by calling this method with that path.
   *
   * @param path The path at which a write and all deeper writes should be removed
   * @return The new WriteCompound with the removed path
   */
  public CompoundWrite removeWrite(Path path) {
    if (path.isEmpty()) {
      return EMPTY;
    } else {
      ImmutableTree<Node> newWriteTree =
          writeTree.setTree(path, ImmutableTree.<Node>emptyInstance());
      return new CompoundWrite(newWriteTree);
    }
  }

  /**
   * Returns whether this CompoundWrite will fully overwrite a node at a given location and can
   * therefore be considered "complete".
   *
   * @param path The path to check for
   * @return Whether there is a complete write at that path
   */
  public boolean hasCompleteWrite(Path path) {
    return getCompleteNode(path) != null;
  }

  public Node rootWrite() {
    return this.writeTree.getValue();
  }

  /**
   * Returns a node for a path if and only if the node is a "complete" overwrite at that path. This
   * will not aggregate writes from deeper paths, but will return child nodes from a more shallow
   * path.
   *
   * @param path The path to get a complete write
   * @return The node if complete at that path, or null otherwise.
   */
  public Node getCompleteNode(Path path) {
    Path rootMost = this.writeTree.findRootMostPathWithValue(path);
    if (rootMost != null) {
      return this.writeTree.get(rootMost).getChild(Path.getRelative(rootMost, path));
    } else {
      return null;
    }
  }

  /**
   * Returns all children that are guaranteed to be a complete overwrite.
   *
   * @return A list of all complete children.
   */
  public List<NamedNode> getCompleteChildren() {
    List<NamedNode> children = new ArrayList<NamedNode>();
    if (this.writeTree.getValue() != null) {
      for (NamedNode entry : this.writeTree.getValue()) {
        children.add(new NamedNode(entry.getName(), entry.getNode()));
      }
    } else {
      for (Map.Entry<ChildKey, ImmutableTree<Node>> entry : this.writeTree.getChildren()) {
        ImmutableTree<Node> childTree = entry.getValue();
        if (childTree.getValue() != null) {
          children.add(new NamedNode(entry.getKey(), childTree.getValue()));
        }
      }
    }
    return children;
  }

  public CompoundWrite childCompoundWrite(Path path) {
    if (path.isEmpty()) {
      return this;
    } else {
      Node shadowingNode = this.getCompleteNode(path);
      if (shadowingNode != null) {
        return new CompoundWrite(new ImmutableTree<Node>(shadowingNode));
      } else {
        // let the constructor extract the priority update
        return new CompoundWrite(this.writeTree.subtree(path));
      }
    }
  }

  public Map<ChildKey, CompoundWrite> childCompoundWrites() {
    Map<ChildKey, CompoundWrite> children = new HashMap<ChildKey, CompoundWrite>();
    for (Map.Entry<ChildKey, ImmutableTree<Node>> entries : this.writeTree.getChildren()) {
      children.put(entries.getKey(), new CompoundWrite(entries.getValue()));
    }
    return children;
  }

  /**
   * Returns true if this CompoundWrite is empty and therefore does not modify any nodes.
   *
   * @return Whether this CompoundWrite is empty
   */
  public boolean isEmpty() {
    return this.writeTree.isEmpty();
  }

  private Node applySubtreeWrite(Path relativePath, ImmutableTree<Node> writeTree, Node node) {
    if (writeTree.getValue() != null) {
      // Since there a write is always a leaf, we're done here
      return node.updateChild(relativePath, writeTree.getValue());
    } else {
      Node priorityWrite = null;
      for (Map.Entry<ChildKey, ImmutableTree<Node>> childTreeEntry : writeTree.getChildren()) {
        ImmutableTree<Node> childTree = childTreeEntry.getValue();
        ChildKey childKey = childTreeEntry.getKey();
        if (childKey.isPriorityChildName()) {
          // Apply priorities at the end so we don't update priorities for either empty nodes or
          // forget to apply priorities to empty nodes that are later filled
          hardAssert(childTree.getValue() != null, "Priority writes must always be leaf nodes");
          priorityWrite = childTree.getValue();
        } else {
          node = applySubtreeWrite(relativePath.child(childKey), childTree, node);
        }
      }
      // If there was a priority write, we only apply it if the node is not empty
      if (!node.getChild(relativePath).isEmpty() && priorityWrite != null) {
        node = node.updateChild(relativePath.child(ChildKey.getPriorityKey()), priorityWrite);
      }
      return node;
    }
  }

  /**
   * Applies this CompoundWrite to a node. The node is returned with all writes from this
   * CompoundWrite applied to the node
   *
   * @param node The node to apply this CompoundWrite to
   * @return The node with all writes applied
   */
  public Node apply(Node node) {
    return applySubtreeWrite(Path.getEmptyPath(), this.writeTree, node);
  }

  /**
   * Returns a serializable version of this CompoundWrite
   *
   * @param exportFormat Nodes to write are saved in their export format
   * @return The map representing this CompoundWrite
   */
  public Map<String, Object> getValue(final boolean exportFormat) {
    final Map<String, Object> writes = new HashMap<String, Object>();
    this.writeTree.foreach(
        new ImmutableTree.TreeVisitor<Node, Void>() {
          @Override
          public Void onNodeValue(Path relativePath, Node value, Void accum) {
            writes.put(relativePath.wireFormat(), value.getValue(exportFormat));
            return null;
          }
        });
    return writes;
  }

  @Override
  public Iterator<Map.Entry<Path, Node>> iterator() {
    return this.writeTree.iterator();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o == null || o.getClass() != this.getClass()) {
      return false;
    }

    return ((CompoundWrite) o).getValue(true).equals(this.getValue(true));
  }

  @Override
  public int hashCode() {
    return this.getValue(true).hashCode();
  }

  @Override
  public String toString() {
    return "CompoundWrite{" + this.getValue(true).toString() + "}";
  }
}
