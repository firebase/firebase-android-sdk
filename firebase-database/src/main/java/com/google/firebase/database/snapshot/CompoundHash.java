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

import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.utilities.NodeSizeEstimator;
import com.google.firebase.database.core.utilities.Utilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class CompoundHash {
  private final List<Path> posts;
  private final List<String> hashes;

  private CompoundHash(List<Path> posts, List<String> hashes) {
    if (posts.size() != hashes.size() - 1) {
      throw new IllegalArgumentException(
          "Number of posts need to be n-1 for n hashes in CompoundHash");
    }
    this.posts = posts;
    this.hashes = hashes;
  }

  public List<Path> getPosts() {
    return Collections.unmodifiableList(this.posts);
  }

  public List<String> getHashes() {
    return Collections.unmodifiableList(this.hashes);
  }

  /** */
  public static interface SplitStrategy {
    public boolean shouldSplit(CompoundHashBuilder state);
  }

  private static class SimpleSizeSplitStrategy implements SplitStrategy {

    private final long splitThreshold;

    public SimpleSizeSplitStrategy(Node node) {
      long estimatedNodeSize = NodeSizeEstimator.estimateSerializedNodeSize(node);
      // Splits for
      // 1k -> 512 (2 parts)
      // 5k -> 715 (7 parts)
      // 100k -> 3.2k (32 parts)
      // 500k -> 7k (71 parts)
      // 5M -> 23k (228 parts)
      this.splitThreshold = Math.max(512, (long) Math.sqrt(estimatedNodeSize * 100));
    }

    @Override
    public boolean shouldSplit(CompoundHashBuilder state) {
      // Never split on priorities
      return state.currentHashLength() > this.splitThreshold
          && (state.currentPath().isEmpty()
              || !state.currentPath().getBack().equals(ChildKey.getPriorityKey()));
    }
  }

  static class CompoundHashBuilder {
    // NOTE: We use the existence of this to know if we've started building a range (i.e.
    // encountered a leaf node).
    private StringBuilder optHashValueBuilder = null;

    // The current path as a stack. This is used in combination with currentPathDepth to
    // simultaneously store the last leaf node path. The depth is changed when descending and
    // ascending, at the same time the current key is set for the current depth. Because the keys
    // are left unchanged for ascending the path will also contain the path of the last visited leaf
    // node (using lastLeafDepth elements)
    @SuppressWarnings("JdkObsolete")
    private Stack<ChildKey> currentPath = new Stack<ChildKey>();

    private int lastLeafDepth = -1;
    private int currentPathDepth;

    private boolean needsComma = true;

    private final List<Path> currentPaths = new ArrayList<Path>();
    private final List<String> currentHashes = new ArrayList<String>();
    private final SplitStrategy splitStrategy;

    public CompoundHashBuilder(SplitStrategy strategy) {
      this.splitStrategy = strategy;
    }

    public boolean buildingRange() {
      return this.optHashValueBuilder != null;
    }

    public int currentHashLength() {
      return this.optHashValueBuilder.length();
    }

    public Path currentPath() {
      return this.currentPath(this.currentPathDepth);
    }

    private Path currentPath(int depth) {
      ChildKey[] segments = new ChildKey[depth];
      for (int i = 0; i < depth; i++) {
        segments[i] = currentPath.get(i);
      }
      return new Path(segments);
    }

    private void ensureRange() {
      if (!buildingRange()) {
        optHashValueBuilder = new StringBuilder();
        optHashValueBuilder.append("(");
        for (ChildKey key : currentPath(currentPathDepth)) {
          appendKey(optHashValueBuilder, key);
          optHashValueBuilder.append(":(");
        }
        needsComma = false;
      }
    }

    private void appendKey(StringBuilder builder, ChildKey key) {
      builder.append(Utilities.stringHashV2Representation(key.asString()));
    }

    private void processLeaf(LeafNode<?> node) {
      ensureRange();

      lastLeafDepth = currentPathDepth;
      optHashValueBuilder.append(node.getHashRepresentation(Node.HashVersion.V2));
      needsComma = true;
      if (splitStrategy.shouldSplit(this)) {
        endRange();
      }
    }

    private void startChild(ChildKey key) {
      ensureRange();

      if (needsComma) {
        optHashValueBuilder.append(",");
      }
      appendKey(optHashValueBuilder, key);
      optHashValueBuilder.append(":(");

      if (currentPathDepth == currentPath.size()) {
        currentPath.add(key);
      } else {
        currentPath.set(currentPathDepth, key);
      }
      currentPathDepth++;
      needsComma = false;
    }

    private void endChild() {
      currentPathDepth--;
      if (buildingRange()) {
        optHashValueBuilder.append(")");
      }
      needsComma = true;
    }

    private void finishHashing() {
      hardAssert(currentPathDepth == 0, "Can't finish hashing in the middle processing a child");
      if (buildingRange()) {
        endRange(); // Finish final range
      }
      // Always close with the empty hash for the remaining range to allow simple appending
      currentHashes.add("");
    }

    private void endRange() {
      hardAssert(buildingRange(), "Can't end range without starting a range!");
      // Add closing parenthesis for current depth
      for (int i = 0; i < currentPathDepth; i++) {
        optHashValueBuilder.append(")");
      }
      optHashValueBuilder.append(")");

      Path lastLeafPath = currentPath(lastLeafDepth);
      String hash = Utilities.sha1HexDigest(optHashValueBuilder.toString());
      currentHashes.add(hash);
      currentPaths.add(lastLeafPath);

      optHashValueBuilder = null;
    }
  }

  public static CompoundHash fromNode(Node node) {
    return fromNode(node, new SimpleSizeSplitStrategy(node));
  }

  public static CompoundHash fromNode(Node node, SplitStrategy strategy) {
    if (node.isEmpty()) {
      return new CompoundHash(Collections.<Path>emptyList(), Collections.singletonList(""));
    } else {
      CompoundHashBuilder state = new CompoundHashBuilder(strategy);
      processNode(node, state);
      state.finishHashing();
      return new CompoundHash(state.currentPaths, state.currentHashes);
    }
  }

  private static void processNode(Node node, final CompoundHashBuilder state) {
    if (node.isLeafNode()) {
      state.processLeaf((LeafNode<?>) node);
    } else if (node.isEmpty()) {
      throw new IllegalArgumentException("Can't calculate hash on empty node!");
    } else {
      if (!(node instanceof ChildrenNode)) {
        throw new IllegalStateException("Expected children node, but got: " + node);
      }
      ChildrenNode childrenNode = (ChildrenNode) node;
      ChildrenNode.ChildVisitor visitor =
          new ChildrenNode.ChildVisitor() {
            @Override
            public void visitChild(ChildKey name, Node child) {
              state.startChild(name);
              processNode(child, state);
              state.endChild();
            }
          };
      childrenNode.forEachChild(visitor, /*includePriority=*/ true);
    }
  }
}
