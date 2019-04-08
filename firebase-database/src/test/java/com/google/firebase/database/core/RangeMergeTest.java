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

import static com.google.firebase.database.UnitTestHelpers.fromSingleQuotedString;
import static com.google.firebase.database.UnitTestHelpers.path;
import static com.google.firebase.database.snapshot.NodeUtilities.NodeFromJSON;
import static org.junit.Assert.assertEquals;

import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.RangeMerge;
import org.junit.Ignore;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RangeMergeTest {
  @Test
  public void smokeTest() {
    Node node =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'bar': 'bar-value', 'foo': { 'a': {'deep-a-1': 1, 'deep-a-2':2}, 'b': 'b', "
                    + "'c': 'c', 'd': 'd'}, 'quu': 'quu-value'}"));
    Node update =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'foo': { 'a': {'deep-a-2':'new-a-2', 'deep-a-3':3}, 'b-2': 'new-b', 'c': "
                    + "'new-c' }}"));
    RangeMerge merge = new RangeMerge(path("foo/a/deep-a-1"), path("foo/c"), update);
    Node actual = merge.applyTo(node);
    Node expected =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'bar': 'bar-value', 'foo': { 'a': {'deep-a-1': 1, 'deep-a-2':'new-a-2', "
                    + "'deep-a-3':3}, 'b-2': 'new-b', 'c': 'new-c', 'd': 'd'}, "
                    + "'quu': 'quu-value'}"));
    assertEquals(expected, actual);
  }

  @Test
  public void startIsExclusive() {
    Node node =
        NodeFromJSON(
            fromSingleQuotedString("{'bar': 'bar-value', 'foo': 'foo-value', 'quu': 'quu-value'}"));
    Node update = NodeFromJSON(fromSingleQuotedString("{'foo': 'new-foo-value' }"));
    RangeMerge merge = new RangeMerge(path("bar"), path("foo"), update);
    Node actual = merge.applyTo(node);
    Node expected =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'bar': 'bar-value', 'foo': 'new-foo-value', 'quu': 'quu-value'}"));
    assertEquals(expected, actual);
  }

  @Test
  public void startIsExclusiveButIncludesChildren() {
    Node node =
        NodeFromJSON(
            fromSingleQuotedString("{'bar': 'bar-value', 'foo': 'foo-value', 'quu': 'quu-value'}"));
    Node update =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'bar': {'bar-child': 'bar-child-value'}, 'foo': 'new-foo-value' }"));
    RangeMerge merge = new RangeMerge(path("bar"), path("foo"), update);
    Node actual = merge.applyTo(node);
    Node expected =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'bar': { 'bar-child': 'bar-child-value'}, 'foo': 'new-foo-value', 'quu': "
                    + "'quu-value'}"));
    assertEquals(expected, actual);
  }

  @Test
  public void endIsInclusive() {
    Node node =
        NodeFromJSON(
            fromSingleQuotedString("{'bar': 'bar-value', 'foo': 'foo-value', 'quu': 'quu-value'}"));
    Node update = NodeFromJSON(fromSingleQuotedString("{'baz': 'baz-value' }"));
    RangeMerge merge = new RangeMerge(path("bar"), path("foo"), update); // foo should be deleted
    Node actual = merge.applyTo(node);
    Node expected =
        NodeFromJSON(
            fromSingleQuotedString("{'bar': 'bar-value', 'baz': 'baz-value', 'quu': 'quu-value'}"));
    assertEquals(expected, actual);
  }

  @Test
  public void endIsInclusiveButExcludesChildren() {
    Node node =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'bar': 'bar-value', 'foo': {'foo-child': 'foo-child-value'}, 'quu': "
                    + "'quu-value'}"));
    Node update = NodeFromJSON(fromSingleQuotedString("{'baz': 'baz-value' }"));
    RangeMerge merge = new RangeMerge(path("bar"), path("foo"), update);
    Node actual = merge.applyTo(node);
    Node expected =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'bar': 'bar-value', 'baz': 'baz-value', 'foo': {'foo-child': "
                    + "'foo-child-value'}, 'quu': 'quu-value'}"));
    assertEquals(expected, actual);
  }

  @Test
  public void canUpdateLeafNode() {
    Node node = NodeFromJSON("leaf-value");
    Node update = NodeFromJSON(fromSingleQuotedString("{'bar': 'bar-value' }"));
    RangeMerge merge = new RangeMerge(null, path("foo"), update);
    Node actual = merge.applyTo(node);
    Node expected = NodeFromJSON(fromSingleQuotedString("{'bar': 'bar-value'}"));
    assertEquals(expected, actual);
  }

  @Test
  public void canReplaceLeafNodeWithLeafNode() {
    Node node = NodeFromJSON("leaf-value");
    Node update = NodeFromJSON("new-leaf-value");
    RangeMerge merge = new RangeMerge(null, path("/"), update);
    Node actual = merge.applyTo(node);
    Node expected = NodeFromJSON("new-leaf-value");
    assertEquals(expected, actual);
  }

  @Test
  public void leafsAreUpdatedWhenRangesIncludeDeeperPath() {
    Node node = NodeFromJSON(fromSingleQuotedString("{'foo': {'bar': 'bar-value'}}"));
    Node update = NodeFromJSON(fromSingleQuotedString("{'foo': {'bar': 'new-bar-value'}}"));
    RangeMerge merge = new RangeMerge(path("foo/"), path("foo/bar/deep"), update);
    Node actual = merge.applyTo(node);
    Node expected = NodeFromJSON(fromSingleQuotedString("{'foo': {'bar': 'new-bar-value'}}"));
    assertEquals(expected, actual);
  }

  @Test
  public void leafsAreNotUpdatedWhenRangesAreAtDeeperPaths() {
    Node node = NodeFromJSON(fromSingleQuotedString("{'foo': {'bar': 'bar-value'}}"));
    Node update = NodeFromJSON(fromSingleQuotedString("{'foo': {'bar': 'new-bar-value'}}"));
    RangeMerge merge = new RangeMerge(path("foo/bar"), path("foo/bar/deep"), update);
    Node actual = merge.applyTo(node);
    Node expected = NodeFromJSON(fromSingleQuotedString("{'foo': {'bar': 'bar-value'}}"));
    assertEquals(expected, actual);
  }

  @Test
  public void updatingEntireRangeUpdatesEverything() {
    Node node = EmptyNode.Empty();
    Node update =
        NodeFromJSON(
            fromSingleQuotedString("{'foo': 'foo-value', 'bar': {'child':'bar-child-value'}}"));
    RangeMerge merge = new RangeMerge(null, null, update);
    Node actual = merge.applyTo(node);
    Node expected =
        NodeFromJSON(
            fromSingleQuotedString("{'foo': 'foo-value', 'bar': {'child':'bar-child-value'}}"));
    assertEquals(expected, actual);
  }

  @Test
  public void updatingRangeWithUnboundedLeftPostWorks() {
    Node node = NodeFromJSON(fromSingleQuotedString("{'bar': 'bar-value', 'foo': 'foo-value'}"));
    Node update = NodeFromJSON(fromSingleQuotedString("{'bar': 'new-bar'}"));
    RangeMerge merge = new RangeMerge(null, path("bar"), update);
    Node actual = merge.applyTo(node);
    Node expected = NodeFromJSON(fromSingleQuotedString("{'bar': 'new-bar', 'foo': 'foo-value'}"));
    assertEquals(expected, actual);
  }

  @Test
  public void updatingRangeWithRightPostChildOfLeftPostWorks() {
    Node node =
        NodeFromJSON(
            fromSingleQuotedString("{'foo': {'a': 'a', 'b': {'1': '1', '2': '2'}, 'c': 'c'}}"));
    Node update =
        NodeFromJSON(fromSingleQuotedString("{'foo': {'a': 'new-a', 'b': {'1': 'new-1'}}}"));
    RangeMerge merge = new RangeMerge(path("foo"), path("foo/b/1"), update);
    Node actual = merge.applyTo(node);
    Node expected =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'foo': {'a': 'new-a', 'b': {'1': 'new-1', '2': '2'}, 'c': 'c'}}"));
    assertEquals(expected, actual);
  }

  @Test
  public void updatingRangeWithRightPostChildOfLeftPostWorksWithIntegerKeys() {
    Node node =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'foo': {'a': 'a', 'b': {'1': '1', '2': '2', '10': '10'}, 'c': 'c'}}"));
    Node update =
        NodeFromJSON(fromSingleQuotedString("{'foo': {'a': 'new-a', 'b': {'1': 'new-1'}}}"));
    RangeMerge merge = new RangeMerge(path("foo"), path("foo/b/2"), update);
    Node actual = merge.applyTo(node);
    Node expected =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'foo': {'a': 'new-a', 'b': {'1': 'new-1', '10': '10'}, 'c': 'c'}}"));
    assertEquals(expected, actual);
  }

  @Test
  public void updatingLeafIncludesPriority() {
    Node node =
        NodeFromJSON(
            fromSingleQuotedString("{'bar': 'bar-value', 'foo': 'foo-value', 'quu': 'quu-value'}"));
    Node update =
        NodeFromJSON(
            fromSingleQuotedString("{'foo': { '.value': 'new-foo', '.priority': 'prio' }}"));
    RangeMerge merge = new RangeMerge(path("bar"), path("foo"), update);
    Node actual = merge.applyTo(node);
    Node expected =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'bar': 'bar-value', 'foo': { '.value': 'new-foo', '.priority': 'prio' }, "
                    + "'quu': 'quu-value'}"));
    assertEquals(expected, actual);
  }

  @Test
  public void updatingPriorityInChildrenNodeWorks() {
    Node node = NodeFromJSON(fromSingleQuotedString("{'bar': 'bar-value', 'foo': 'foo-value'}"));
    Node update = NodeFromJSON(fromSingleQuotedString("{'bar': 'new-bar', '.priority': 'prio' }"));
    RangeMerge merge = new RangeMerge(null, path("bar"), update);
    Node actual = merge.applyTo(node);
    Node expected =
        NodeFromJSON(
            fromSingleQuotedString("{'bar': 'new-bar', 'foo': 'foo-value', '.priority': 'prio'}"));
    assertEquals(expected, actual);
  }

  // TODO: this test should actually work, but priorities on empty nodes are ignored :(
  // TODO(b/34107586): Fix and enable test
  @Ignore
  @Test
  public void updatingPriorityInChildrenNodeWorksAlone() {
    Node node = NodeFromJSON(fromSingleQuotedString("{'bar': 'bar-value', 'foo': 'foo-value'}"));
    Node update = NodeFromJSON(fromSingleQuotedString("{'.priority': 'prio' }"));
    RangeMerge merge = new RangeMerge(null, path(".priority"), update);
    Node actual = merge.applyTo(node);
    Node expected =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'bar': 'bar-value', 'foo': 'foo-value', '.priority': 'prio'}"));
    assertEquals(expected, actual);
  }

  @Test
  public void updatingPriorityOnInitiallyEmptyNodeDoesNotBreak() {
    Node node = NodeFromJSON(fromSingleQuotedString("{}"));
    Node update =
        NodeFromJSON(fromSingleQuotedString("{'.priority': 'prio', 'foo': 'foo-value' }"));
    RangeMerge merge = new RangeMerge(null, path("foo"), update);
    Node actual = merge.applyTo(node);
    Node expected =
        NodeFromJSON(fromSingleQuotedString("{'foo': 'foo-value', '.priority': 'prio'}"));
    assertEquals(expected, actual);
  }

  @Test
  public void priorityIsDeletedWhenIncludedInChildrenRange() {
    Node node =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'bar': 'bar-value', 'foo': 'foo-value', '.priority': 'prio'}"));
    Node update = NodeFromJSON(fromSingleQuotedString("{'bar': 'new-bar'}"));
    RangeMerge merge = new RangeMerge(null, path("bar"), update); // deletes priority
    Node actual = merge.applyTo(node);
    Node expected = NodeFromJSON(fromSingleQuotedString("{'bar': 'new-bar', 'foo': 'foo-value'}"));
    assertEquals(expected, actual);
  }

  @Test
  public void priorityIsIncludedInOpenStart() {
    Node node = NodeFromJSON(fromSingleQuotedString("{'foo': {'bar': 'bar-value'}}"));
    Node update = NodeFromJSON(fromSingleQuotedString("{'.priority': 'prio', 'baz': 'baz' }"));
    RangeMerge merge = new RangeMerge(null, path("foo/bar"), update);
    Node actual = merge.applyTo(node);
    Node expected = NodeFromJSON(fromSingleQuotedString("{'baz': 'baz', '.priority': 'prio'}"));
    assertEquals(expected, actual);
  }

  @Test
  public void priorityIsIncludedInOpenEnd() {
    Node node = NodeFromJSON("leaf-value");
    Node update = NodeFromJSON(fromSingleQuotedString("{'.priority': 'prio', 'foo': 'bar' }"));
    RangeMerge merge = new RangeMerge(path("/"), null, update);
    Node actual = merge.applyTo(node);
    Node expected = NodeFromJSON(fromSingleQuotedString("{'foo': 'bar', '.priority': 'prio'}"));
    assertEquals(expected, actual);
  }
}
