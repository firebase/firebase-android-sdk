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

package com.google.firebase.database.collection;

import java.util.Comparator;

public abstract class LLRBValueNode<K, V> implements LLRBNode<K, V> {

  private static Color oppositeColor(LLRBNode node) {
    return node.isRed() ? Color.BLACK : Color.RED;
  }

  private final K key;
  private final V value;
  private LLRBNode<K, V> left;
  private final LLRBNode<K, V> right;

  LLRBValueNode(K key, V value, LLRBNode<K, V> left, LLRBNode<K, V> right) {
    this.key = key;
    this.value = value;
    this.left = left == null ? LLRBEmptyNode.<K, V>getInstance() : left;
    this.right = right == null ? LLRBEmptyNode.<K, V>getInstance() : right;
  }

  @Override
  public LLRBNode<K, V> getLeft() {
    return left;
  }

  @Override
  public LLRBNode<K, V> getRight() {
    return right;
  }

  @Override
  public K getKey() {
    return key;
  }

  @Override
  public V getValue() {
    return value;
  }

  protected abstract Color getColor();

  protected abstract LLRBValueNode<K, V> copy(
      K key, V value, LLRBNode<K, V> left, LLRBNode<K, V> right);

  @Override
  public LLRBValueNode<K, V> copy(
      K key, V value, Color color, LLRBNode<K, V> left, LLRBNode<K, V> right) {
    K newKey = key == null ? this.key : key;
    V newValue = value == null ? this.value : value;
    LLRBNode<K, V> newLeft = left == null ? this.left : left;
    LLRBNode<K, V> newRight = right == null ? this.right : right;
    if (color == Color.RED) {
      return new LLRBRedValueNode<>(newKey, newValue, newLeft, newRight);
    } else {
      return new LLRBBlackValueNode<>(newKey, newValue, newLeft, newRight);
    }
  }

  @Override
  public LLRBNode<K, V> insert(K key, V value, Comparator<K> comparator) {
    int cmp = comparator.compare(key, this.key);
    LLRBValueNode<K, V> n;
    if (cmp < 0) {
      // new key is less than current key
      LLRBNode<K, V> newLeft = this.left.insert(key, value, comparator);
      n = copy(null, null, newLeft, null);
    } else if (cmp == 0) {
      // same key
      n = copy(key, value, null, null);
    } else {
      // new key is greater than current key
      LLRBNode<K, V> newRight = this.right.insert(key, value, comparator);
      n = copy(null, null, null, newRight);
    }
    return n.fixUp();
  }

  @Override
  public LLRBNode<K, V> remove(K key, Comparator<K> comparator) {
    LLRBValueNode<K, V> n = this;

    if (comparator.compare(key, n.key) < 0) {
      if (!n.left.isEmpty() && !n.left.isRed() && !((LLRBValueNode<K, V>) n.left).left.isRed()) {
        n = n.moveRedLeft();
      }
      n = n.copy(null, null, n.left.remove(key, comparator), null);
    } else {
      if (n.left.isRed()) {
        n = n.rotateRight();
      }

      if (!n.right.isEmpty() && !n.right.isRed() && !((LLRBValueNode<K, V>) n.right).left.isRed()) {
        n = n.moveRedRight();
      }

      if (comparator.compare(key, n.key) == 0) {
        if (n.right.isEmpty()) {
          return LLRBEmptyNode.getInstance();
        } else {
          LLRBNode<K, V> smallest = n.right.getMin();
          n =
              n.copy(
                  smallest.getKey(),
                  smallest.getValue(),
                  null,
                  ((LLRBValueNode<K, V>) n.right).removeMin());
        }
      }
      n = n.copy(null, null, null, n.right.remove(key, comparator));
    }
    return n.fixUp();
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public LLRBNode<K, V> getMin() {
    if (left.isEmpty()) {
      return this;
    } else {
      return left.getMin();
    }
  }

  @Override
  public LLRBNode<K, V> getMax() {
    if (right.isEmpty()) {
      return this;
    } else {
      return right.getMax();
    }
  }

  @Override
  public void inOrderTraversal(NodeVisitor<K, V> visitor) {
    left.inOrderTraversal(visitor);
    visitor.visitEntry(key, value);
    right.inOrderTraversal(visitor);
  }

  @Override
  public boolean shortCircuitingInOrderTraversal(ShortCircuitingNodeVisitor<K, V> visitor) {
    if (left.shortCircuitingInOrderTraversal(visitor)) {
      if (visitor.shouldContinue(key, value)) {
        return right.shortCircuitingInOrderTraversal(visitor);
      }
    }
    return false;
  }

  @Override
  public boolean shortCircuitingReverseOrderTraversal(ShortCircuitingNodeVisitor<K, V> visitor) {
    if (right.shortCircuitingReverseOrderTraversal(visitor)) {
      if (visitor.shouldContinue(key, value)) {
        return left.shortCircuitingReverseOrderTraversal(visitor);
      }
    }
    return false;
  }

  // For use by the builder, which is package local
  void setLeft(LLRBNode<K, V> left) {
    this.left = left;
  }

  private LLRBNode<K, V> removeMin() {
    if (left.isEmpty()) {
      return LLRBEmptyNode.getInstance();
    } else {
      LLRBValueNode<K, V> n = this;
      if (!n.getLeft().isRed() && !n.getLeft().getLeft().isRed()) {
        n = n.moveRedLeft();
      }

      n = n.copy(null, null, ((LLRBValueNode<K, V>) n.left).removeMin(), null);
      return n.fixUp();
    }
  }

  private LLRBValueNode<K, V> moveRedLeft() {
    LLRBValueNode<K, V> n = colorFlip();
    if (n.getRight().getLeft().isRed()) {
      n = n.copy(null, null, null, ((LLRBValueNode<K, V>) n.getRight()).rotateRight());
      n = n.rotateLeft();
      n = n.colorFlip();
    }
    return n;
  }

  private LLRBValueNode<K, V> moveRedRight() {
    LLRBValueNode<K, V> n = colorFlip();
    if (n.getLeft().getLeft().isRed()) {
      n = n.rotateRight();
      n = n.colorFlip();
    }
    return n;
  }

  private LLRBValueNode<K, V> fixUp() {
    LLRBValueNode<K, V> n = this;
    if (n.right.isRed() && !n.left.isRed()) {
      n = n.rotateLeft();
    }
    if (n.left.isRed() && ((LLRBValueNode<K, V>) n.left).left.isRed()) {
      n = n.rotateRight();
    }
    if (n.left.isRed() && n.right.isRed()) {
      n = n.colorFlip();
    }
    return n;
  }

  private LLRBValueNode<K, V> rotateLeft() {
    LLRBValueNode<K, V> newLeft =
        this.copy(null, null, Color.RED, null, ((LLRBValueNode<K, V>) this.right).left);
    return (LLRBValueNode<K, V>) this.right.copy(null, null, this.getColor(), newLeft, null);
  }

  private LLRBValueNode<K, V> rotateRight() {
    LLRBValueNode<K, V> newRight =
        this.copy(null, null, Color.RED, ((LLRBValueNode<K, V>) this.left).right, null);
    return (LLRBValueNode<K, V>) this.left.copy(null, null, this.getColor(), null, newRight);
  }

  private LLRBValueNode<K, V> colorFlip() {
    LLRBNode<K, V> newLeft = this.left.copy(null, null, oppositeColor(this.left), null, null);
    LLRBNode<K, V> newRight = this.right.copy(null, null, oppositeColor(this.right), null, null);

    return this.copy(null, null, oppositeColor(this), newLeft, newRight);
  }
}
