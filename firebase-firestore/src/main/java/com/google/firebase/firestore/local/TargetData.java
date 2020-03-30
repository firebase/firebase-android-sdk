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

import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.remote.WatchStream;
import com.google.protobuf.ByteString;

/** An immutable set of metadata that the store will need to keep track of for each target. */
public final class TargetData {
  private final Target target;
  private final int targetId;
  private final long sequenceNumber;
  private final QueryPurpose purpose;
  private final SnapshotVersion snapshotVersion;
  private final SnapshotVersion lastLimboFreeSnapshotVersion;
  private final ByteString resumeToken;

  /**
   * Creates a new TargetData with the given values.
   *
   * @param target The target being listened to.
   * @param targetId The target id to which the target corresponds, assigned by the LocalStore for
   *     user queries or the SyncEngine for limbo queries.
   * @param sequenceNumber The sequence number, denoting the last time this target was used.
   * @param purpose The purpose of the target.
   * @param snapshotVersion The latest snapshot version seen for this target.
   * @param lastLimboFreeSnapshotVersion The maximum snapshot version at which the associated target
   *     view contained no limbo documents.
   * @param resumeToken An opaque, server-assigned token that allows watching a target to be resumed
   *     after disconnecting without retransmitting all the data that matches the target. The resume
   *     token essentially identifies a point in time from which the server should resume sending
   */
  TargetData(
      Target target,
      int targetId,
      long sequenceNumber,
      QueryPurpose purpose,
      SnapshotVersion snapshotVersion,
      SnapshotVersion lastLimboFreeSnapshotVersion,
      ByteString resumeToken) {
    this.target = checkNotNull(target);
    this.targetId = targetId;
    this.sequenceNumber = sequenceNumber;
    this.lastLimboFreeSnapshotVersion = lastLimboFreeSnapshotVersion;
    this.purpose = purpose;
    this.snapshotVersion = checkNotNull(snapshotVersion);
    this.resumeToken = checkNotNull(resumeToken);
  }

  /** Convenience constructor for use when creating a TargetData for the first time. */
  public TargetData(Target target, int targetId, long sequenceNumber, QueryPurpose purpose) {
    this(
        target,
        targetId,
        sequenceNumber,
        purpose,
        SnapshotVersion.NONE,
        SnapshotVersion.NONE,
        WatchStream.EMPTY_RESUME_TOKEN);
  }

  /** Creates a new target data instance with an updated sequence number. */
  public TargetData withSequenceNumber(long sequenceNumber) {
    return new TargetData(
        target,
        targetId,
        sequenceNumber,
        purpose,
        snapshotVersion,
        lastLimboFreeSnapshotVersion,
        resumeToken);
  }

  /** Creates a new target data instance with an updated resume token and snapshot version. */
  public TargetData withResumeToken(ByteString resumeToken, SnapshotVersion snapshotVersion) {
    return new TargetData(
        target,
        targetId,
        sequenceNumber,
        purpose,
        snapshotVersion,
        lastLimboFreeSnapshotVersion,
        resumeToken);
  }

  /** Creates a new target data instance with an updated last limbo free snapshot version number. */
  public TargetData withLastLimboFreeSnapshotVersion(SnapshotVersion lastLimboFreeSnapshotVersion) {
    return new TargetData(
        target,
        targetId,
        sequenceNumber,
        purpose,
        snapshotVersion,
        lastLimboFreeSnapshotVersion,
        resumeToken);
  }

  public Target getTarget() {
    return target;
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

  /**
   * Returns the last snapshot version for which the associated view contained no limbo documents.
   */
  public SnapshotVersion getLastLimboFreeSnapshotVersion() {
    return lastLimboFreeSnapshotVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TargetData targetData = (TargetData) o;
    return target.equals(targetData.target)
        && targetId == targetData.targetId
        && sequenceNumber == targetData.sequenceNumber
        && purpose.equals(targetData.purpose)
        && snapshotVersion.equals(targetData.snapshotVersion)
        && lastLimboFreeSnapshotVersion.equals(targetData.lastLimboFreeSnapshotVersion)
        && resumeToken.equals(targetData.resumeToken);
  }

  @Override
  public int hashCode() {
    int result = target.hashCode();
    result = 31 * result + targetId;
    result = 31 * result + (int) sequenceNumber;
    result = 31 * result + purpose.hashCode();
    result = 31 * result + snapshotVersion.hashCode();
    result = 31 * result + lastLimboFreeSnapshotVersion.hashCode();
    result = 31 * result + resumeToken.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "TargetData{"
        + "target="
        + target
        + ", targetId="
        + targetId
        + ", sequenceNumber="
        + sequenceNumber
        + ", purpose="
        + purpose
        + ", snapshotVersion="
        + snapshotVersion
        + ", lastLimboFreeSnapshotVersion="
        + lastLimboFreeSnapshotVersion
        + ", resumeToken="
        + resumeToken
        + '}';
  }
}
