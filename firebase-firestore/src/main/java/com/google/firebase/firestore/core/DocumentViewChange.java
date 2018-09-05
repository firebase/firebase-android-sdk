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

import com.google.firebase.firestore.model.Document;

/** A change to a single document's state within a view. */
public class DocumentViewChange {
  /**
   * The types of changes that can happen to a document with respect to a view. NOTE: We sort
   * document changes by their type, so the ordering of this enum is significant.
   */
  public enum Type {
    REMOVED,
    ADDED,
    MODIFIED,
    METADATA
  }

  public static DocumentViewChange create(Type type, Document document) {
    return new DocumentViewChange(type, document);
  }

  private final Type type;

  private final Document document;

  private DocumentViewChange(Type type, Document document) {
    this.type = type;
    this.document = document;
  }

  public Document getDocument() {
    return document;
  }

  public Type getType() {
    return type;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DocumentViewChange)) {
      return false;
    }
    DocumentViewChange other = (DocumentViewChange) o;

    return type.equals(other.type) && document.equals(other.document);
  }

  @Override
  public int hashCode() {
    int res = 61;
    res = res * 31 + type.hashCode();
    res = res * 31 + document.hashCode();
    return res;
  }

  @Override
  public String toString() {
    return "DocumentViewChange(" + document + "," + type + ")";
  }
}
