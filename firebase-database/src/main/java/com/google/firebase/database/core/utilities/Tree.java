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

import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.ChildKey;
import java.util.Map;

public class Tree<T> {
  /** */
  public interface TreeVisitor<T> {
    public void visitTree(Tree<T> tree);
  }

  /** */
  public interface TreeFilter<T> {
    public boolean filterTreeNode(Tree<T> tree);
  }

  private ChildKey name;
  private Tree<T> parent;
  private TreeNode<T> node;

  public Tree(ChildKey name, Tree<T> parent, TreeNode<T> node) {
    this.name = name;
    this.parent = parent;
    this.node = node;
  }

  public Tree() {
    this(null, null, new TreeNode<T>());
  }

  public TreeNode<T> lastNodeOnPath(Path path) {
    TreeNode<T> current = this.node;
    ChildKey next = path.getFront();
    while (next != null) {
      TreeNode<T> childNode =
          current.children.containsKey(next) ? current.children.get(next) : null;
      if (childNode == null) {
        return current;
      }
      current = childNode;
      path = path.popFront();
      next = path.getFront();
    }
    return current;
  }

  public Tree<T> subTree(Path path) {
    Tree<T> child = this;
    ChildKey next = path.getFront();
    while (next != null) {
      TreeNode<T> childNode =
          child.node.children.containsKey(next) ? child.node.children.get(next) : new TreeNode<T>();
      child = new Tree<T>(next, child, childNode);
      path = path.popFront();
      next = path.getFront();
    }
    return child;
  }

  public T getValue() {
    return node.value;
  }

  public void setValue(T value) {
    node.value = value;
    updateParents();
  }

  public Tree<T> getParent() {
    return parent;
  }

  public ChildKey getName() {
    return name;
  }

  public Path getPath() {
    if (parent != null) {
      hardAssert(name != null);
      return parent.getPath().child(name);
    } else {
      return (name != null) ? new Path(name) : Path.getEmptyPath();
    }
  }

  public boolean hasChildren() {
    return !node.children.isEmpty();
  }

  public boolean isEmpty() {
    return node.value == null && node.children.isEmpty();
  }

  public void forEachDescendant(TreeVisitor<T> visitor) {
    forEachDescendant(visitor, false, false);
  }

  public void forEachDescendant(TreeVisitor<T> visitor, boolean includeSelf) {
    forEachDescendant(visitor, includeSelf, false);
  }

  public void forEachDescendant(
      final TreeVisitor<T> visitor, boolean includeSelf, final boolean childrenFirst) {
    if (includeSelf && !childrenFirst) {
      visitor.visitTree(this);
    }

    forEachChild(
        new TreeVisitor<T>() {
          @Override
          public void visitTree(Tree<T> tree) {
            tree.forEachDescendant(visitor, true, childrenFirst);
          }
        });

    if (includeSelf && childrenFirst) {
      visitor.visitTree(this);
    }
  }

  public boolean forEachAncestor(TreeFilter<T> filter) {
    return forEachAncestor(filter, false);
  }

  public boolean forEachAncestor(TreeFilter<T> filter, boolean includeSelf) {
    Tree<T> tree = includeSelf ? this : this.parent;
    while (tree != null) {
      if (filter.filterTreeNode(tree)) {
        return true;
      }
      tree = tree.parent;
    }
    return false;
  }

  public void forEachChild(TreeVisitor<T> visitor) {
    // Decouple from actual tree so we can avoid ConcurrentModification exceptions
    Object[] entries = node.children.entrySet().toArray();
    for (int i = 0; i < entries.length; ++i) {
      @SuppressWarnings("unchecked")
      Map.Entry<ChildKey, TreeNode<T>> entry = (Map.Entry<ChildKey, TreeNode<T>>) entries[i];
      Tree<T> subTree = new Tree<T>(entry.getKey(), this, entry.getValue());
      visitor.visitTree(subTree);
    }
  }

  private void updateParents() {
    if (parent != null) {
      parent.updateChild(name, this);
    }
  }

  private void updateChild(ChildKey name, Tree<T> child) {
    boolean childEmpty = child.isEmpty();
    boolean childExists = node.children.containsKey(name);
    if (childEmpty && childExists) {
      node.children.remove(name);
      updateParents();
    } else if (!childEmpty && !childExists) {
      node.children.put(name, child.node);
      updateParents();
    }
  }

  @Override
  public String toString() {
    return toString("");
  }

  String toString(String prefix) {
    String nodeName = name == null ? "<anon>" : name.asString();
    return prefix + nodeName + "\n" + node.toString(prefix + "\t");
  }
}
