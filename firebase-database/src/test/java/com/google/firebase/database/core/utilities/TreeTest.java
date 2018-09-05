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

package com.google.firebase.database.core.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.ChildKey;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class TreeTest {

  @Test
  public void testDeletion() {
    /*
     * At one point, we had a concurrent modification exception in the Tree code, so just make sure
     * this doesn't throw a ConcurrentModification exception
     */
    Tree<String> root = new Tree<String>();
    Path path = new Path("foo/bar");
    root.subTree(path).setValue("bar");
    root.subTree(path.getParent().child(ChildKey.fromString("baz"))).setValue("baz");
    Tree<String> intermediate = root.subTree(new Path("foo"));
    intermediate.forEachChild(
        new Tree.TreeVisitor<String>() {
          @Override
          public void visitTree(Tree<String> tree) {
            if (tree.getName().equals(ChildKey.fromString("baz"))) {
              tree.setValue(null);
            }
          }
        });
    String result = root.subTree(new Path("foo/bar")).getValue();
    assertEquals("bar", result);
    result = root.subTree(new Path("foo/baz")).getValue();
    assertNull(result);
  }
}
