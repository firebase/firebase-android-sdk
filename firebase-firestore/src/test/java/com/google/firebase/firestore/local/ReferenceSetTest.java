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

package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.testutil.TestUtil.key;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.model.DocumentKey;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests ReferenceSet */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ReferenceSetTest {

  @Test
  public void testAddOrRemoveReferences() {
    DocumentKey key = key("foo/bar");

    ReferenceSet set = new ReferenceSet();
    assertFalse(set.containsKey(key));
    assertTrue(set.isEmpty());

    set.addReference(key, 1);
    assertTrue(set.containsKey(key));
    assertFalse(set.isEmpty());

    set.addReference(key, 2);
    assertTrue(set.containsKey(key));

    set.removeReference(key, 1);
    assertTrue(set.containsKey(key));

    set.removeReference(key, 3);
    assertTrue(set.containsKey(key));

    set.removeReference(key, 2);
    assertFalse(set.containsKey(key));
    assertTrue(set.isEmpty());
  }

  @Test
  public void testRemoveReferencesForId() {
    DocumentKey key1 = key("foo/bar");
    DocumentKey key2 = key("foo/baz");
    DocumentKey key3 = key("foo/blah");
    ReferenceSet set = new ReferenceSet();

    set.addReference(key1, 1);
    set.addReference(key2, 1);
    set.addReference(key3, 2);
    assertFalse(set.isEmpty());
    assertTrue(set.containsKey(key1));
    assertTrue(set.containsKey(key2));
    assertTrue(set.containsKey(key3));

    set.removeReferencesForId(1);
    assertFalse(set.isEmpty());
    assertFalse(set.containsKey(key1));
    assertFalse(set.containsKey(key2));
    assertTrue(set.containsKey(key3));

    set.removeReferencesForId(2);
    assertTrue(set.isEmpty());
    assertFalse(set.containsKey(key1));
    assertFalse(set.containsKey(key2));
    assertFalse(set.containsKey(key3));
  }
}
