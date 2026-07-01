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
package com.google.firebase.firestore.local

import com.google.firebase.firestore.core.TargetOrPipeline
import com.google.firebase.firestore.model.SnapshotVersion
import com.google.firebase.firestore.util.Preconditions
import com.google.protobuf.ByteString
import java.util.Objects

/** An abstract set of metadata that the store will need to keep track of for each target. */
abstract class BaseTargetData
protected constructor(
  target: TargetOrPipeline,
  @JvmField val sequenceNumber: Long,
  @JvmField val purpose: QueryPurpose,
  snapshotVersion: SnapshotVersion,
  @JvmField val lastLimboFreeSnapshotVersion: SnapshotVersion,
  resumeToken: ByteString,
  @JvmField val expectedCount: Int?
) {
  @JvmField val target: TargetOrPipeline
  @JvmField val snapshotVersion: SnapshotVersion
  @JvmField val resumeToken: ByteString

  /** Creates a new BaseTargetData with the given values. */
  init {
    this.target = Preconditions.checkNotNull<TargetOrPipeline>(target)
    this.snapshotVersion = Preconditions.checkNotNull<SnapshotVersion>(snapshotVersion)
    this.resumeToken = Preconditions.checkNotNull<ByteString>(resumeToken)
  }

  /** Creates a new target data instance with an updated sequence number. */
  abstract fun withSequenceNumber(sequenceNumber: Long): BaseTargetData?

  /** Creates a new target data instance with an updated resume token and snapshot version. */
  abstract fun withResumeToken(
    resumeToken: ByteString,
    snapshotVersion: SnapshotVersion
  ): BaseTargetData?

  /** Creates a new target data instance with an updated expected count. */
  abstract fun withExpectedCount(expectedCount: Int?): BaseTargetData?

  /** Creates a new target data instance with an updated last limbo free snapshot version number. */
  abstract fun withLastLimboFreeSnapshotVersion(
    lastLimboFreeSnapshotVersion: SnapshotVersion
  ): BaseTargetData?

  override fun equals(o: Any?): Boolean {
    if (this === o) {
      return true
    }
    if (o == null || javaClass != o.javaClass) {
      return false
    }

    val that = o as BaseTargetData
    return target == that.target &&
      sequenceNumber == that.sequenceNumber &&
      purpose == that.purpose &&
      snapshotVersion == that.snapshotVersion &&
      lastLimboFreeSnapshotVersion == that.lastLimboFreeSnapshotVersion &&
      resumeToken == that.resumeToken &&
      expectedCount == that.expectedCount
  }

  override fun hashCode(): Int {
    var result = target.hashCode()
    result = 31 * result + sequenceNumber.toInt()
    result = 31 * result + purpose.hashCode()
    result = 31 * result + snapshotVersion.hashCode()
    result = 31 * result + lastLimboFreeSnapshotVersion.hashCode()
    result = 31 * result + resumeToken.hashCode()
    result = 31 * result + Objects.hashCode(expectedCount)
    return result
  }

  override fun toString(): String {
    return (javaClass.getSimpleName() +
      "{" +
      "target=" +
      target +
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
