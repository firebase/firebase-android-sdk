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

import static com.google.firebase.database.IntegrationTestHelpers.fromSingleQuotedString;
import static com.google.firebase.database.IntegrationTestHelpers.path;
import static com.google.firebase.database.snapshot.NodeUtilities.NodeFromJSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.RetryRule;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.utilities.Utilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
public class CompoundHashTest {
  @Rule public RetryRule retryRule = new RetryRule(3);

  private static final CompoundHash.SplitStrategy NEVER_SPLIT_STRATEGY =
      new CompoundHash.SplitStrategy() {
        @Override
        public boolean shouldSplit(CompoundHash.CompoundHashBuilder state) {
          return false;
        }
      };

  private static CompoundHash.SplitStrategy splitAtPaths(String... paths) {
    final List<Path> pathList = new ArrayList<Path>();
    for (String path : paths) {
      pathList.add(path(path));
    }
    return new CompoundHash.SplitStrategy() {
      @Override
      public boolean shouldSplit(CompoundHash.CompoundHashBuilder state) {
        return pathList.contains(state.currentPath());
      }
    };
  }

  @Test
  public void emptyNodeYieldsEmptyHash() {
    CompoundHash hash = CompoundHash.fromNode(EmptyNode.Empty());
    assertEquals(Collections.<Path>emptyList(), hash.getPosts());
    assertEquals(Arrays.asList(""), hash.getHashes());
  }

  @Test
  public void compoundHashIsAlwaysFollowedByEmptyHash() {
    Node node = NodeFromJSON(fromSingleQuotedString("{'foo': 'bar'}"));
    CompoundHash hash = CompoundHash.fromNode(node, NEVER_SPLIT_STRATEGY);
    String expectedHash = Utilities.sha1HexDigest("(\"foo\":(string:\"bar\"))");
    assertEquals(Arrays.asList(path("foo")), hash.getPosts());
    assertEquals(Arrays.asList(expectedHash, ""), hash.getHashes());
  }

