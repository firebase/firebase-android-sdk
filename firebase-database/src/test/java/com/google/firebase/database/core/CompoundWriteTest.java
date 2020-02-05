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

import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import com.google.firebase.database.snapshot.PriorityUtilities;
import com.google.firebase.database.snapshot.StringNode;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class CompoundWriteTest {

  private static final Node LEAF_NODE = NodeUtilities.NodeFromJSON("leaf-node");
  private static final Node PRIO_NODE = NodeUtilities.NodeFromJSON("prio");

  private static void assertNodeGetsCorrectPriority(
      CompoundWrite compoundWrite, Node node, Node priority) {
    if (node.isEmpty()) {
      Assert.assertEquals(EmptyNode.Empty(), compoundWrite.apply(node));
    } else {
      Assert.assertEquals(node.updatePriority(priority), compoundWrite.apply(node));
    }
  }

  @Test
  public void emptyMergeIsEmpty() {
    Assert.assertTrue(CompoundWrite.emptyWrite().isEmpty());
  }

  @Test
  public void compoundWriteWithPriorityUpdateIsNotEmpty() {
    Assert.assertFalse(
        CompoundWrite.emptyWrite().addWrite(ChildKey.getPriorityKey(), PRIO_NODE).isEmpty());
  }

  @Test
  public void compoundWriteWithUpdateIsNotEmpty() {
    Assert.assertFalse(
        CompoundWrite.emptyWrite().addWrite(new Path("foo/bar"), LEAF_NODE).isEmpty());
  }

  @Test
  public void compoundWriteWithRootUpdateIsNotEmpty() {
    Assert.assertFalse(
        CompoundWrite.emptyWrite().addWrite(Path.getEmptyPath(), LEAF_NODE).isEmpty());
  }

  @Test
  public void compoundWriteWithEmptyRootUpdateIsNotEmpty() {
    Assert.assertFalse(
        CompoundWrite.emptyWrite().addWrite(Path.getEmptyPath(), EmptyNode.Empty()).isEmpty());
  }

  @Test
  public void compoundWriteWithRootPriorityUpdateAndChildMergeIsNotEmpty() {
    CompoundWrite compoundWrite =
        CompoundWrite.emptyWrite().addWrite(ChildKey.getPriorityKey(), PRIO_NODE);
    Assert.assertFalse(compoundWrite.childCompoundWrite(new Path(".priority")).isEmpty());
  }

  @Test
  public void appliesLeafOverwrite() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(Path.getEmptyPath(), LEAF_NODE);
    Assert.assertEquals(LEAF_NODE, compoundWrite.apply(EmptyNode.Empty()));
  }

  @Test
  public void appliesChildrenOverwrite() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Node childNode =
        EmptyNode.Empty().updateImmediateChild(ChildKey.fromString("child"), LEAF_NODE);
    compoundWrite = compoundWrite.addWrite(Path.getEmptyPath(), childNode);
    Assert.assertEquals(childNode, compoundWrite.apply(EmptyNode.Empty()));
  }

  @Test
  public void addsChildNode() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    ChildKey childKey = ChildKey.fromString("child");
    Node expected = EmptyNode.Empty().updateImmediateChild(childKey, LEAF_NODE);
    compoundWrite = compoundWrite.addWrite(childKey, LEAF_NODE);
    Assert.assertEquals(expected, compoundWrite.apply(EmptyNode.Empty()));
  }

  @Test
  public void addsDeepChildNode() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Path path = new Path("deep/deep/node");
    Node expected = EmptyNode.Empty().updateChild(path, LEAF_NODE);
    compoundWrite = compoundWrite.addWrite(path, LEAF_NODE);
    Assert.assertEquals(expected, compoundWrite.apply(EmptyNode.Empty()));
  }

  @Test
  public void overwritesExistingChild() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Map<String, Object> base =
        new MapBuilder().put("child-1", "value-1").put("child-2", "value-2").build();
    Node baseNode = NodeUtilities.NodeFromJSON(base);
    Path path = new Path("child-1");
    compoundWrite = compoundWrite.addWrite(path, LEAF_NODE);
    Assert.assertEquals(
        baseNode.updateImmediateChild(path.getFront(), LEAF_NODE), compoundWrite.apply(baseNode));
  }

  @Test
  public void updatesExistingChild() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Map<String, Object> base =
        new MapBuilder().put("child-1", "value-1").put("child-2", "value-2").build();
    Node baseNode = NodeUtilities.NodeFromJSON(base);
    Path path = new Path("child-1/foo");
    compoundWrite = compoundWrite.addWrite(path, LEAF_NODE);
    Assert.assertEquals(baseNode.updateChild(path, LEAF_NODE), compoundWrite.apply(baseNode));
  }

  @Test
  public void doesntUpdatePriorityOnEmptyNode() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite =
        compoundWrite.addWrite(ChildKey.getPriorityKey(), NodeUtilities.NodeFromJSON("prio"));
    assertNodeGetsCorrectPriority(
        compoundWrite, EmptyNode.Empty(), PriorityUtilities.NullPriority());
  }

  @Test
  public void updatesPriorityOnNode() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(ChildKey.getPriorityKey(), PRIO_NODE);
    Node node = NodeUtilities.NodeFromJSON("value");
    assertNodeGetsCorrectPriority(compoundWrite, node, PRIO_NODE);
  }

  @Test
  public void updatesPriorityOfChild() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Map<String, Object> base =
        new MapBuilder().put("child-1", "value-1").put("child-2", "value-2").build();
    Node baseNode = NodeUtilities.NodeFromJSON(base);
    Path path = new Path("child-1/.priority");
    compoundWrite = compoundWrite.addWrite(path, PRIO_NODE);
    Assert.assertEquals(baseNode.updateChild(path, PRIO_NODE), compoundWrite.apply(baseNode));
  }

  @Test
  public void doesntUpdatePriorityOfNonExistentChild() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Map<String, Object> base =
        new MapBuilder().put("child-1", "value-1").put("child-2", "value-2").build();
    Node baseNode = NodeUtilities.NodeFromJSON(base);
    Path path = new Path("child-3/.priority");
    compoundWrite = compoundWrite.addWrite(path, PRIO_NODE);
    Assert.assertEquals(baseNode, compoundWrite.apply(baseNode));
  }

  @Test
  public void priorityUpdateOnPreviousEmptyChildWriteIsIgnored() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(ChildKey.fromString("child"), EmptyNode.Empty());
    Path path = new Path("child/.priority");
    compoundWrite = compoundWrite.addWrite(path, PRIO_NODE);
    Node applied = compoundWrite.apply(EmptyNode.Empty());
    Assert.assertEquals(EmptyNode.Empty(), applied.getChild(new Path("child")).getPriority());
    Assert.assertEquals(
        EmptyNode.Empty(), compoundWrite.getCompleteNode(new Path("child")).getPriority());
    for (NamedNode node : compoundWrite.getCompleteChildren()) {
      Assert.assertEquals(EmptyNode.Empty(), node.getNode().getPriority());
    }
  }

  @Test
  public void deepUpdateExistingUpdates() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Map<String, Object> base =
        new MapBuilder().put("child-1", "value-1").put("child-2", "value-2").build();
    Node baseNode = NodeUtilities.NodeFromJSON(base);
    Node updateOne =
        NodeUtilities.NodeFromJSON(
            new MapBuilder().put("foo", "foo-value").put("bar", "bar-value").build());
    Node updateTwo = NodeUtilities.NodeFromJSON("baz-value");
    Node updateThree = NodeUtilities.NodeFromJSON("new-foo-value");
    compoundWrite = compoundWrite.addWrite(new Path("child-1"), updateOne);
    compoundWrite = compoundWrite.addWrite(new Path("child-1/baz"), updateTwo);
    compoundWrite = compoundWrite.addWrite(new Path("child-1/foo"), updateThree);
    Map<String, Object> expectedChildOne =
        new MapBuilder()
            .put("foo", "new-foo-value")
            .put("bar", "bar-value")
            .put("baz", "baz-value")
            .build();
    Node expected =
        baseNode.updateImmediateChild(
            ChildKey.fromString("child-1"), NodeUtilities.NodeFromJSON(expectedChildOne));
    Assert.assertEquals(expected, compoundWrite.apply(baseNode));
  }

  @Test
  public void shallowUpdateRemovesDeepUpdate() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Map<String, Object> base =
        new MapBuilder().put("child-1", "value-1").put("child-2", "value-2").build();
    Node baseNode = NodeUtilities.NodeFromJSON(base);
    Node updateOne = NodeUtilities.NodeFromJSON("new-foo-value");
    Node updateTwo = NodeUtilities.NodeFromJSON("baz-value");
    Node updateThree =
        NodeUtilities.NodeFromJSON(
            new MapBuilder().put("foo", "foo-value").put("bar", "bar-value").build());
    compoundWrite = compoundWrite.addWrite(new Path("child-1/foo"), updateOne);
    compoundWrite = compoundWrite.addWrite(new Path("child-1/baz"), updateTwo);
    compoundWrite = compoundWrite.addWrite(new Path("child-1"), updateThree);
    Map<String, Object> expectedChildOne =
        new MapBuilder().put("foo", "foo-value").put("bar", "bar-value").build();
    Node expected =
        baseNode.updateImmediateChild(
            ChildKey.fromString("child-1"), NodeUtilities.NodeFromJSON(expectedChildOne));
    Assert.assertEquals(expected, compoundWrite.apply(baseNode));
  }

  @Test
  public void childPriorityDoesntUpdateEmptyNodePriorityOnChildMerge() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(new Path("child-1/.priority"), PRIO_NODE);
    assertNodeGetsCorrectPriority(
        compoundWrite.childCompoundWrite(new Path("child-1")),
        EmptyNode.Empty(),
        PriorityUtilities.NullPriority());
  }

  @Test
  public void childPriorityUpdatesPriorityOnChildMerge() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(new Path("child-1/.priority"), PRIO_NODE);
    Node node = NodeUtilities.NodeFromJSON("value");
    assertNodeGetsCorrectPriority(
        compoundWrite.childCompoundWrite(new Path("child-1")), node, PRIO_NODE);
  }

  @Test
  public void childPriorityUpdatesEmptyPriorityOnChildMerge() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(new Path("child-1/.priority"), EmptyNode.Empty());
    Node node = new StringNode("foo", PRIO_NODE);
    assertNodeGetsCorrectPriority(
        compoundWrite.childCompoundWrite(new Path("child-1")), node, EmptyNode.Empty());
  }

  @Test
  public void deepPrioritySetWorksOnEmptyNodeWhenOtherSetIsAvailable() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(new Path("foo/.priority"), PRIO_NODE);
    compoundWrite = compoundWrite.addWrite(new Path("foo/child"), LEAF_NODE);
    Node node = compoundWrite.apply(EmptyNode.Empty());
    Assert.assertEquals(PRIO_NODE, node.getChild(new Path("foo")).getPriority());
  }

  @Test
  public void childMergeLooksIntoUpdateNode() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Node update =
        NodeUtilities.NodeFromJSON(
            new MapBuilder().put("foo", "foo-value").put("bar", "bar-value").build());
    compoundWrite = compoundWrite.addWrite(Path.getEmptyPath(), update);
    Assert.assertEquals(
        NodeUtilities.NodeFromJSON("foo-value"),
        compoundWrite.childCompoundWrite(new Path("foo")).apply(EmptyNode.Empty()));
  }

  @Test
  public void childMergeRemovesNodeOnDeeperPaths() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Node update =
        NodeUtilities.NodeFromJSON(
            new MapBuilder().put("foo", "foo-value").put("bar", "bar-value").build());
    compoundWrite = compoundWrite.addWrite(Path.getEmptyPath(), update);
    Assert.assertEquals(
        EmptyNode.Empty(),
        compoundWrite.childCompoundWrite(new Path("foo/not/existing")).apply(LEAF_NODE));
  }

  @Test
  public void childMergeWithEmptyPathIsSameMerge() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Node update =
        NodeUtilities.NodeFromJSON(
            new MapBuilder().put("foo", "foo-value").put("bar", "bar-value").build());
    compoundWrite = compoundWrite.addWrite(Path.getEmptyPath(), update);
    Assert.assertSame(compoundWrite, compoundWrite.childCompoundWrite(Path.getEmptyPath()));
  }

  @Test
  public void rootUpdateRemovesRootPriority() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(new Path(".priority"), PRIO_NODE);
    compoundWrite = compoundWrite.addWrite(Path.getEmptyPath(), NodeUtilities.NodeFromJSON("foo"));
    Assert.assertEquals(NodeUtilities.NodeFromJSON("foo"), compoundWrite.apply(EmptyNode.Empty()));
  }

  @Test
  public void deepUpdateRemovesPriorityThere() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(new Path("foo/.priority"), PRIO_NODE);
    compoundWrite = compoundWrite.addWrite(new Path("foo"), NodeUtilities.NodeFromJSON("bar"));
    Node expected = NodeUtilities.NodeFromJSON(new MapBuilder().put("foo", "bar").build());
    Assert.assertEquals(expected, compoundWrite.apply(EmptyNode.Empty()));
  }

  @Test
  public void addingUpdatesAtPathWorks() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Map<String, Object> base =
        new MapBuilder().put("child-1", "value-1").put("child-2", "value-2").build();
    Map<ChildKey, Node> updates = new HashMap<ChildKey, Node>();
    updates.put(ChildKey.fromString("foo"), NodeUtilities.NodeFromJSON("foo-value"));
    updates.put(ChildKey.fromString("bar"), NodeUtilities.NodeFromJSON("bar-value"));
    compoundWrite =
        compoundWrite.addWrites(new Path("child-1"), CompoundWrite.fromChildMerge(updates));

    Node baseNode = NodeUtilities.NodeFromJSON(base);
    Map<String, Object> expectedChildOne =
        new MapBuilder().put("foo", "foo-value").put("bar", "bar-value").build();
    Node expected =
        baseNode.updateImmediateChild(
            ChildKey.fromString("child-1"), NodeUtilities.NodeFromJSON(expectedChildOne));
    Assert.assertEquals(expected, compoundWrite.apply(baseNode));
  }

  @Test
  public void addingUpdatesAtRootWorks() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Map<String, Object> base =
        new MapBuilder().put("child-1", "value-1").put("child-2", "value-2").build();
    Map<ChildKey, Node> updates = new HashMap<ChildKey, Node>();
    updates.put(ChildKey.fromString("child-1"), NodeUtilities.NodeFromJSON("new-value-1"));
    updates.put(ChildKey.fromString("child-2"), EmptyNode.Empty());
    updates.put(ChildKey.fromString("child-3"), NodeUtilities.NodeFromJSON("value-3"));
    compoundWrite =
        compoundWrite.addWrites(Path.getEmptyPath(), CompoundWrite.fromChildMerge(updates));

    Node baseNode = NodeUtilities.NodeFromJSON(base);
    Map<String, Object> expected =
        new MapBuilder().put("child-1", "new-value-1").put("child-3", "value-3").build();
    Assert.assertEquals(NodeUtilities.NodeFromJSON(expected), compoundWrite.apply(baseNode));
  }

  @Test
  public void childMergeOfRootPriorityWorks() {
    CompoundWrite compoundWrite =
        CompoundWrite.emptyWrite().addWrite(new Path(".priority"), PRIO_NODE);
    Assert.assertEquals(
        PRIO_NODE,
        compoundWrite.childCompoundWrite(new Path(".priority")).apply(EmptyNode.Empty()));
  }

  @Test
  public void completeChildrenOnlyReturnsCompleteOverwrites() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(new Path("child-1"), LEAF_NODE);
    Assert.assertEquals(
        Arrays.asList(new NamedNode(ChildKey.fromString("child-1"), LEAF_NODE)),
        compoundWrite.getCompleteChildren());
  }

  @Test
  public void completeChildrenOnlyReturnsEmptyOverwrites() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(new Path("child-1"), EmptyNode.Empty());
    Assert.assertEquals(
        Arrays.asList(new NamedNode(ChildKey.fromString("child-1"), EmptyNode.Empty())),
        compoundWrite.getCompleteChildren());
  }

  @Test
  public void completeChildrenDoesntReturnDeepOverwrites() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(new Path("child-1/deep/path"), LEAF_NODE);
    Assert.assertEquals(Collections.<NamedNode>emptyList(), compoundWrite.getCompleteChildren());
  }

  @Test
  public void completeChildrenReturnAllCompleteChildrenButNoIncomplete() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(new Path("child-1/deep/path"), LEAF_NODE);
    compoundWrite = compoundWrite.addWrite(new Path("child-2"), LEAF_NODE);
    compoundWrite = compoundWrite.addWrite(new Path("child-3"), EmptyNode.Empty());
    Map<ChildKey, Node> expected = new HashMap<ChildKey, Node>();
    expected.put(ChildKey.fromString("child-2"), LEAF_NODE);
    expected.put(ChildKey.fromString("child-3"), EmptyNode.Empty());
    Map<ChildKey, Node> actual = new HashMap<ChildKey, Node>();
    for (NamedNode node : compoundWrite.getCompleteChildren()) {
      actual.put(node.getName(), node.getNode());
    }
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void completeChildrenReturnAllChildrenForRootSet() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Map<String, Object> base =
        new MapBuilder().put("child-1", "value-1").put("child-2", "value-2").build();
    Node baseNode = NodeUtilities.NodeFromJSON(base);
    compoundWrite = compoundWrite.addWrite(Path.getEmptyPath(), baseNode);

    Map<ChildKey, Node> expected = new HashMap<ChildKey, Node>();
    expected.put(ChildKey.fromString("child-1"), NodeUtilities.NodeFromJSON("value-1"));
    expected.put(ChildKey.fromString("child-2"), NodeUtilities.NodeFromJSON("value-2"));

    Map<ChildKey, Node> actual = new HashMap<ChildKey, Node>();
    for (NamedNode node : compoundWrite.getCompleteChildren()) {
      actual.put(node.getName(), node.getNode());
    }
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void emptyMergeHasNoShadowingWrite() {
    Assert.assertFalse(CompoundWrite.emptyWrite().hasCompleteWrite(Path.getEmptyPath()));
  }

  @Test
  public void compoundWriteWithEmptyRootHasShadowingWrite() {
    CompoundWrite compoundWrite =
        CompoundWrite.emptyWrite().addWrite(Path.getEmptyPath(), EmptyNode.Empty());
    Assert.assertTrue(compoundWrite.hasCompleteWrite(Path.getEmptyPath()));
    Assert.assertTrue(compoundWrite.hasCompleteWrite(new Path("child")));
  }

  @Test
  public void compoundWriteWithRootHasShadowingWrite() {
    CompoundWrite compoundWrite =
        CompoundWrite.emptyWrite().addWrite(Path.getEmptyPath(), LEAF_NODE);
    Assert.assertTrue(compoundWrite.hasCompleteWrite(Path.getEmptyPath()));
    Assert.assertTrue(compoundWrite.hasCompleteWrite(new Path("child")));
  }

  @Test
  public void compoundWriteWithDeepUpdateHasShadowingWrite() {
    CompoundWrite compoundWrite =
        CompoundWrite.emptyWrite().addWrite(new Path("deep/update"), LEAF_NODE);
    Assert.assertFalse(compoundWrite.hasCompleteWrite(Path.getEmptyPath()));
    Assert.assertFalse(compoundWrite.hasCompleteWrite(new Path("deep")));
    Assert.assertTrue(compoundWrite.hasCompleteWrite(new Path("deep/update")));
  }

  @Test
  public void compoundWriteWithPriorityUpdateHasShadowingWrite() {
    CompoundWrite compoundWrite =
        CompoundWrite.emptyWrite().addWrite(new Path(".priority"), PRIO_NODE);
    Assert.assertFalse(compoundWrite.hasCompleteWrite(Path.getEmptyPath()));
    Assert.assertTrue(compoundWrite.hasCompleteWrite(new Path(".priority")));
  }

  @Test
  public void updatesCanBeRemoved() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Map<String, Object> base =
        new MapBuilder().put("child-1", "value-1").put("child-2", "value-2").build();
    Node baseNode = NodeUtilities.NodeFromJSON(base);
    Node update =
        NodeUtilities.NodeFromJSON(
            new MapBuilder().put("foo", "foo-value").put("bar", "bar-value").build());
    compoundWrite = compoundWrite.addWrite(new Path("child-1"), update);
    compoundWrite = compoundWrite.removeWrite(new Path("child-1"));
    Assert.assertEquals(baseNode, compoundWrite.apply(baseNode));
  }

  @Test
  public void deepRemovesHasNoEffectOnOverlayingSet() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Map<String, Object> base =
        new MapBuilder().put("child-1", "value-1").put("child-2", "value-2").build();
    Node baseNode = NodeUtilities.NodeFromJSON(base);
    Node updateOne =
        NodeUtilities.NodeFromJSON(
            new MapBuilder().put("foo", "foo-value").put("bar", "bar-value").build());
    Node updateTwo = NodeUtilities.NodeFromJSON("baz-value");
    Node updateThree = NodeUtilities.NodeFromJSON("new-foo-value");
    compoundWrite = compoundWrite.addWrite(new Path("child-1"), updateOne);
    compoundWrite = compoundWrite.addWrite(new Path("child-1/baz"), updateTwo);
    compoundWrite = compoundWrite.addWrite(new Path("child-1/foo"), updateThree);
    compoundWrite = compoundWrite.removeWrite(new Path("child-1/foo"));
    Map<String, Object> expectedChildOne =
        new MapBuilder()
            .put("foo", "new-foo-value")
            .put("bar", "bar-value")
            .put("baz", "baz-value")
            .build();
    Node expected =
        baseNode.updateImmediateChild(
            ChildKey.fromString("child-1"), NodeUtilities.NodeFromJSON(expectedChildOne));
    Assert.assertEquals(expected, compoundWrite.apply(baseNode));
  }

  @Test
  public void removeAtPathWithoutSetIsWithoutEffect() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Map<String, Object> base =
        new MapBuilder().put("child-1", "value-1").put("child-2", "value-2").build();
    Node baseNode = NodeUtilities.NodeFromJSON(base);
    Node updateOne =
        NodeUtilities.NodeFromJSON(
            new MapBuilder().put("foo", "foo-value").put("bar", "bar-value").build());
    Node updateTwo = NodeUtilities.NodeFromJSON("baz-value");
    Node updateThree = NodeUtilities.NodeFromJSON("new-foo-value");
    compoundWrite = compoundWrite.addWrite(new Path("child-1"), updateOne);
    compoundWrite = compoundWrite.addWrite(new Path("child-1/baz"), updateTwo);
    compoundWrite = compoundWrite.addWrite(new Path("child-1/foo"), updateThree);
    compoundWrite = compoundWrite.removeWrite(new Path("child-2"));
    Map<String, Object> expectedChildOne =
        new MapBuilder()
            .put("foo", "new-foo-value")
            .put("bar", "bar-value")
            .put("baz", "baz-value")
            .build();
    Node expected =
        baseNode.updateImmediateChild(
            ChildKey.fromString("child-1"), NodeUtilities.NodeFromJSON(expectedChildOne));
    Assert.assertEquals(expected, compoundWrite.apply(baseNode));
  }

  @Test
  public void canRemovePriority() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(new Path(".priority"), PRIO_NODE);
    compoundWrite = compoundWrite.removeWrite(new Path(".priority"));
    assertNodeGetsCorrectPriority(compoundWrite, LEAF_NODE, PriorityUtilities.NullPriority());
  }

  @Test
  public void removingOnlyAffectsRemovedPath() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Map<String, Object> base =
        new MapBuilder().put("child-1", "value-1").put("child-2", "value-2").build();
    Map<ChildKey, Node> updates = new HashMap<ChildKey, Node>();
    updates.put(ChildKey.fromString("child-1"), NodeUtilities.NodeFromJSON("new-value-1"));
    updates.put(ChildKey.fromString("child-2"), EmptyNode.Empty());
    updates.put(ChildKey.fromString("child-3"), NodeUtilities.NodeFromJSON("value-3"));
    compoundWrite =
        compoundWrite.addWrites(Path.getEmptyPath(), CompoundWrite.fromChildMerge(updates));
    compoundWrite = compoundWrite.removeWrite(new Path("child-2"));

    Node baseNode = NodeUtilities.NodeFromJSON(base);
    Map<String, Object> expected =
        new MapBuilder()
            .put("child-1", "new-value-1")
            .put("child-2", "value-2")
            .put("child-3", "value-3")
            .build();
    Assert.assertEquals(NodeUtilities.NodeFromJSON(expected), compoundWrite.apply(baseNode));
  }

  @Test
  public void removeRemovesAllDeeperSets() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    Map<String, Object> base =
        new MapBuilder().put("child-1", "value-1").put("child-2", "value-2").build();
    Node baseNode = NodeUtilities.NodeFromJSON(base);
    Node updateTwo = NodeUtilities.NodeFromJSON("baz-value");
    Node updateThree = NodeUtilities.NodeFromJSON("new-foo-value");
    compoundWrite = compoundWrite.addWrite(new Path("child-1/baz"), updateTwo);
    compoundWrite = compoundWrite.addWrite(new Path("child-1/foo"), updateThree);
    compoundWrite = compoundWrite.removeWrite(new Path("child-1"));
    Assert.assertEquals(baseNode, compoundWrite.apply(baseNode));
  }

  @Test
  public void removeAtRootAlsoRemovesPriority() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(Path.getEmptyPath(), new StringNode("foo", PRIO_NODE));
    compoundWrite = compoundWrite.removeWrite(Path.getEmptyPath());
    Node node = NodeUtilities.NodeFromJSON("value");
    assertNodeGetsCorrectPriority(compoundWrite, node, EmptyNode.Empty());
  }

  @Test
  public void updatingPriorityDoesntOverwriteLeafNode() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(Path.getEmptyPath(), LEAF_NODE);
    compoundWrite = compoundWrite.addWrite(new Path("child/.priority"), PRIO_NODE);
    Assert.assertEquals(LEAF_NODE, compoundWrite.apply(EmptyNode.Empty()));
  }

  @Test
  public void updatingEmptyNodeDoesntOverwriteLeafNode() {
    CompoundWrite compoundWrite = CompoundWrite.emptyWrite();
    compoundWrite = compoundWrite.addWrite(Path.getEmptyPath(), LEAF_NODE);
    compoundWrite = compoundWrite.addWrite(new Path("child"), EmptyNode.Empty());
    Assert.assertEquals(LEAF_NODE, compoundWrite.apply(EmptyNode.Empty()));
  }
}
