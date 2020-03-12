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

package com.google.firebase.firestore.model;

import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static com.google.firebase.firestore.testutil.TestUtil.wrapObject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DocumentTest {

  @Test
  public void testInstantiation() {
    Document document =
        new Document(
            key("messages/first"), version(1), wrapObject("a", 1), Document.DocumentState.SYNCED);

    assertEquals(key("messages/first"), document.getKey());
    assertEquals(version(1), document.getVersion());
    assertEquals(wrapObject("a", 1), document.getData());
    assertFalse(document.hasLocalMutations());
  }

  @Test
  public void testIsEqual() {
    String key1 = "messages/first";
    String key2 = "messages/second";
    Map<String, Object> data1 = map("a", 1);
    Map<String, Object> data2 = map("a", 2);
    Document doc1 = doc(key1, 1, data1);
    Document doc2 = doc(key1, 1, data1);

    assertEquals(doc1, doc2);
    assertEquals(doc1, doc("messages/first", 1, map("a", 1)));

    assertNotEquals(doc1, doc(key1, 1, data2));
    assertNotEquals(doc1, doc(key2, 1, data1));
    assertNotEquals(doc1, doc(key1, 2, data1));
    assertNotEquals(doc1, doc(key1, 1, data1, Document.DocumentState.LOCAL_MUTATIONS));
  }
}
