// Copyright 2019 Google LLC
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

package com.google.firebase.firestore.testutil;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.UserDataReader;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.DocumentSet;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firestore.v1.Value;
import java.util.Comparator;
import java.util.Map;

/** A set of utilities for tests */
public class TestUtil {

  public static Value wrap(Object value) {
    DatabaseId databaseId = DatabaseId.forProject("project");
    UserDataReader dataReader = new UserDataReader(databaseId);
    // HACK: We use parseQueryValue() since it accepts scalars as well as arrays / objects, and
    // our tests currently use wrap() pretty generically so we don't know the intent.
    return dataReader.parseQueryValue(value);
  }

  public static ObjectValue wrapObject(Map<String, Object> value) {
    // Cast is safe here because value passed in is a map
    return new ObjectValue(wrap(value));
  }

  public static DocumentKey key(String key) {
    return DocumentKey.fromPathString(key);
  }

  public static ResourcePath path(String key) {
    return ResourcePath.fromString(key);
  }

  public static Query query(String path) {
    return Query.atPath(path(path));
  }

  public static SnapshotVersion version(long versionMicros) {
    long seconds = versionMicros / 1000000;
    int nanos = (int) (versionMicros % 1000000L) * 1000;
    return new SnapshotVersion(new Timestamp(seconds, nanos));
  }

  public static Document doc(String key, long version, Map<String, Object> data) {
    return new Document(
        key(key), version(version), wrapObject(data), Document.DocumentState.SYNCED);
  }

  public static Document doc(DocumentKey key, long version, Map<String, Object> data) {
    return new Document(key, version(version), wrapObject(data), Document.DocumentState.SYNCED);
  }

  public static Document doc(
      String key, long version, ObjectValue data, Document.DocumentState documentState) {
    return new Document(key(key), version(version), data, documentState);
  }

  public static Document doc(
      String key, long version, Map<String, Object> data, Document.DocumentState documentState) {
    return new Document(key(key), version(version), wrapObject(data), documentState);
  }

  public static DocumentSet docSet(Comparator<Document> comparator, Document... documents) {
    DocumentSet set = DocumentSet.emptySet(comparator);
    for (Document document : documents) {
      set = set.add(document);
    }
    return set;
  }
}
