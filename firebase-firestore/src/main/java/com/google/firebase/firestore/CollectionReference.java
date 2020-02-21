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

import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.util.Executors;
import com.google.firebase.firestore.util.Util;

/**
 * A {@code CollectionReference} can be used for adding documents, getting document references, and
 * querying for documents (using the methods inherited from {@code Query}).
 *
 * <p><b>Subclassing Note</b>: Cloud Firestore classes are not meant to be subclassed except for use
 * in test mocks. Subclassing is not supported in production code and new SDK releases may break
 * code that does so.
 */
public class CollectionReference extends Query {

  CollectionReference(ResourcePath path, FirebaseFirestore firestore) {
    super(com.google.firebase.firestore.core.Query.atPath(path), firestore);
    if (path.length() % 2 != 1) {
      throw new IllegalArgumentException(
          "Invalid collection reference. Collection references must have an odd number "
              + "of segments, but "
              + path.canonicalString()
              + " has "
              + path.length());
    }
  }

  /** @return The ID of the collection. */
  @NonNull
  public String getId() {
    return query.getPath().getLastSegment();
  }

  /**
   * Gets a {@code DocumentReference} to the document that contains this collection. Only
   * subcollections are contained in a document. For root collections, returns {@code null}.
   *
   * @return The {@code DocumentReference} that contains this collection or {@code null} if this is
   *     a root collection.
   */
  @Nullable
  public DocumentReference getParent() {
    ResourcePath parentPath = query.getPath().popLast();
    if (parentPath.isEmpty()) {
      return null;
    } else {
      return new DocumentReference(DocumentKey.fromPath(parentPath), firestore);
    }
  }

  /**
   * Gets the path of this collection (relative to the root of the database) as a slash-separated
   * string.
   *
   * @return The path of this collection.
   */
  @NonNull
  public String getPath() {
    return query.getPath().canonicalString();
  }

  /**
   * Returns a {@code DocumentReference} pointing to a new document with an auto-generated ID within
   * this collection.
   *
   * @return A {@code DocumentReference} pointing to a new document with an auto-generated ID.
   */
  @NonNull
  public DocumentReference document() {
    return document(Util.autoId());
  }

  /**
   * Gets a {@code DocumentReference} instance that refers to the document at the specified path
   * within this collection.
   *
   * @param documentPath A slash-separated relative path to a document.
   * @return The {@code DocumentReference} instance.
   */
  @NonNull
  public DocumentReference document(@NonNull String documentPath) {
    checkNotNull(documentPath, "Provided document path must not be null.");
    return DocumentReference.forPath(
        query.getPath().append(ResourcePath.fromString(documentPath)), firestore);
  }

  /**
   * Adds a new document to this collection with the specified data, assigning it a document ID
   * automatically.
   *
   * @param data The data to write to the document (e.g. a Map or a POJO containing the desired
   *     document contents).
   * @return A Task that will be resolved with the {@code DocumentReference} of the newly created
   *     document.
   */
  @NonNull
  public Task<DocumentReference> add(@NonNull Object data) {
    checkNotNull(data, "Provided data must not be null.");
    final DocumentReference ref = document();
    return ref.set(data)
        .continueWith(
            Executors.DIRECT_EXECUTOR,
            task -> {
              // Make sure the result or error is propagated by accessing the result.
              task.getResult();
              return ref;
            });
  }
}
