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

package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.core.TargetOrPipeline;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.protobuf.ByteString;
import java.util.Objects;

/** An abstract set of metadata that the store will need to keep track of for each target. */
public abstract class BaseTargetData {
  private final TargetOrPipeline target;
  private final long sequenceNumber;
  private final QueryPurpose purpose;
  private final SnapshotVersion snapshotVersion;
  private final SnapshotVersion lastLimboFreeSnapshotVersion;
  private final ByteString resumeToken;
  private final @Nullable Integer expectedCount;

  /** Creates a new BaseTargetData with the given values. */
  protected BaseTargetData(
      TargetOrPipeline target,
      long sequenceNumber,
      QueryPurpose purpose,
      SnapshotVersion snapshotVersion,
      SnapshotVersion lastLimboFreeSnapshotVersion,
      ByteString resumeToken,
      @Nullable Integer expectedCount) {
    this.target = checkNotNull(target);
    this.sequenceNumber = sequenceNumber;
    this.lastLimboFreeSnapshotVersion = lastLimboFreeSnapshotVersion;
    this.purpose = purpose;
    this.snapshotVersion = checkNotNull(snapshotVersion);
    this.resumeToken = checkNotNull(resumeToken);
    this.expectedCount = expectedCount;
  }

  /** Creates a new target data instance with an updated sequence number. */
  public abstract BaseTargetData withSequenceNumber(long sequenceNumber);

  /** Creates a new target data instance with an updated resume token and snapshot version. */
  public abstract BaseTargetData withResumeToken(
      ByteString resumeToken, SnapshotVersion snapshotVersion);

  /** Creates a new target data instance with an updated expected count. */
  public abstract BaseTargetData withExpectedCount(@Nullable Integer expectedCount);

  /** Creates a new target data instance with an updated last limbo free snapshot version number. */
  public abstract BaseTargetData withLastLimboFreeSnapshotVersion(
      SnapshotVersion lastLimboFreeSnapshotVersion);

  public TargetOrPipeline getTarget() {
    return target;
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

  @Nullable
  public Integer getExpectedCount() {
    return expectedCount;
  }

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

    BaseTargetData that = (BaseTargetData) o;
    return target.equals(that.target)
        && sequenceNumber == that.sequenceNumber
        && purpose.equals(that.purpose)
        && snapshotVersion.equals(that.snapshotVersion)
        && lastLimboFreeSnapshotVersion.equals(that.lastLimboFreeSnapshotVersion)
        && resumeToken.equals(that.resumeToken)
        && Objects.equals(expectedCount, that.expectedCount);
  }

  @Override
  public int hashCode() {
    int result = target.hashCode();
    result = 31 * result + (int) sequenceNumber;
    result = 31 * result + purpose.hashCode();
    result = 31 * result + snapshotVersion.hashCode();
    result = 31 * result + lastLimboFreeSnapshotVersion.hashCode();
    result = 31 * result + resumeToken.hashCode();
    result = 31 * result + Objects.hashCode(expectedCount);
    return result;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "{"
        + "target="
        + target
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
        + ", expectedCount="
        + expectedCount
        + '}';
  }
}
