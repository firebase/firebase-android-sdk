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

package com.google.firebase.database.core.persistence;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.utilities.ImmutableTree;
import com.google.firebase.database.core.utilities.Predicate;
import com.google.firebase.database.snapshot.ChildKey;
import java.util.Set;

/**
 * Forest of "prune trees" where a prune tree is a location that can be pruned with a tree of
 * descendants that must be excluded from the pruning.
 *
 * <p>Internally we store this as a single tree of booleans with the following characteristics: *
 * 'true' indicates a location that can be pruned, possibly with some excluded descendants. *
 * 'false' indicates a location that we should keep (i.e. exclude from pruning). * 'true' (prune)
 * cannot be a descendant of 'false' (keep). This will trigger an exception. * 'true' cannot be a
 * descendant of 'true' (we'll just keep the more shallow 'true'). * 'false' cannot be a descendant
 * of 'false' (we'll just keep the more shallow 'false').
 */
public class PruneForest {
  private final ImmutableTree<Boolean> pruneForest;

  private static final Predicate<Boolean> KEEP_PREDICATE =
      new Predicate<Boolean>() {
        @Override
        public boolean evaluate(Boolean prune) {
          return !prune;
        }
      };

  private static final Predicate<Boolean> PRUNE_PREDICATE =
      new Predicate<Boolean>() {
        @Override
        public boolean evaluate(Boolean prune) {
          return prune;
        }
      };

  private static final ImmutableTree<Boolean> PRUNE_TREE = new ImmutableTree<Boolean>(true);
  private static final ImmutableTree<Boolean> KEEP_TREE = new ImmutableTree<Boolean>(false);

  public PruneForest() {
    this.pruneForest = ImmutableTree.emptyInstance();
  }

  private PruneForest(ImmutableTree<Boolean> pruneForest) {
    this.pruneForest = pruneForest;
  }

  public boolean prunesAnything() {
    return this.pruneForest.containsMatchingValue(PRUNE_PREDICATE);
  }

  /**
   * Indicates that path is marked for pruning, so anything below it that didn't have keep() called
   * on it should be pruned.
   *
   * @param path The path in question
   * @return True if we should prune descendants that didn't have keep() called on them.
   */
  public boolean shouldPruneUnkeptDescendants(Path path) {
    Boolean shouldPrune = this.pruneForest.leafMostValue(path);
    return shouldPrune != null && shouldPrune;
  }

  public boolean shouldKeep(Path path) {
    Boolean shouldPrune = this.pruneForest.leafMostValue(path);
    return shouldPrune != null && !shouldPrune;
  }

  public boolean affectsPath(Path path) {
    return this.pruneForest.rootMostValue(path) != null
        || !this.pruneForest.subtree(path).isEmpty();
  }

  public PruneForest child(ChildKey key) {
    ImmutableTree<Boolean> childPruneTree = this.pruneForest.getChild(key);
    if (childPruneTree == null) {
      childPruneTree = new ImmutableTree<Boolean>(this.pruneForest.getValue());
    } else {
      if (childPruneTree.getValue() == null && this.pruneForest.getValue() != null) {
        childPruneTree = childPruneTree.set(Path.getEmptyPath(), this.pruneForest.getValue());
      }
    }
    return new PruneForest(childPruneTree);
  }

  public PruneForest child(Path path) {
    if (path.isEmpty()) {
      return this;
    } else {
      return this.child(path.getFront()).child(path.popFront());
    }
  }

  public <T> T foldKeptNodes(T startValue, final ImmutableTree.TreeVisitor<Void, T> treeVisitor) {
    return this.pruneForest.fold(
        startValue,
        new ImmutableTree.TreeVisitor<Boolean, T>() {
          @Override
          public T onNodeValue(Path relativePath, Boolean prune, T accum) {
            if (!prune) {
              return treeVisitor.onNodeValue(relativePath, null, accum);
            } else {
              return accum;
            }
          }
        });
  }

  public PruneForest prune(Path path) {
    if (this.pruneForest.rootMostValueMatching(path, KEEP_PREDICATE) != null) {
      throw new IllegalArgumentException("Can't prune path that was kept previously!");
    }
    if (this.pruneForest.rootMostValueMatching(path, PRUNE_PREDICATE) != null) {
      // This path will already be pruned
      return this;
    } else {
      ImmutableTree<Boolean> newPruneTree = this.pruneForest.setTree(path, PRUNE_TREE);
      return new PruneForest(newPruneTree);
    }
  }

  public PruneForest keep(Path path) {
    if (this.pruneForest.rootMostValueMatching(path, KEEP_PREDICATE) != null) {
      // This path will already be kept
      return this;
    } else {
      ImmutableTree<Boolean> newPruneTree = this.pruneForest.setTree(path, KEEP_TREE);
      return new PruneForest(newPruneTree);
    }
  }

  public PruneForest keepAll(Path path, Set<ChildKey> children) {
    if (this.pruneForest.rootMostValueMatching(path, KEEP_PREDICATE) != null) {
      // This path will already be kept
      return this;
    } else {
      return doAll(path, children, KEEP_TREE);
    }
  }

  public PruneForest pruneAll(Path path, Set<ChildKey> children) {
    if (this.pruneForest.rootMostValueMatching(path, KEEP_PREDICATE) != null) {
      throw new IllegalArgumentException("Can't prune path that was kept previously!");
    }

    if (this.pruneForest.rootMostValueMatching(path, PRUNE_PREDICATE) != null) {
      // This path will already be kept
      return this;
    } else {
      return doAll(path, children, PRUNE_TREE);
    }
  }

  private PruneForest doAll(
      Path path, Set<ChildKey> children, ImmutableTree<Boolean> keepOrPruneTree) {
    ImmutableTree<Boolean> subtree = this.pruneForest.subtree(path);
    ImmutableSortedMap<ChildKey, ImmutableTree<Boolean>> childrenMap = subtree.getChildren();
    for (ChildKey key : children) {
      childrenMap = childrenMap.insert(key, keepOrPruneTree);
    }
    return new PruneForest(
        this.pruneForest.setTree(
            path, new ImmutableTree<Boolean>(subtree.getValue(), childrenMap)));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PruneForest)) {
      return false;
    }

    PruneForest that = (PruneForest) o;

    if (!pruneForest.equals(that.pruneForest)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return pruneForest.hashCode();
  }

  @Override
  public String toString() {
    return "{PruneForest:" + pruneForest.toString() + "}";
  }
}
