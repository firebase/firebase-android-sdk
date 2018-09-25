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

package com.google.firebase.firestore.core;

import static com.google.firebase.firestore.testutil.TestUtil.EMPTY_MAP;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static org.junit.Assert.assertEquals;

import com.google.firebase.firestore.core.DocumentViewChange.Type;
import com.google.firebase.firestore.model.Document;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests DocumentViewChangeSet */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DocumentViewChangeSetTest {

  @Test
  public void testDocumentViewChangeConstructor() {
    Document doc1 = doc("a/b", 0, EMPTY_MAP);
    Type type = Type.MODIFIED;
    DocumentViewChange change = DocumentViewChange.create(type, doc1);
    assertEquals(change.getDocument(), doc1);
    assertEquals(change.getType(), type);
  }

  @Test
  public void testTrack() {
    DocumentViewChangeSet set = new DocumentViewChangeSet();

    Document added = doc("a/1", 0, EMPTY_MAP);
    Document removed = doc("a/2", 0, EMPTY_MAP);
    Document modified = doc("a/3", 0, EMPTY_MAP);

    Document addedThenModified = doc("b/1", 0, EMPTY_MAP);
    Document addedThenRemoved = doc("b/2", 0, EMPTY_MAP);
    Document removedThenAdded = doc("b/3", 0, EMPTY_MAP);
    Document modifiedThenRemoved = doc("b/4", 0, EMPTY_MAP);
    Document modifiedThenModified = doc("b/5", 0, EMPTY_MAP);

    set.addChange(DocumentViewChange.create(Type.ADDED, added));
    set.addChange(DocumentViewChange.create(Type.REMOVED, removed));
    set.addChange(DocumentViewChange.create(Type.MODIFIED, modified));

    set.addChange(DocumentViewChange.create(Type.ADDED, addedThenModified));
    set.addChange(DocumentViewChange.create(Type.MODIFIED, addedThenModified));
    set.addChange(DocumentViewChange.create(Type.ADDED, addedThenRemoved));
    set.addChange(DocumentViewChange.create(Type.REMOVED, addedThenRemoved));
    set.addChange(DocumentViewChange.create(Type.REMOVED, removedThenAdded));
    set.addChange(DocumentViewChange.create(Type.ADDED, removedThenAdded));
    set.addChange(DocumentViewChange.create(Type.MODIFIED, modifiedThenRemoved));
    set.addChange(DocumentViewChange.create(Type.REMOVED, modifiedThenRemoved));
    set.addChange(DocumentViewChange.create(Type.MODIFIED, modifiedThenModified));
    set.addChange(DocumentViewChange.create(Type.MODIFIED, modifiedThenModified));

    List<DocumentViewChange> changes = set.getChanges();

    assertEquals(7, changes.size());
    assertEquals(added, changes.get(0).getDocument());
    assertEquals(Type.ADDED, changes.get(0).getType());
    assertEquals(removed, changes.get(1).getDocument());
    assertEquals(Type.REMOVED, changes.get(1).getType());
    assertEquals(modified, changes.get(2).getDocument());
    assertEquals(Type.MODIFIED, changes.get(2).getType());
    assertEquals(addedThenModified, changes.get(3).getDocument());
    assertEquals(Type.ADDED, changes.get(3).getType());
    assertEquals(removedThenAdded, changes.get(4).getDocument());
    assertEquals(Type.MODIFIED, changes.get(4).getType());
    assertEquals(modifiedThenRemoved, changes.get(5).getDocument());
    assertEquals(Type.REMOVED, changes.get(5).getType());
    assertEquals(modifiedThenModified, changes.get(6).getDocument());
    assertEquals(Type.MODIFIED, changes.get(6).getType());
  }
}
