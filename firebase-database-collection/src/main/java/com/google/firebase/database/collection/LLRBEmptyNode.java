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

public class LLRBEmptyNode<K, V> implements LLRBNode<K, V> {

  private static final LLRBEmptyNode INSTANCE = new LLRBEmptyNode();

  @SuppressWarnings("unchecked")
  public static <K, V> LLRBEmptyNode<K, V> getInstance() {
    return INSTANCE;
  }

  private LLRBEmptyNode() {}

  @Override
  public LLRBNode<K, V> copy(
      K key, V value, Color color, LLRBNode<K, V> left, LLRBNode<K, V> right) {
    return this;
  }

  @Override
  public LLRBNode<K, V> insert(K key, V value, Comparator<K> comparator) {
    return new LLRBRedValueNode<K, V>(key, value);
  }

  @Override
  public LLRBNode<K, V> remove(K key, Comparator<K> comparator) {
    return this;
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public boolean isRed() {
    return false;
  }

  @Override
  public K getKey() {
    return null;
  }

  @Override
  public V getValue() {
    return null;
  }

  @Override
  public LLRBNode<K, V> getLeft() {
    return this;
  }

  @Override
  public LLRBNode<K, V> getRight() {
    return this;
  }

  @Override
  public LLRBNode<K, V> getMin() {
    return this;
  }

  @Override
  public LLRBNode<K, V> getMax() {
    return this;
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public void inOrderTraversal(NodeVisitor<K, V> visitor) {
    // No-op
  }

  @Override
  public boolean shortCircuitingInOrderTraversal(ShortCircuitingNodeVisitor<K, V> visitor) {
    // No-op
    return true;
  }

  @Override
  public boolean shortCircuitingReverseOrderTraversal(ShortCircuitingNodeVisitor<K, V> visitor) {
    // No-op
    return true;
  }
}
