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

import com.google.firebase.firestore.model.SnapshotVersion;

/** Represents a Firestore bundle saved by the SDK in its local storage. */
public class BundleMetadata implements BundleElement {
  private final String bundleId;
  private final int schemaVersion;
  private final SnapshotVersion createTime;
  private final int totalDocuments;
  private final long totalBytes;

  public BundleMetadata(
      String bundleId,
      int schemaVersion,
      SnapshotVersion createTime,
      int totalDocuments,
      long totalBytes) {
    this.bundleId = bundleId;
    this.schemaVersion = schemaVersion;
    this.createTime = createTime;
    this.totalDocuments = totalDocuments;
    this.totalBytes = totalBytes;
  }

  /**
   * Returns the ID of the bundle. It is used together with `createTime` to determine if a bundle
   * has been loaded by the SDK.
   */
  public String getBundleId() {
    return bundleId;
  }

  /** Returns the schema version of the bundle. */
  public int getSchemaVersion() {
    return schemaVersion;
  }

  /**
   * Returns the snapshot version of the bundle if created by the Server SDKs, or else
   * SnapshotVersion.MIN.
   */
  public SnapshotVersion getCreateTime() {
    return createTime;
  }

  /** Returns the number of documents in the bundle. */
  public int getTotalDocuments() {
    return totalDocuments;
  }

  /** Returns the size of the bundle in bytes, excluding this BundleMetadata. */
  public long getTotalBytes() {
    return totalBytes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BundleMetadata that = (BundleMetadata) o;

    if (schemaVersion != that.schemaVersion) return false;
    if (totalDocuments != that.totalDocuments) return false;
    if (totalBytes != that.totalBytes) return false;
    if (!bundleId.equals(that.bundleId)) return false;
    return createTime.equals(that.createTime);
  }

  @Override
  public int hashCode() {
    int result = bundleId.hashCode();
    result = 31 * result + schemaVersion;
    result = 31 * result + totalDocuments;
    result = 31 * result + (int) (totalBytes ^ (totalBytes >>> 32));
    result = 31 * result + createTime.hashCode();
    return result;
  }
}
