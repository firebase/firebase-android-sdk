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
package com.google.firebase.firestore.local

import com.google.firebase.firestore.core.TargetOrPipeline
import com.google.firebase.firestore.model.SnapshotVersion
import com.google.firebase.firestore.remote.WatchStream
import com.google.protobuf.ByteString

/** An immutable set of metadata that the store will need to keep track of for each target. */
class TargetData
/** Convenience constructor for use when creating a TargetData for the first time. */
@JvmOverloads
constructor(
  target: TargetOrPipeline,
  @JvmField val targetId: Int,
  sequenceNumber: Long,
  purpose: QueryPurpose,
  snapshotVersion: SnapshotVersion = SnapshotVersion.NONE,
  lastLimboFreeSnapshotVersion: SnapshotVersion = SnapshotVersion.NONE,
  resumeToken: ByteString = WatchStream.EMPTY_RESUME_TOKEN,
  expectedCount: Int? = null
) :
  BaseTargetData(
    target,
    sequenceNumber,
    purpose,
    snapshotVersion,
    lastLimboFreeSnapshotVersion,
    resumeToken,
    expectedCount
  ) {
  /**
   * Creates a new TargetData with the given values.
   *
   * @param target The target being listened to.
   * @param targetId The target id to which the target corresponds.
   * @param sequenceNumber The sequence number, denoting the last time this target was used.
   * @param purpose The purpose of the target.
   * @param snapshotVersion The latest snapshot version seen for this target.
   * @param lastLimboFreeSnapshotVersion The maximum snapshot version at which the associated target
   * view contained no limbo documents.
   * @param resumeToken An opaque, server-assigned token that allows watching a target to be resumed
   * after disconnecting without retransmitting all the data that matches the target. The resume
   * token essentially identifies a point in time from which the server should resume sending
   * @param expectedCount The number of documents that last matched the query at the resume token or
   * read time. Documents are counted only when making a listen request with resume token or read
   * time, otherwise, keep it null.
   */
  public override fun withSequenceNumber(sequenceNumber: Long): TargetData {
    return TargetData(
      target,
      targetId,
      sequenceNumber,
      purpose,
      snapshotVersion,
      lastLimboFreeSnapshotVersion,
      resumeToken,
      expectedCount
    )
  }

  public override fun withResumeToken(
    resumeToken: ByteString,
    snapshotVersion: SnapshotVersion
  ): TargetData {
    return TargetData(
      target,
      targetId,
      sequenceNumber,
      purpose,
      snapshotVersion,
      lastLimboFreeSnapshotVersion,
      resumeToken,
      /* expectedCount= */ null
    )
  }

  public override fun withExpectedCount(expectedCount: Int?): TargetData {
    return TargetData(
      target,
      targetId,
      sequenceNumber,
      purpose,
      snapshotVersion,
      lastLimboFreeSnapshotVersion,
      resumeToken,
      expectedCount
    )
  }

  public override fun withLastLimboFreeSnapshotVersion(
    lastLimboFreeSnapshotVersion: SnapshotVersion
  ): TargetData {
    return TargetData(
      target,
      targetId,
      sequenceNumber,
      purpose,
      snapshotVersion,
      lastLimboFreeSnapshotVersion,
      resumeToken,
      expectedCount
    )
  }

  fun withTarget(target: TargetOrPipeline): TargetData {
    return TargetData(
      target,
      targetId,
      sequenceNumber,
      purpose,
      snapshotVersion,
      lastLimboFreeSnapshotVersion,
      resumeToken,
      expectedCount
    )
  }

  public override fun equals(o: Any?): Boolean {
    if (!super.equals(o)) {
      return false
    }
    val that = o as TargetData
    return targetId == that.targetId
  }

  public override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + targetId
    return result
  }

  public override fun toString(): String {
    return ("TargetData{" +
      "target=" +
      target +
      ", targetId=" +
      targetId +
      ", sequenceNumber=" +
      sequenceNumber +
      ", purpose=" +
      purpose +
      ", snapshotVersion=" +
      snapshotVersion +
      ", lastLimboFreeSnapshotVersion=" +
      lastLimboFreeSnapshotVersion +
      ", resumeToken=" +
      resumeToken +
      ", expectedCount=" +
      expectedCount +
      '}')
  }
}
