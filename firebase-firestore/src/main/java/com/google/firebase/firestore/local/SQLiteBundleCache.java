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

package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.util.Assert.fail;

import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.bundle.BundleMetadata;
import com.google.firebase.firestore.bundle.NamedQuery;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firestore.bundle.BundledQuery;
import com.google.protobuf.InvalidProtocolBufferException;

class SQLiteBundleCache implements BundleCache {
  private final SQLitePersistence db;
  private final LocalSerializer serializer;

  SQLiteBundleCache(SQLitePersistence persistence, LocalSerializer serializer) {
    this.db = persistence;
    this.serializer = serializer;
  }

  @Nullable
  @Override
  public BundleMetadata getBundleMetadata(String bundleId) {
    return db.query(
            "SELECT schema_version, create_time_seconds, create_time_nanos, total_documents, "
                + " total_bytes FROM bundles WHERE bundle_id = ?")
        .binding(bundleId)
        .firstValue(
            row ->
                row == null
                    ? null
                    : new BundleMetadata(
                        bundleId,
                        row.getInt(0),
                        new SnapshotVersion(new Timestamp(row.getLong(1), row.getInt(2))),
                        row.getInt(3),
                        row.getLong(4)));
  }

  @Override
  public void saveBundleMetadata(BundleMetadata metadata) {
    db.execute(
        "INSERT OR REPLACE INTO bundles "
            + "(bundle_id, schema_version, create_time_seconds, create_time_nanos, "
            + "total_documents, total_bytes) VALUES (?, ?, ?, ?, ?, ?)",
        metadata.getBundleId(),
        metadata.getSchemaVersion(),
        metadata.getCreateTime().getTimestamp().getSeconds(),
        metadata.getCreateTime().getTimestamp().getNanoseconds(),
        metadata.getTotalDocuments(),
        metadata.getTotalBytes());
  }

  @Override
  @Nullable
  public NamedQuery getNamedQuery(String queryName) {
    return db.query(
            "SELECT read_time_seconds, read_time_nanos, bundled_query_proto "
                + "FROM named_queries WHERE name = ?")
        .binding(queryName)
        .firstValue(
            row -> {
              if (row != null) {
                try {
                  BundledQuery bundledQuery = BundledQuery.parseFrom(row.getBlob(2));
                  return new NamedQuery(
                      queryName,
                      serializer.decodeBundledQuery(bundledQuery),
                      new SnapshotVersion(new Timestamp(row.getLong(0), row.getInt(1))));
                } catch (InvalidProtocolBufferException e) {
                  throw fail("NamedQuery failed to parse: %s", e);
                }
              }

              return null;
            });
  }

  @Override
  public void saveNamedQuery(NamedQuery query) {
    BundledQuery bundledQuery = serializer.encodeBundledQuery(query.getBundledQuery());

    db.execute(
        "INSERT OR REPLACE INTO named_queries "
            + "(name, read_time_seconds, read_time_nanos, bundled_query_proto) "
            + "VALUES (?, ?, ?, ?)",
        query.getName(),
        query.getReadTime().getTimestamp().getSeconds(),
        query.getReadTime().getTimestamp().getNanoseconds(),
        bundledQuery.toByteArray());
  }
}
