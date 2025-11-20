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

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.TestUtil.EMPTY_MAP;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static org.junit.Assert.assertEquals;

import com.google.firebase.firestore.core.DocumentViewChange.Type;
import com.google.firebase.firestore.model.MutableDocument;
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
    MutableDocument doc1 = doc("a/b", 0, EMPTY_MAP);
    Type type = Type.MODIFIED;
    DocumentViewChange change = DocumentViewChange.create(type, doc1);
    assertEquals(change.getDocument(), doc1);
    assertEquals(change.getType(), type);
  }

  @Test
  public void testTrack() {
    DocumentViewChangeSet set = new DocumentViewChangeSet();

    MutableDocument added = doc("a/1", 0, EMPTY_MAP);
    MutableDocument removed = doc("a/2", 0, EMPTY_MAP);
    MutableDocument modified = doc("a/3", 0, EMPTY_MAP);

    MutableDocument addedThenModified = doc("b/1", 0, EMPTY_MAP);
    MutableDocument addedThenRemoved = doc("b/2", 0, EMPTY_MAP);
    MutableDocument removedThenAdded = doc("b/3", 0, EMPTY_MAP);
    MutableDocument modifiedThenRemoved = doc("b/4", 0, EMPTY_MAP);
    MutableDocument modifiedThenModified = doc("b/5", 0, EMPTY_MAP);

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

    assertThat(changes)
        .containsExactly(
            DocumentViewChange.create(Type.ADDED, added),
            DocumentViewChange.create(Type.REMOVED, removed),
            DocumentViewChange.create(Type.MODIFIED, modified),
            DocumentViewChange.create(Type.ADDED, addedThenModified),
            DocumentViewChange.create(Type.MODIFIED, removedThenAdded),
            DocumentViewChange.create(Type.REMOVED, modifiedThenRemoved),
            DocumentViewChange.create(Type.MODIFIED, modifiedThenModified));
  }
}
