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

/** Represents a particular database in Firestore */
public final class DatabaseId implements Comparable<DatabaseId> {
  public static final String DEFAULT_DATABASE_ID = "(default)";

  public static DatabaseId forProject(String projectId) {
    return forDatabase(projectId, DEFAULT_DATABASE_ID);
  }

  public static DatabaseId forDatabase(String projectId, String databaseId) {
    return new DatabaseId(projectId, databaseId);
  }

  private final String projectId;

  private final String databaseId;

  private DatabaseId(String projectId, String databaseId) {
    this.projectId = projectId;
    this.databaseId = databaseId;
  }

  /** Returns a DatabaseId from a fully qualified resource name. */
  public static DatabaseId fromName(String name) {
    ResourcePath resourceName = ResourcePath.fromString(name);
    hardAssert(
        resourceName.length() >= 3
            && resourceName.getSegment(0).equals("projects")
            && resourceName.getSegment(2).equals("databases"),
        "Tried to parse an invalid resource name: %s",
        resourceName);
    return new DatabaseId(resourceName.getSegment(1), resourceName.getSegment(3));
  }

  public String getProjectId() {
    return projectId;
  }

  public String getDatabaseId() {
    return databaseId;
  }

  @Override
  public String toString() {
    return "DatabaseId(" + projectId + ", " + databaseId + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DatabaseId that = (DatabaseId) o;

    return projectId.equals(that.projectId) && databaseId.equals(that.databaseId);
  }

  @Override
  public int hashCode() {
    int result = projectId.hashCode();
    result = 31 * result + databaseId.hashCode();
    return result;
  }

  @Override
  public int compareTo(@NonNull DatabaseId other) {
    int cmp = projectId.compareTo(other.projectId);
    return cmp != 0 ? cmp : databaseId.compareTo(other.databaseId);
  }
}
