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

package com.google.firebase.firestore.conformance;

import java.util.ArrayList;
import java.util.List;

/** A simplified representation of a Firestore collection for testing. */
public final class TestCollection {
  // Note: This code is copied from Google3.

  private final String path;
  private final ArrayList<TestDocument> documents = new ArrayList<>();

  TestCollection(String path) {
    this.path = path;
  }

  public TestDocument addDocumentWithId(String id) {
    TestDocument document = new TestDocument(id);
    documents.add(document);
    return document;
  }

  public String path() {
    return path;
  }

  public List<TestDocument> documents() {
    return documents;
  }
}
