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

import com.google.firebase.database.core.operation.Operation;
import com.google.firebase.database.core.view.CacheNode;
import com.google.firebase.database.core.view.Change;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.core.view.QueryParams;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.core.view.ViewCache;
import com.google.firebase.database.core.view.ViewProcessor;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.PriorityUtilities;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests the ViewProcessor implementation against randomly generated list of operations. After
 * applying each operation the current state and the generated changes are compared to the expected
 * outcome of that operation. This is useful for finding edge cases not previously tested against or
 * thought about (e.g. deleting a node which was just added in the step before)
 */
@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RandomViewProcessorTest {

  private static final int NUM_TESTS = 1000;
  private static final int OPERATIONS_PER_TEST = 300;

  // This test still fails in some weird edge cases, nonetheless it helped find several bugs
  // Activate the test if you want to do some more soul searching...
  @Test
  @Ignore
  public void randomOperations() {
    for (int i = 0; i < NUM_TESTS; i++) {
      RandomOperationGenerator generator = new RandomOperationGenerator();
      QueryParams params = generator.nextRandomParams();
      generator.listen(new QuerySpec(Path.getEmptyPath(), params));
      System.out.println("Test " + i);
      System.out.println("==========");
      System.out.println("Running with seed " + generator.getSeed());
      System.out.println("Query params: " + params);
      ViewProcessor processor = new ViewProcessor(params.getNodeFilter());
      CacheNode emptyCacheNode =
          new CacheNode(IndexedNode.from(EmptyNode.Empty(), params.getIndex()), false, false);
      Node currentChangeNode = EmptyNode.Empty();
      Node currentValueNode = null;
      ViewCache viewCache = new ViewCache(emptyCacheNode, emptyCacheNode);
      for (int j = 0; j < OPERATIONS_PER_TEST; j++) {
        Operation op = generator.nextOperation();
        System.out.println(j + ": Applying " + op);
        // TODO: get optCompleteServerCache
        WriteTreeRef writeTreeRef = new WriteTreeRef(Path.getEmptyPath(), generator.getWriteTree());
        ViewProcessor.ProcessorResult result =
            processor.applyOperation(viewCache, op, writeTreeRef, null);
        viewCache = result.viewCache;
        for (Change change : result.changes) {
          if (change.getEventType() == Event.EventType.VALUE) {
            currentValueNode = change.getIndexedNode().getNode();
          } else {
            currentChangeNode = applyChange(currentChangeNode, change);
          }
        }
        CacheNode expectedSnap = generator.getExpectedClientState(params);
        Node expectedNodeWithoutPriorities =
            expectedSnap.getNode().updatePriority(PriorityUtilities.NullPriority());
        CacheNode actualSnap = viewCache.getEventCache();
        Assert.assertTrue(
            "Event snap should be indexed",
            viewCache.getEventCache().getIndexedNode().hasIndex(params.getIndex()));
        if (expectedSnap.getNode().isLeafNode()) {
          assertNodesMatch(
              "Cache event snap did not equal expected event snap",
              expectedSnap.getNode(),
              actualSnap.getNode(),
              false);
          assertNodesMatch(
              "Value event snap did not match expected event snap",
              expectedSnap.getNode(),
              currentValueNode,
              false);
          Assert.assertTrue(
              "Change node did not remove all children for leaf node", currentChangeNode.isEmpty());
        } else {
          assertNodesMatch(
              "Cache event snap did not equal expected event snap",
              expectedSnap.getNode(),
              actualSnap.getNode(),
              false);
          assertNodesMatch(
              "Applying changes caused a diverge from the expected snap",
              expectedNodeWithoutPriorities,
              currentChangeNode,
              false);
          if (expectedSnap.isFullyInitialized()) {
            assertNodesMatch(
                "Value event snap did not match expected snap",
                expectedSnap.getNode(),
                currentValueNode,
                false);
          } else {
            Assert.assertNull("We shouldn't have gotten a value node yet!", currentValueNode);
          }
          Assert.assertEquals(expectedSnap.isFullyInitialized(), actualSnap.isFullyInitialized());
        }
      }
    }
  }

  private static Node applyChange(Node node, Change change) {
    if (change.getEventType() == Event.EventType.CHILD_ADDED) {
      Assert.assertFalse(node.hasChild(change.getChildKey()));
      return node.updateImmediateChild(change.getChildKey(), change.getIndexedNode().getNode());
    } else if (change.getEventType() == Event.EventType.CHILD_REMOVED) {
      Assert.assertTrue(node.hasChild(change.getChildKey()));
      Assert.assertEquals(
          node.getImmediateChild(change.getChildKey()), change.getIndexedNode().getNode());
      return node.updateImmediateChild(change.getChildKey(), EmptyNode.Empty());
    } else if (change.getEventType() == Event.EventType.CHILD_CHANGED) {
      Assert.assertTrue(node.hasChild(change.getChildKey()));
      Assert.assertEquals(
          node.getImmediateChild(change.getChildKey()), change.getOldIndexedNode().getNode());
      return node.updateImmediateChild(change.getChildKey(), change.getIndexedNode().getNode());
    } else {
      throw new IllegalArgumentException("Can't handle change of type: " + change.getEventType());
    }
  }

  private static Node removePriority(Node node) {
    node = node.updatePriority(PriorityUtilities.NullPriority());
    // this loop only triggers updates on children nodes, so we don't overwrite leaf nodes
    for (NamedNode childEntry : node) {
      node = node.updateImmediateChild(childEntry.getName(), removePriority(childEntry.getNode()));
    }
    return node;
  }

  private static void assertNodesMatch(String message, Node one, Node two, boolean checkPriority) {
    if (!checkPriority) {
      if (!one.equals(two)) {
        System.err.println(message + ": Priorities don't match!");
      }
      one = removePriority(one);
      two = removePriority(two);
    }
    Assert.assertEquals(message, one, two);
  }
}
