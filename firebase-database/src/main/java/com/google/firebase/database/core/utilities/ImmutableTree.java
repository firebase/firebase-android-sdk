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

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.StandardComparator;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.ChildKey;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class ImmutableTree<T> implements Iterable<Map.Entry<Path, T>> {

  private final T value;
  private final ImmutableSortedMap<ChildKey, ImmutableTree<T>> children;

  /** */
  public interface TreeVisitor<T, R> {
    public R onNodeValue(Path relativePath, T value, R accum);
  }

  private static final ImmutableSortedMap EMPTY_CHILDREN =
      ImmutableSortedMap.Builder.emptyMap(StandardComparator.getComparator(ChildKey.class));

  @SuppressWarnings("unchecked")
  private static final ImmutableTree EMPTY = new ImmutableTree<Object>(null, EMPTY_CHILDREN);

  @SuppressWarnings("unchecked")
  public static <V> ImmutableTree<V> emptyInstance() {
    return EMPTY;
  }

  public ImmutableTree(T value, ImmutableSortedMap<ChildKey, ImmutableTree<T>> children) {
    this.value = value;
    this.children = children;
  }

  @SuppressWarnings("unchecked")
  public ImmutableTree(T value) {
    this(value, EMPTY_CHILDREN);
  }

  public T getValue() {
    return this.value;
  }

  public ImmutableSortedMap<ChildKey, ImmutableTree<T>> getChildren() {
    return this.children;
  }

  public boolean isEmpty() {
    return this.value == null && this.children.isEmpty();
  }

  public Path findRootMostMatchingPath(Path relativePath, Predicate<? super T> predicate) {
    if (this.value != null && predicate.evaluate(this.value)) {
      return Path.getEmptyPath();
    } else {
      if (relativePath.isEmpty()) {
        return null;
      } else {
        ChildKey front = relativePath.getFront();
        ImmutableTree<T> child = this.children.get(front);
        if (child != null) {
          Path path = child.findRootMostMatchingPath(relativePath.popFront(), predicate);
          if (path != null) {
            // TODO: this seems inefficient
            return new Path(front).child(path);
          } else {
            return null;
          }
        } else {
          return null;
        }
      }
    }
  }

  public Path findRootMostPathWithValue(Path relativePath) {
    return findRootMostMatchingPath(relativePath, Predicate.TRUE);
  }

  public T rootMostValue(Path relativePath) {
    return rootMostValueMatching(relativePath, Predicate.TRUE);
  }

  public T rootMostValueMatching(Path relativePath, Predicate<? super T> predicate) {
    if (this.value != null && predicate.evaluate(this.value)) {
      return this.value;
    } else {
      ImmutableTree<T> currentTree = this;
      for (ChildKey key : relativePath) {
        currentTree = currentTree.children.get(key);
        if (currentTree == null) {
          return null;
        } else if (currentTree.value != null && predicate.evaluate(currentTree.value)) {
          return currentTree.value;
        }
      }
      return null;
    }
  }

  public T leafMostValue(Path relativePath) {
    return leafMostValueMatching(relativePath, Predicate.TRUE);
  }

  /**
   * Returns the deepest value found between the root and the specified path that matches the
   * predicate.
   *
   * @param path Path along which to look for matching values.
   * @param predicate The predicate to evaluate values against.
   * @return The deepest matching value, or null if no value matches.
   */
  public T leafMostValueMatching(Path path, Predicate<? super T> predicate) {
    T currentValue = (this.value != null && predicate.evaluate(this.value)) ? this.value : null;
    ImmutableTree<T> currentTree = this;
    for (ChildKey key : path) {
      currentTree = currentTree.children.get(key);
      if (currentTree == null) {
        return currentValue;
      } else {
        if (currentTree.value != null && predicate.evaluate(currentTree.value)) {
          currentValue = currentTree.value;
        }
      }
    }
    return currentValue;
  }

  public boolean containsMatchingValue(Predicate<? super T> predicate) {
    if (this.value != null && predicate.evaluate(this.value)) {
      return true;
    } else {
      for (Map.Entry<ChildKey, ImmutableTree<T>> subtree : this.children) {
        if (subtree.getValue().containsMatchingValue(predicate)) {
          return true;
        }
      }
      return false;
    }
  }

  public ImmutableTree<T> getChild(ChildKey child) {
    ImmutableTree<T> childTree = this.children.get(child);
    if (childTree != null) {
      return childTree;
    } else {
      return emptyInstance();
    }
  }

  public ImmutableTree<T> subtree(Path relativePath) {
    if (relativePath.isEmpty()) {
      return this;
    } else {
      ChildKey front = relativePath.getFront();
      ImmutableTree<T> childTree = this.children.get(front);
      if (childTree != null) {
        return childTree.subtree(relativePath.popFront());
      } else {
        return emptyInstance();
      }
    }
  }

  public ImmutableTree<T> set(Path relativePath, T value) {
    if (relativePath.isEmpty()) {
      return new ImmutableTree<T>(value, this.children);
    } else {
      ChildKey front = relativePath.getFront();
      ImmutableTree<T> child = this.children.get(front);
      if (child == null) {
        child = emptyInstance();
      }
      ImmutableTree<T> newChild = child.set(relativePath.popFront(), value);
      ImmutableSortedMap<ChildKey, ImmutableTree<T>> newChildren =
          this.children.insert(front, newChild);
      return new ImmutableTree<T>(this.value, newChildren);
    }
  }

  public ImmutableTree<T> remove(Path relativePath) {
    if (relativePath.isEmpty()) {
      if (this.children.isEmpty()) {
        return emptyInstance();
      } else {
        return new ImmutableTree<T>(null, this.children);
      }
    } else {
      ChildKey front = relativePath.getFront();
      ImmutableTree<T> child = this.children.get(front);
      if (child != null) {
        ImmutableTree<T> newChild = child.remove(relativePath.popFront());
        ImmutableSortedMap<ChildKey, ImmutableTree<T>> newChildren;
        if (newChild.isEmpty()) {
          newChildren = this.children.remove(front);
        } else {
          newChildren = this.children.insert(front, newChild);
        }
        if (this.value == null && newChildren.isEmpty()) {
          return emptyInstance();
        } else {
          return new ImmutableTree<T>(this.value, newChildren);
        }
      } else {
        return this;
      }
    }
  }

  public T get(Path relativePath) {
    if (relativePath.isEmpty()) {
      return this.value;
    } else {
      ChildKey front = relativePath.getFront();
      ImmutableTree<T> child = this.children.get(front);
      if (child != null) {
        return child.get(relativePath.popFront());
      } else {
        return null;
      }
    }
  }

  public ImmutableTree<T> setTree(Path relativePath, ImmutableTree<T> newTree) {
    if (relativePath.isEmpty()) {
      return newTree;
    } else {
      ChildKey front = relativePath.getFront();
      ImmutableTree<T> child = this.children.get(front);
      if (child == null) {
        child = emptyInstance();
      }
      ImmutableTree<T> newChild = child.setTree(relativePath.popFront(), newTree);
      ImmutableSortedMap<ChildKey, ImmutableTree<T>> newChildren;
      if (newChild.isEmpty()) {
        newChildren = this.children.remove(front);
      } else {
        newChildren = this.children.insert(front, newChild);
      }
      return new ImmutableTree<T>(this.value, newChildren);
    }
  }

  public void foreach(TreeVisitor<T, Void> visitor) {
    fold(Path.getEmptyPath(), visitor, null);
  }

  public <R> R fold(R accum, TreeVisitor<? super T, R> visitor) {
    return fold(Path.getEmptyPath(), visitor, accum);
  }

  private <R> R fold(Path relativePath, TreeVisitor<? super T, R> visitor, R accum) {
    for (Map.Entry<ChildKey, ImmutableTree<T>> subtree : this.children) {
      accum = subtree.getValue().fold(relativePath.child(subtree.getKey()), visitor, accum);
    }
    if (this.value != null) {
      accum = visitor.onNodeValue(relativePath, this.value, accum);
    }
    return accum;
  }

  public Collection<T> values() {
    final ArrayList<T> list = new ArrayList<T>();
    this.foreach(
        new TreeVisitor<T, Void>() {
          @Override
          public Void onNodeValue(Path relativePath, T value, Void accum) {
            list.add(value);
            return null;
          }
        });
    return list;
  }

  @Override
  public Iterator<Map.Entry<Path, T>> iterator() {
    // This could probably be done more efficient than prefilling a list, however, it's also a bit
    // tricky as we have to potentially scan all subtrees for a value that exists. Since iterators
    // are consumed fully in most cases, this should give a fairly efficient implementation in most
    // cases.
    final List<Map.Entry<Path, T>> list = new ArrayList<Map.Entry<Path, T>>();
    this.foreach(
        new TreeVisitor<T, Void>() {
          @Override
          public Void onNodeValue(Path relativePath, T value, Void accum) {
            list.add(new AbstractMap.SimpleImmutableEntry<Path, T>(relativePath, value));
            return null;
          }
        });
    return list.iterator();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("ImmutableTree { value=");
    builder.append(getValue());
    builder.append(", children={");
    for (Map.Entry<ChildKey, ImmutableTree<T>> child : children) {
      builder.append(child.getKey().asString());
      builder.append("=");
      builder.append(child.getValue());
    }
    builder.append("} }");
    return builder.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ImmutableTree that = (ImmutableTree) o;

    if (children != null ? !children.equals(that.children) : that.children != null) {
      return false;
    }
    if (value != null ? !value.equals(that.value) : that.value != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = value != null ? value.hashCode() : 0;
    result = 31 * result + (children != null ? children.hashCode() : 0);
    return result;
  }
}
