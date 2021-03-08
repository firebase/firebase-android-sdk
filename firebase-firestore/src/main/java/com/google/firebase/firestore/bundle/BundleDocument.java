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
import com.google.firebase.firestore.model.MutableDocument;

/** A document that was saved to a bundle. */
public class BundleDocument implements BundleElement {
  private MutableDocument document;

  public BundleDocument(MutableDocument document) {
    this.document = document;
  }

  /** Returns the key for this document. */
  public DocumentKey getKey() {
    return document.getKey();
  }

  /** Returns the document. */
  public MutableDocument getDocument() {
    return document;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BundleDocument that = (BundleDocument) o;

    return document.equals(that.document);
  }

  @Override
  public int hashCode() {
    return document.hashCode();
  }
}
