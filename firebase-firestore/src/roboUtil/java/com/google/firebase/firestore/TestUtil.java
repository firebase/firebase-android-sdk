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
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static org.mockito.Mockito.mock;

import com.google.android.gms.tasks.Task;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.DocumentViewChange;
import com.google.firebase.firestore.core.DocumentViewChange.Type;
import com.google.firebase.firestore.core.ViewSnapshot;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.DocumentSet;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.ResourcePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.robolectric.Robolectric;

public class TestUtil {

  private static final FirebaseFirestore FIRESTORE = mock(FirebaseFirestore.class);

  public static FirebaseFirestore firestore() {
    return FIRESTORE;
  }

  public static CollectionReference collectionReference(String path) {
    return new CollectionReference(ResourcePath.fromString(path), FIRESTORE);
  }

  public static DocumentReference documentReference(String path) {
    return new DocumentReference(key(path), FIRESTORE);
  }

  public static DocumentSnapshot documentSnapshot(
      String path, Map<String, Object> data, boolean isFromCache) {
    if (data == null) {
      return DocumentSnapshot.fromNoDocument(
          FIRESTORE, key(path), isFromCache, /*hasPendingWrites=*/ false);
    } else {
      return DocumentSnapshot.fromDocument(
          FIRESTORE, doc(path, 1L, data), isFromCache, /*hasPendingWrites=*/ false);
    }
  }

  public static Query query(String path) {
    return new Query(com.google.firebase.firestore.testutil.TestUtil.query(path), FIRESTORE);
  }

  /**
   * A convenience method for creating a particular query snapshot for tests.
   *
   * @param path To be used in constructing the query.
   * @param oldDocs Provides the prior set of documents in the QuerySnapshot. Each entry maps to a
   *     document, with the key being the document id, and the value being the document contents.
   * @param docsToAdd Specifies data to be added into the query snapshot as of now. Each entry maps
   *     to a document, with the key being the document id, and the value being the document
   *     contents.
   * @param isFromCache Whether the query snapshot is cache result.
   * @return A query snapshot that consists of both sets of documents.
   */
  public static QuerySnapshot querySnapshot(
      String path,
      Map<String, ObjectValue> oldDocs,
      Map<String, ObjectValue> docsToAdd,
      boolean hasPendingWrites,
      boolean isFromCache) {
    DocumentSet oldDocuments = docSet(Document.keyComparator());
    ImmutableSortedSet<DocumentKey> mutatedKeys = DocumentKey.emptyKeySet();
    for (Map.Entry<String, ObjectValue> pair : oldDocs.entrySet()) {
      String docKey = path + "/" + pair.getKey();
      oldDocuments =
          oldDocuments.add(
              doc(
                  docKey,
                  1L,
                  pair.getValue(),
                  hasPendingWrites
                      ? Document.DocumentState.SYNCED
                      : Document.DocumentState.LOCAL_MUTATIONS));

      if (hasPendingWrites) {
        mutatedKeys = mutatedKeys.insert(key(docKey));
      }
    }
    DocumentSet newDocuments = docSet(Document.keyComparator());
    List<DocumentViewChange> documentChanges = new ArrayList<>();
    for (Map.Entry<String, ObjectValue> pair : docsToAdd.entrySet()) {
      String docKey = path + "/" + pair.getKey();
      Document docToAdd =
          doc(
              docKey,
              1L,
              pair.getValue(),
              hasPendingWrites
                  ? Document.DocumentState.SYNCED
                  : Document.DocumentState.LOCAL_MUTATIONS);
      newDocuments = newDocuments.add(docToAdd);
      documentChanges.add(DocumentViewChange.create(Type.ADDED, docToAdd));

      if (hasPendingWrites) {
        mutatedKeys = mutatedKeys.insert(key(docKey));
      }
    }
    ViewSnapshot viewSnapshot =
        new ViewSnapshot(
            com.google.firebase.firestore.testutil.TestUtil.query(path),
            newDocuments,
            oldDocuments,
            documentChanges,
            isFromCache,
            mutatedKeys,
            /* didSyncStateChange= */ true,
            /* excludesMetadataChanges= */ false);
    return new QuerySnapshot(query(path), viewSnapshot, FIRESTORE);
  }

  public static <T> T waitFor(Task<T> task) {
    if (!task.isComplete()) {
      Robolectric.flushBackgroundThreadScheduler();
    }
    Assert.assertTrue(
        "Expected task to be completed after background thread flush", task.isComplete());
    return task.getResult();
  }
}
