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

import static com.google.firebase.database.snapshot.NodeUtilities.NodeFromJSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.core.Path;
import java.util.Map;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class NodeTest {

  @Test
  public void getHashWorksCorrectly() {
    Map<String, Object> data =
        new MapBuilder()
            .put("intNode", 4)
            .put("doubleNode", 4.5623)
            .put("stringNode", "hey guys")
            .put("boolNode", true)
            .build();

    Node node = NodeFromJSON(data);

    Node child = node.getImmediateChild(ChildKey.fromString("intNode"));
    String hash = child.getHash();
    assertEquals("eVih19a6ZDz3NL32uVBtg9KSgQY=", hash);

    child = node.getImmediateChild(ChildKey.fromString("doubleNode"));
    hash = child.getHash();
    assertEquals("vf1CL0tIRwXXunHcG/irRECk3lY=", hash);

    child = node.getImmediateChild(ChildKey.fromString("stringNode"));
    hash = child.getHash();
    assertEquals("CUNLXWpCVoJE6z7z1vE57lGaKAU=", hash);

    child = node.getImmediateChild(ChildKey.fromString("boolNode"));
    hash = child.getHash();
    assertEquals("E5z61QM0lN/U2WsOnusszCTkR8M=", hash);

    hash = node.getHash();
    assertEquals("6Mc4jFmNdrLVIlJJjz2/MakTK9I=", hash);
  }

  @Test
  public void matchServerHash() {
    Map<String, Object> wireData =
        new MapBuilder()
            .put("c", new MapBuilder().put(".value", 99).put(".priority", "abc").build())
            .put(".priority", "def")
            .build();
    Node node = NodeFromJSON(wireData);
    node = EmptyNode.Empty().updateChild(new Path("root"), node);
    String hash = node.getHash();
    assertEquals("Fm6tzN4CVEu5WxFDZUdTtqbTVaA=", hash);
  }

  @Test
  public void leadingZeroesWorkCorrectly() {
    Map<String, Object> data =
        new MapBuilder().put("1", 1).put("01", 2).put("001", 3).put("0001", 4).build();

    Node node = NodeFromJSON(data);

    Node child = node.getImmediateChild(ChildKey.fromString("1"));
    assertEquals(1L, child.getValue());

    child = node.getImmediateChild(ChildKey.fromString("01"));
    assertEquals(2L, child.getValue());

    child = node.getImmediateChild(ChildKey.fromString("001"));
    assertEquals(3L, child.getValue());

    child = node.getImmediateChild(ChildKey.fromString("0001"));
    assertEquals(4L, child.getValue());
  }

  @Test
  public void leadingZerosDoNotOverwriteOtherKeys() {
    Map<String, Object> data =
        new MapBuilder()
            .put("1", "value1")
            .put("01", "value2")
            .put("001", "value3")
            .put("0001", "value4")
            .build();

    Node node = NodeFromJSON(data);
    assertEquals(node.getImmediateChild(ChildKey.fromString("1")).getValue(), "value1");
    assertEquals(node.getImmediateChild(ChildKey.fromString("01")).getValue(), "value2");
    assertEquals(node.getImmediateChild(ChildKey.fromString("001")).getValue(), "value3");
    assertEquals(node.getImmediateChild(ChildKey.fromString("0001")).getValue(), "value4");
  }

  @Test
  public void leadingZerosDoNotOverwriteKeysInValue() {
    Map<String, Object> data = new MapBuilder().put("1", "value1").put("01", "value2").build();

    Node node = NodeFromJSON(data);
    assertEquals(node.getValue(), data);
  }

  @Test
  public void emptyNodeEqualsEmptyChildrenNode() {
    assertEquals(EmptyNode.Empty(), new ChildrenNode());
    assertEquals(new ChildrenNode(), EmptyNode.Empty());
  }

  @Test
  public void updatingEmptyChildrenDoesntOverwriteLeafNode() {
    LeafNode<StringNode> node = new StringNode("value", PriorityUtilities.NullPriority());
    assertEquals(node, node.updateChild(new Path(".priority"), EmptyNode.Empty()));
    assertEquals(node, node.updateChild(new Path("child"), EmptyNode.Empty()));
    assertEquals(node, node.updateChild(new Path("child/.priority"), EmptyNode.Empty()));
    assertEquals(node, node.updateImmediateChild(ChildKey.fromString("child"), EmptyNode.Empty()));
    assertEquals(node, node.updateImmediateChild(ChildKey.getPriorityKey(), EmptyNode.Empty()));
  }

  @Test
  public void updatingPrioritiesOnEmptyNodesIsANoop() {
    Node priority = PriorityUtilities.parsePriority("prio");
    assertTrue(EmptyNode.Empty().updatePriority(priority).getPriority().isEmpty());
    assertTrue(
        EmptyNode.Empty().updateChild(new Path(".priority"), priority).getPriority().isEmpty());
    assertTrue(
        EmptyNode.Empty()
            .updateImmediateChild(ChildKey.getPriorityKey(), priority)
            .getPriority()
            .isEmpty());

    Node reemptiedChildren =
        EmptyNode.Empty()
            .updateChild(new Path("child"), NodeFromJSON("value"))
            .updateChild(new Path("child"), EmptyNode.Empty());
    assertTrue(reemptiedChildren.updatePriority(priority).getPriority().isEmpty());
    assertTrue(
        reemptiedChildren.updateChild(new Path(".priority"), priority).getPriority().isEmpty());
    assertTrue(
        reemptiedChildren
            .updateImmediateChild(ChildKey.getPriorityKey(), priority)
            .getPriority()
            .isEmpty());
  }

  @Test
  public void deletingLastChildFromChildrenNodeRemovesPriority() {
    Node priority = PriorityUtilities.parsePriority("prio");
    Node withPriority =
        EmptyNode.Empty()
            .updateChild(new Path("child"), NodeFromJSON("value"))
            .updatePriority(priority);
    assertEquals(priority, withPriority.getPriority());
    Node deletedChild = withPriority.updateChild(new Path("child"), EmptyNode.Empty());
    assertTrue(deletedChild.getPriority().isEmpty());
  }

  @Test
  public void nodeFromJsonReturnsEmptyNodesWithoutPriority() {
    Node empty1 = NodeFromJSON(new MapBuilder().put(".priority", "prio").build());
    assertTrue(empty1.getPriority().isEmpty());

    Node empty2 =
        NodeFromJSON(new MapBuilder().put("dummy-node", null).put(".priority", "prio").build());
    assertTrue(empty2.getPriority().isEmpty());
  }
}
