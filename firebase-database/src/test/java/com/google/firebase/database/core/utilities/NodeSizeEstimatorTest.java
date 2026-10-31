// Copyright 2026 Google LLC
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

package com.google.firebase.database.core.utilities;

import static com.google.firebase.database.core.utilities.NodeSizeEstimator.estimateSerializedNodeSize;
import static com.google.firebase.database.snapshot.NodeUtilities.NodeFromJSON;
import static org.junit.Assert.assertEquals;

import com.google.firebase.database.ListBuilder;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Node;
import java.util.Map;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class NodeSizeEstimatorTest {

  private static final Map<String, Object> SERVER_TIMESTAMP =
      new MapBuilder().put(".sv", "timestamp").build();

  @Test
  public void estimatesEmptyNode() {
    assertEquals(4, estimateSerializedNodeSize(EmptyNode.Empty()));
  }

  @Test
  public void estimatesLeafNodes() {
    assertEquals(7, estimateSerializedNodeSize(NodeFromJSON("hello")));
    assertEquals(8, estimateSerializedNodeSize(NodeFromJSON(42L)));
    assertEquals(8, estimateSerializedNodeSize(NodeFromJSON(4.5)));
    assertEquals(4, estimateSerializedNodeSize(NodeFromJSON(true)));
  }

  @Test
  public void estimatesLeafNodesWithPriority() {
    Node stringWithPriority =
        NodeFromJSON(new MapBuilder().put(".value", "hello").put(".priority", "prio").build());
    Node longWithPriority =
        NodeFromJSON(new MapBuilder().put(".value", 42L).put(".priority", 1.0).build());

    assertEquals(37, estimateSerializedNodeSize(stringWithPriority));
    assertEquals(40, estimateSerializedNodeSize(longWithPriority));
  }

  @Test
  public void estimatesChildrenNodes() {
    Node node = NodeFromJSON(new MapBuilder().put("a", "hello").put("b", 42L).build());
    assertEquals(26, estimateSerializedNodeSize(node));
  }

  @Test
  public void estimatesChildrenNodeWithPriority() {
    Node node =
        NodeFromJSON(
            new MapBuilder().put("a", "hello").put("b", 42L).put(".priority", "prio").build());
    assertEquals(44, estimateSerializedNodeSize(node));
  }

  @Test
  public void estimatesNestedChildrenNodes() {
    Node node =
        NodeFromJSON(new MapBuilder().put("x", new MapBuilder().put("y", true).build()).build());
    assertEquals(16, estimateSerializedNodeSize(node));
  }

  @Test
  public void estimatesChildrenNodeWithObjectPriority() {
    Node objectPriority = NodeFromJSON(new MapBuilder().put("k", "v").build());
    // A server overwrite at "<path>/.priority" sets the priority without validating it.
    Node node =
        NodeFromJSON(new MapBuilder().put("a", "hello").put("b", 42L).build())
            .updateImmediateChild(ChildKey.getPriorityKey(), objectPriority);

    // The priority is estimated like any other object.
    assertEquals(47, estimateSerializedNodeSize(node));
  }

  @Test
  public void estimatesLeafNodeWithArrayPriority() {
    Node arrayPriority = NodeFromJSON(new ListBuilder().put("a").put("b").build());
    Node node = NodeFromJSON("hello").updateChild(new Path(".priority"), arrayPriority);

    assertEquals(48, estimateSerializedNodeSize(node));
  }

  @Test
  public void estimatesServerValue() {
    Node node = NodeFromJSON(new MapBuilder().put("a", SERVER_TIMESTAMP).build());

    // An unresolved server value is estimated like a number.
    assertEquals(8, estimateSerializedNodeSize(NodeFromJSON(SERVER_TIMESTAMP)));
    assertEquals(14, estimateSerializedNodeSize(node));
  }

  @Test
  public void estimatesServerValuePriority() {
    Node leaf =
        NodeFromJSON(
            new MapBuilder().put(".value", "hello").put(".priority", SERVER_TIMESTAMP).build());
    Node children =
        NodeFromJSON(
            new MapBuilder()
                .put("a", "hello")
                .put("b", 42L)
                .put(".priority", SERVER_TIMESTAMP)
                .build());

    assertEquals(39, estimateSerializedNodeSize(leaf));
    assertEquals(46, estimateSerializedNodeSize(children));
  }
}
