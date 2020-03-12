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

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.NonNull;
import com.google.firebase.database.collection.ImmutableSortedSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** DocumentKey represents the location of a document in the Firestore database. */
public final class DocumentKey implements Comparable<DocumentKey> {

  public static final String KEY_FIELD_NAME = "__name__";

  private static final Comparator<DocumentKey> COMPARATOR = DocumentKey::compareTo;

  private static final ImmutableSortedSet<DocumentKey> EMPTY_KEY_SET =
      new ImmutableSortedSet<>(Collections.emptyList(), COMPARATOR);

  /** Returns a comparator for DocumentKeys */
  public static Comparator<DocumentKey> comparator() {
    return COMPARATOR;
  }

  /** Returns an empty immutable key set */
  public static ImmutableSortedSet<DocumentKey> emptyKeySet() {
    return EMPTY_KEY_SET;
  }

  /** Returns a document key for the empty path. */
  public static DocumentKey empty() {
    return fromSegments(Collections.emptyList());
  }

  /** Returns a DocumentKey from a fully qualified resource name. */
  public static DocumentKey fromName(String name) {
    ResourcePath resourceName = ResourcePath.fromString(name);
    hardAssert(
        resourceName.length() >= 4
            && resourceName.getSegment(0).equals("projects")
            && resourceName.getSegment(2).equals("databases")
            && resourceName.getSegment(4).equals("documents"),
        "Tried to parse an invalid key: %s",
        resourceName);
    return DocumentKey.fromPath(resourceName.popFirst(5));
  }

  /**
   * Creates and returns a new document key with the given path.
   *
   * @param path The path to the document
   * @return A new instance of DocumentKey
   */
  public static DocumentKey fromPath(ResourcePath path) {
    return new DocumentKey(path);
  }

  /**
   * Creates and returns a new document key with the given segments.
   *
   * @param segments The segments of the path to the document
   * @return A new instance of DocumentKey
   */
  public static DocumentKey fromSegments(List<String> segments) {
    return new DocumentKey(ResourcePath.fromSegments(segments));
  }

  /**
   * Creates and returns a new document key using '/' to split the string into segments.
   *
   * @param path The slash-separated path string to the document
   * @return A new instance of DocumentKey
   */
  public static DocumentKey fromPathString(String path) {
    return new DocumentKey(ResourcePath.fromString(path));
  }

  /** Returns true iff the given path is a path to a document. */
  public static boolean isDocumentKey(ResourcePath path) {
    return path.length() % 2 == 0;
  }

  /** The path to the document. */
  private final ResourcePath path;

  private DocumentKey(ResourcePath path) {
    hardAssert(isDocumentKey(path), "Not a document key path: %s", path);
    this.path = path;
  }

  /** Returns the path of to the document */
  public ResourcePath getPath() {
    return path;
  }

  /** Returns true if the document is in the specified collectionId. */
  public boolean hasCollectionId(String collectionId) {
    return path.length() >= 2 && path.segments.get(path.length() - 2).equals(collectionId);
  }

  @Override
  public int compareTo(@NonNull DocumentKey another) {
    return path.compareTo(another.path);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DocumentKey that = (DocumentKey) o;

    return path.equals(that.path);
  }

  @Override
  public int hashCode() {
    return path.hashCode();
  }

  @Override
  public String toString() {
    return path.toString();
  }
}