  @Test
  public void compoundHashCanSplitAtPriority() {
    Node node =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'foo': {'!beforePriority': 'before', '.priority': 'prio', 'afterPriority': "
                    + "'after'}, 'qux': 'qux'}"));
    CompoundHash hash = CompoundHash.fromNode(node, splitAtPaths("foo/.priority"));
    String firstHash =
        Utilities.sha1HexDigest(
            "(\"foo\":(\"!beforePriority\":(string:\"before\"),\".priority\":(string:\"prio\")))");
    String secondHash =
        Utilities.sha1HexDigest(
            "(\"foo\":(\"afterPriority\":(string:\"after\")),\"qux\":(string:\"qux\"))");
    assertEquals(Arrays.asList(path("foo/.priority"), path("qux")), hash.getPosts());
    assertEquals(Arrays.asList(firstHash, secondHash, ""), hash.getHashes());
  }

  @Test
  public void hashesPriorityLeafNodes() {
    Node node =
        NodeFromJSON(fromSingleQuotedString("{'foo': {'.value': 'bar', '.priority': 'baz'}}"));
    CompoundHash hash = CompoundHash.fromNode(node, NEVER_SPLIT_STRATEGY);
    String expectedHash =
        Utilities.sha1HexDigest("(\"foo\":(priority:string:\"baz\":string:\"bar\"))");
    assertEquals(Arrays.asList(path("foo")), hash.getPosts());
    assertEquals(Arrays.asList(expectedHash, ""), hash.getHashes());
  }

  @Test
  public void hashingFollowsFirebaseKeySemantics() {
    Node node = NodeFromJSON(fromSingleQuotedString("{'1': 'one', '2': 'two', '10': 'ten'}"));
    // 10 is after 2 in Firebase key semantics, but would be before 2 in string semantics
    CompoundHash hash = CompoundHash.fromNode(node, splitAtPaths("2"));
    String firstHash = Utilities.sha1HexDigest("(\"1\":(string:\"one\"),\"2\":(string:\"two\"))");
    String secondHash = Utilities.sha1HexDigest("(\"10\":(string:\"ten\"))");
    assertEquals(Arrays.asList(path("2"), path("10")), hash.getPosts());
    assertEquals(Arrays.asList(firstHash, secondHash, ""), hash.getHashes());
  }

  @Test
  public void hashingOnChildBoundariesWorks() {
    Node node =
        NodeFromJSON(
            fromSingleQuotedString("{'bar': {'deep': 'value'}, 'foo': {'other-deep': 'value'}}"));
    CompoundHash hash = CompoundHash.fromNode(node, splitAtPaths("bar/deep"));
    String firstHash = Utilities.sha1HexDigest("(\"bar\":(\"deep\":(string:\"value\")))");
    String secondHash = Utilities.sha1HexDigest("(\"foo\":(\"other-deep\":(string:\"value\")))");
    assertEquals(Arrays.asList(path("bar/deep"), path("foo/other-deep")), hash.getPosts());
    assertEquals(Arrays.asList(firstHash, secondHash, ""), hash.getHashes());
  }

  @Test
  public void commasAreSetForNestedChildren() {
    Node node =
        NodeFromJSON(
            fromSingleQuotedString("{'bar': {'deep': 'value'}, 'foo': {'other-deep': 'value'}}"));
    CompoundHash hash = CompoundHash.fromNode(node, NEVER_SPLIT_STRATEGY);
    String hashValue =
        Utilities.sha1HexDigest(
            "(\"bar\":(\"deep\":(string:\"value\")),\"foo\":(\"other-deep\":(string:\"value\")))");
    assertEquals(Arrays.asList(path("foo/other-deep")), hash.getPosts());
    assertEquals(Arrays.asList(hashValue, ""), hash.getHashes());
  }

  @Test
  public void quotedStringsAndKeys() {
    Map<String, Object> data = new MapBuilder().put("\"\\\"\\", "\"\\\"\\").put("\"", "\\").build();
    Node node = NodeFromJSON(data);
    CompoundHash hash = CompoundHash.fromNode(node, NEVER_SPLIT_STRATEGY);
    String hashValue =
        Utilities.sha1HexDigest(
            "(\"\\\"\":(string:\"\\\\\"),\"\\\"\\\\\\\"\\\\\":(string:\"\\\"\\\\\\\"\\\\\"))");
    assertEquals(Arrays.asList(path("\"\\\"\\")), hash.getPosts());
    assertEquals(Arrays.asList(hashValue, ""), hash.getHashes());
  }

  private static void assertWithinPercent(int expected, int actual, double percent) {
    double percentDecimal = percent / 100.0;
    double lowerBound = expected * (1 - percentDecimal);
    double upperBound = expected * (1 + percentDecimal);
    assertTrue(
        String.format("Not within range: (%02f, %02f): %d", lowerBound, upperBound, actual),
        actual > lowerBound);
    assertTrue(
        String.format("Not within range: (%02f, %02f): %d", lowerBound, upperBound, actual),
        actual < upperBound);
  }

  @Test
  public void defaultSplitHasSensibleAmountOfHashes() {
    Node node10k = EmptyNode.Empty();
    Node node100k = EmptyNode.Empty();
    Node node1M = EmptyNode.Empty();
    for (int i = 0; i < 500; i++) {
      // roughly 15-20 bytes serialized per node, 100k total
      node10k =
          node10k.updateImmediateChild(ChildKey.fromString("key-" + i), NodeFromJSON("value"));
    }
    for (int i = 0; i < 5000; i++) {
      // roughly 15-20 bytes serialized per node, 100k total
      node100k =
          node100k.updateImmediateChild(ChildKey.fromString("key-" + i), NodeFromJSON("value"));
    }
    for (int i = 0; i < 50000; i++) {
      // roughly 15-20 bytes serialized per node, 1M total
      node1M = node1M.updateImmediateChild(ChildKey.fromString("key-" + i), NodeFromJSON("value"));
    }
    CompoundHash hash10K = CompoundHash.fromNode(node10k);
    CompoundHash hash100K = CompoundHash.fromNode(node100k);
    CompoundHash hash1M = CompoundHash.fromNode(node1M);
    assertWithinPercent(15, hash10K.getHashes().size(), /*percent=*/ 10);
    assertWithinPercent(50, hash100K.getHashes().size(), /*percent=*/ 10);
    assertWithinPercent(150, hash1M.getHashes().size(), /*percent=*/ 10);
  }

  @Test
  public void defaultSplitHandlesLargeLeafNodeAtRoot() {
    StringBuilder largeString = new StringBuilder();
    for (int i = 0; i < 50 * 1024; i++) {
      largeString.append("x");
    }
    Node leafNode = NodeFromJSON(largeString.toString(), EmptyNode.Empty());
    CompoundHash hash = CompoundHash.fromNode(leafNode);
    assertEquals(2, hash.getHashes().size());
  }
}
