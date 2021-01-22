// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.bundle;

import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.SnapshotVersion;
import java.util.List;

// TODO(bundles): Figure out whether we need this class (it is not needed in Web).

/** Metadata describing a Firestore document saved in the bundle. */
public class BundledDocumentMetadata implements BundleElement {
  private final DocumentKey key;
  private final SnapshotVersion readTime;
  private final boolean exists;
  private final List<String> queries;

  public BundledDocumentMetadata(
      DocumentKey key, SnapshotVersion readTime, boolean exists, List<String> queries) {
    this.key = key;
    this.readTime = readTime;
    this.exists = exists;
    this.queries = queries;
  }

  /** Returns the document key of a bundled document. */
  public DocumentKey getKey() {
    return key;
  }

  /** Returns the snapshot version of the document data bundled. */
  public SnapshotVersion getReadTime() {
    return readTime;
  }

  /** Returns whether the document exists. */
  public boolean exists() {
    return exists;
  }

  /** Returns the names of the queries in this bundle that this document matches to. */
  public List<String> getQueries() {
    return queries;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BundledDocumentMetadata that = (BundledDocumentMetadata) o;

    if (exists != that.exists) return false;
    if (!key.equals(that.key)) return false;
    if (!readTime.equals(that.readTime)) return false;
    return queries.equals(that.queries);
  }

  @Override
  public int hashCode() {
    int result = key.hashCode();
    result = 31 * result + readTime.hashCode();
    result = 31 * result + (exists ? 1 : 0);
    result = 31 * result + queries.hashCode();
    return result;
  }
}
