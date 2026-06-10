// Copyright 2026 Google LLC
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
package com.google.firebase.firestore.remote

import com.google.firebase.firestore.core.TargetOrPipeline
import com.google.firebase.firestore.local.BaseTargetData
import com.google.firebase.firestore.local.QueryPurpose
import com.google.firebase.firestore.model.SnapshotVersion
import com.google.protobuf.ByteString
import java.util.Objects

/**
 * An immutable set of metadata that the remote store will need to keep track of for each target.
 */
class RemoteTargetData
@JvmOverloads
constructor(
  target: TargetOrPipeline,
  @JvmField val targetId: RemoteTargetId?,
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
  public override fun withSequenceNumber(sequenceNumber: Long): RemoteTargetData {
    return RemoteTargetData(
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
  ): RemoteTargetData {
    return RemoteTargetData(
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

  public override fun withExpectedCount(expectedCount: Int?): RemoteTargetData {
    return RemoteTargetData(
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
  ): RemoteTargetData {
    return RemoteTargetData(
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
    val that = o as RemoteTargetData
    return targetId == that.targetId
  }

  public override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + Objects.hashCode(targetId)
    return result
  }

  public override fun toString(): String {
    return ("RemoteTargetData{" +
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
