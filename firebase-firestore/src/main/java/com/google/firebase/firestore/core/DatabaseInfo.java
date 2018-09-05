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

import com.google.firebase.firestore.model.DatabaseId;

/** Contains info about host, project id and database */
public final class DatabaseInfo {

  private final DatabaseId databaseId;
  private final String persistenceKey;
  private final String host;
  private final boolean sslEnabled;

  /**
   * Constructs a new DatabaseInfo.
   *
   * @param databaseId The Google Cloud Project ID and database naming the Firestore instance.
   * @param persistenceKey A unique identifier for this Firestore's local storage. Usually derived
   *     from FirebaseApp.name.
   * @param host The hostname of the backend.
   * @param sslEnabled Whether to use SSL when connecting.
   */
  public DatabaseInfo(
      DatabaseId databaseId, String persistenceKey, String host, boolean sslEnabled) {
    this.databaseId = databaseId;
    this.persistenceKey = persistenceKey;
    this.host = host;
    this.sslEnabled = sslEnabled;
  }

  public DatabaseId getDatabaseId() {
    return databaseId;
  }

  public String getPersistenceKey() {
    return persistenceKey;
  }

  public String getHost() {
    return host;
  }

  public boolean isSslEnabled() {
    return sslEnabled;
  }

  @Override
  public String toString() {
    return "DatabaseInfo(databaseId:" + databaseId + " host:" + host + ")";
  }
}
