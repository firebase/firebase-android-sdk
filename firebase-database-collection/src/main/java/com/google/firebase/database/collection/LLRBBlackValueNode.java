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

public class LLRBBlackValueNode<K, V> extends LLRBValueNode<K, V> {

  /**
   * Only memoize size on black nodes, not on red nodes. This saves memory while guaranteeing that
   * size will still have an amortized constant runtime. The first time size() may have to traverse
   * the entire tree. However, the red black tree algorithm guarantees that every red node has two
   * black children. So future invocations of the size() function will have to go at most 2 levels
   * deep if the child is a red node.
   *
   * <p>Needs to be mutable because left node can be updated via setLeft.
   */
  private int size = -1;

  LLRBBlackValueNode(K key, V value, LLRBNode<K, V> left, LLRBNode<K, V> right) {
    super(key, value, left, right);
  }

  @Override
  protected Color getColor() {
    return Color.BLACK;
  }

  @Override
  public boolean isRed() {
    return false;
  }

  @Override
  public int size() {
    if (size == -1) {
      size = getLeft().size() + 1 + getRight().size();
    }
    return size;
  }

  @Override
  void setLeft(LLRBNode<K, V> left) {
    if (size != -1) {
      // Modifying left node after invoking size
      throw new IllegalStateException("Can't set left after using size");
    }
    super.setLeft(left);
  }

  @Override
  protected LLRBValueNode<K, V> copy(K key, V value, LLRBNode<K, V> left, LLRBNode<K, V> right) {
    K newKey = key == null ? this.getKey() : key;
    V newValue = value == null ? this.getValue() : value;
    LLRBNode<K, V> newLeft = left == null ? this.getLeft() : left;
    LLRBNode<K, V> newRight = right == null ? this.getRight() : right;
    return new LLRBBlackValueNode<K, V>(newKey, newValue, newLeft, newRight);
  }
}
