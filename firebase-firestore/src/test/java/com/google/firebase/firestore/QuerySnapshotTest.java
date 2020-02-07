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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.docSet;
import static com.google.firebase.firestore.testutil.TestUtil.keySet;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.wrapObject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior;
import com.google.firebase.firestore.core.DocumentViewChange;
import com.google.firebase.firestore.core.ViewSnapshot;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentSet;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.ServerTimestamps;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class QuerySnapshotTest {

  public static class POJO {
    public Date timestamp;
  }

  @Test
  public void testEquals() {
    ObjectValue firstValue = wrapObject("a", 1);
    ObjectValue secondValue = wrapObject("b", 1);

    QuerySnapshot foo = TestUtil.querySnapshot("foo", map(), map("a", firstValue), true, false);
    QuerySnapshot fooDup = TestUtil.querySnapshot("foo", map(), map("a", firstValue), true, false);
    QuerySnapshot differentPath =
        TestUtil.querySnapshot("bar", map(), map("a", firstValue), true, false);
    QuerySnapshot differentDoc =
        TestUtil.querySnapshot("foo", map(), map("a", secondValue), true, false);
    QuerySnapshot noPendingWrites =
        TestUtil.querySnapshot("foo", map(), map("a", firstValue), false, false);
    QuerySnapshot fromCache =
        TestUtil.querySnapshot("foo", map(), map("a", firstValue), true, true);
    assertEquals(foo, fooDup);
    assertNotEquals(foo, differentPath);
    assertNotEquals(foo, differentDoc);
    assertNotEquals(foo, noPendingWrites);
    assertNotEquals(foo, fromCache);

    // Note: `foo` and `differentDoc` have the same hash code since we no longer take document
    // contents into account.
    assertEquals(foo.hashCode(), fooDup.hashCode());
    assertNotEquals(foo.hashCode(), differentPath.hashCode());
    assertNotEquals(foo.hashCode(), noPendingWrites.hashCode());
    assertNotEquals(foo.hashCode(), fromCache.hashCode());
  }

  @Test
  public void testToObjects() {
    // Prevent NPE on trying to access non-existent settings on the mock.
    when(TestUtil.firestore().getFirestoreSettings())
        .thenReturn(new FirebaseFirestoreSettings.Builder().build());

    ObjectValue objectData =
        ObjectValue.fromMap(map("timestamp", ServerTimestamps.valueOf(Timestamp.now(), null)));
    QuerySnapshot foo = TestUtil.querySnapshot("foo", map(), map("a", objectData), true, false);

    List<POJO> docs = foo.toObjects(POJO.class);
    assertEquals(1, docs.size());
    assertNull(docs.get(0).timestamp);

    docs = foo.toObjects(POJO.class, ServerTimestampBehavior.ESTIMATE);
    assertEquals(1, docs.size());
    assertNotNull(docs.get(0).timestamp);
  }

  @Test
  public void testIncludeMetadataChanges() {
    Document doc1Old =
        doc("foo/bar", 1, wrapObject("a", "b"), Document.DocumentState.LOCAL_MUTATIONS);
    Document doc1New = doc("foo/bar", 1, wrapObject("a", "b"), Document.DocumentState.SYNCED);

    Document doc2Old = doc("foo/baz", 1, wrapObject("a", "b"), Document.DocumentState.SYNCED);
    Document doc2New = doc("foo/baz", 1, wrapObject("a", "c"), Document.DocumentState.SYNCED);

    DocumentSet oldDocuments = docSet(Document.keyComparator(), doc1Old, doc2Old);
    DocumentSet newDocuments = docSet(Document.keyComparator(), doc1New, doc2New);

    List<DocumentViewChange> documentChanges =
        Arrays.asList(
            DocumentViewChange.create(DocumentViewChange.Type.METADATA, doc1New),
            DocumentViewChange.create(DocumentViewChange.Type.MODIFIED, doc2New));

    FirebaseFirestore firestore = TestUtil.firestore();
    com.google.firebase.firestore.core.Query fooQuery = query("foo");
    ViewSnapshot viewSnapshot =
        new ViewSnapshot(
            fooQuery,
            newDocuments,
            oldDocuments,
            documentChanges,
            /*isFromCache=*/ false,
            /*mutatedKeys=*/ keySet(),
            /*didSyncStateChange=*/ true,
            /* excludesMetadataChanges= */ false);

    QuerySnapshot snapshot =
        new QuerySnapshot(new Query(fooQuery, firestore), viewSnapshot, firestore);

    QueryDocumentSnapshot doc1Snap =
        QueryDocumentSnapshot.fromDocument(
            firestore, doc1New, /*fromCache=*/ false, /*hasPendingWrites=*/ false);
    QueryDocumentSnapshot doc2Snap =
        QueryDocumentSnapshot.fromDocument(
            firestore, doc2New, /*fromCache=*/ false, /*hasPendingWrites=*/ false);

    assertEquals(1, snapshot.getDocumentChanges().size());
    List<DocumentChange> changesWithoutMetadata =
        Arrays.asList(
            new DocumentChange(
                doc2Snap, DocumentChange.Type.MODIFIED, /*oldIndex=*/ 1, /*newIndex=*/ 1));
    assertEquals(changesWithoutMetadata, snapshot.getDocumentChanges());

    List<DocumentChange> changesWithMetadata =
        Arrays.asList(
            new DocumentChange(
                doc1Snap, DocumentChange.Type.MODIFIED, /*oldIndex=*/ 0, /*newIndex=*/ 0),
            new DocumentChange(
                doc2Snap, DocumentChange.Type.MODIFIED, /*oldIndex=*/ 1, /*newIndex=*/ 1));
    assertEquals(changesWithMetadata, snapshot.getDocumentChanges(MetadataChanges.INCLUDE));
  }
}
