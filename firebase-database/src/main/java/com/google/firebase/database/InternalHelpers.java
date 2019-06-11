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

package com.google.firebase.database;

import androidx.annotation.RestrictTo;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.Repo;
import com.google.firebase.database.core.RepoInfo;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.Node;

/**
 * Internal helpers in com.google.firebase.database package (for use by core, tests, etc.)
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class InternalHelpers {

  /** So Repo, etc. can create DatabaseReference instances. */
  public static DatabaseReference createReference(Repo repo, Path path) {
    return new DatabaseReference(repo, path);
  }

  /** So Repo, etc. can create DataSnapshots. */
  public static DataSnapshot createDataSnapshot(DatabaseReference ref, IndexedNode node) {
    return new DataSnapshot(ref, node);
  }

  /** So Repo can create FirebaseDatabase objects to keep legacy tests working. */
  public static FirebaseDatabase createDatabaseForTests(
      FirebaseApp app, RepoInfo repoInfo, DatabaseConfig config) {
    return FirebaseDatabase.createForTests(app, repoInfo, config);
  }

  /** For Repo to create MutableData objects. */
  public static MutableData createMutableData(Node node) {
    return new MutableData(node);
  }
}
