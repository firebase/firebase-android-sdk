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

public interface LLRBNode<K, V> {

  public interface ShortCircuitingNodeVisitor<K, V> {
    boolean shouldContinue(K key, V value);
  }

  public abstract class NodeVisitor<K, V> implements ShortCircuitingNodeVisitor<K, V> {

    @Override
    public boolean shouldContinue(K key, V value) {
      visitEntry(key, value);
      return true;
    }

    public abstract void visitEntry(K key, V value);
  }

  enum Color {
    RED,
    BLACK
  }

  LLRBNode<K, V> copy(K key, V value, Color color, LLRBNode<K, V> left, LLRBNode<K, V> right);

  LLRBNode<K, V> insert(K key, V value, Comparator<K> comparator);

  LLRBNode<K, V> remove(K key, Comparator<K> comparator);

  boolean isEmpty();

  boolean isRed();

  K getKey();

  V getValue();

  LLRBNode<K, V> getLeft();

  LLRBNode<K, V> getRight();

  LLRBNode<K, V> getMin();

  LLRBNode<K, V> getMax();

  int size();

  void inOrderTraversal(NodeVisitor<K, V> visitor);

  boolean shortCircuitingInOrderTraversal(ShortCircuitingNodeVisitor<K, V> visitor);

  boolean shortCircuitingReverseOrderTraversal(ShortCircuitingNodeVisitor<K, V> visitor);
}
