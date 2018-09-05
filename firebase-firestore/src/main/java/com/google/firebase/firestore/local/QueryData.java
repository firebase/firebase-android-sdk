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

package com.google.firebase.firestore.local;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.remote.WatchStream;
import com.google.protobuf.ByteString;

/** An immutable set of metadata that the store will need to keep track of for each query. */
public final class QueryData {
  private final Query query;
  private final int targetId;
  private final long sequenceNumber;
  private final QueryPurpose purpose;
  private final SnapshotVersion snapshotVersion;
  private final ByteString resumeToken;

  /**
   * Creates a new QueryData with the given values.
   *
   * @param query The query being listened to.
   * @param targetId The target to which the query corresponds, assigned by the LocalStore for user
   *     queries or the SyncEngine for limbo queries.
   * @param purpose The purpose of the query.
   * @param snapshotVersion The latest snapshot version seen for this target.
   * @param resumeToken An opaque, server-assigned token that allows watching a query to be resumed
   *     after disconnecting without retransmitting all the data that matches the query. The resume
   *     token essentially identifies a point in time from which the server should resume sending
   *     results.
   */
  public QueryData(
      Query query,
      int targetId,
      long sequenceNumber,
      QueryPurpose purpose,
      SnapshotVersion snapshotVersion,
      ByteString resumeToken) {
    this.query = checkNotNull(query);
    this.targetId = targetId;
    this.sequenceNumber = sequenceNumber;
    this.purpose = purpose;
    this.snapshotVersion = checkNotNull(snapshotVersion);
    this.resumeToken = checkNotNull(resumeToken);
  }

  /** Convenience constructor for use when creating a QueryData for the first time. */
  public QueryData(Query query, int targetId, long sequenceNumber, QueryPurpose purpose) {
    this(
        query,
        targetId,
        sequenceNumber,
        purpose,
        SnapshotVersion.NONE,
        WatchStream.EMPTY_RESUME_TOKEN);
  }

  public Query getQuery() {
    return query;
  }

  public int getTargetId() {
    return targetId;
  }

  public long getSequenceNumber() {
    return sequenceNumber;
  }

  public QueryPurpose getPurpose() {
    return purpose;
  }

  public SnapshotVersion getSnapshotVersion() {
    return snapshotVersion;
  }

  public ByteString getResumeToken() {
    return resumeToken;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    QueryData queryData = (QueryData) o;
    return query.equals(queryData.query)
        && targetId == queryData.targetId
        && sequenceNumber == queryData.sequenceNumber
        && purpose.equals(queryData.purpose)
        && snapshotVersion.equals(queryData.snapshotVersion)
        && resumeToken.equals(queryData.resumeToken);
  }

  @Override
  public int hashCode() {
    int result = query.hashCode();
    result = 31 * result + targetId;
    result = 31 * result + (int) sequenceNumber;
    result = 31 * result + purpose.hashCode();
    result = 31 * result + snapshotVersion.hashCode();
    result = 31 * result + resumeToken.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "QueryData{"
        + "query="
        + query
        + ", targetId="
        + targetId
        + ", sequenceNumber="
        + sequenceNumber
        + ", purpose="
        + purpose
        + ", snapshotVersion="
        + snapshotVersion
        + ", resumeToken="
        + resumeToken
        + '}';
  }

  /** Creates a new query data instance with an updated snapshot version and resume token. */
  public QueryData copy(
      SnapshotVersion snapshotVersion, ByteString resumeToken, long sequenceNumber) {
    return new QueryData(query, targetId, sequenceNumber, purpose, snapshotVersion, resumeToken);
  }
}
