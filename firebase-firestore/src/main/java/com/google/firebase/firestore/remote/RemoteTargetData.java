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

package com.google.firebase.firestore.remote;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.core.TargetOrPipeline;
import com.google.firebase.firestore.local.BaseTargetData;
import com.google.firebase.firestore.local.QueryPurpose;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.protobuf.ByteString;
import java.util.Objects;

/** An immutable set of metadata that the remote store will need to keep track of for each target. */
public final class RemoteTargetData extends BaseTargetData {
  private final RemoteTargetId targetId;

  public RemoteTargetData(
      TargetOrPipeline target,
      RemoteTargetId targetId,
      long sequenceNumber,
      QueryPurpose purpose,
      SnapshotVersion snapshotVersion,
      SnapshotVersion lastLimboFreeSnapshotVersion,
      ByteString resumeToken,
      @Nullable Integer expectedCount) {
    super(
        target,
        sequenceNumber,
        purpose,
        snapshotVersion,
        lastLimboFreeSnapshotVersion,
        resumeToken,
        expectedCount);
    this.targetId = targetId;
  }

  public RemoteTargetData(
      TargetOrPipeline target, RemoteTargetId targetId, long sequenceNumber, QueryPurpose purpose) {
    this(
        target,
        targetId,
        sequenceNumber,
        purpose,
        SnapshotVersion.NONE,
        SnapshotVersion.NONE,
        WatchStream.EMPTY_RESUME_TOKEN,
        null);
  }

  public RemoteTargetId getTargetId() {
    return targetId;
  }

  @Override
  public RemoteTargetData withSequenceNumber(long sequenceNumber) {
    return new RemoteTargetData(
        getTarget(),
        targetId,
        sequenceNumber,
        getPurpose(),
        getSnapshotVersion(),
        getLastLimboFreeSnapshotVersion(),
        getResumeToken(),
        getExpectedCount());
  }

  @Override
  public RemoteTargetData withResumeToken(ByteString resumeToken, SnapshotVersion snapshotVersion) {
    return new RemoteTargetData(
        getTarget(),
        targetId,
        getSequenceNumber(),
        getPurpose(),
        snapshotVersion,
        getLastLimboFreeSnapshotVersion(),
        resumeToken,
        /* expectedCount= */ null);
  }

  @Override
  public RemoteTargetData withExpectedCount(@Nullable Integer expectedCount) {
    return new RemoteTargetData(
        getTarget(),
        targetId,
        getSequenceNumber(),
        getPurpose(),
        getSnapshotVersion(),
        getLastLimboFreeSnapshotVersion(),
        getResumeToken(),
        expectedCount);
  }

  @Override
  public RemoteTargetData withLastLimboFreeSnapshotVersion(
      SnapshotVersion lastLimboFreeSnapshotVersion) {
    return new RemoteTargetData(
        getTarget(),
        targetId,
        getSequenceNumber(),
        getPurpose(),
        getSnapshotVersion(),
        lastLimboFreeSnapshotVersion,
        getResumeToken(),
        getExpectedCount());
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    RemoteTargetData that = (RemoteTargetData) o;
    return Objects.equals(targetId, that.targetId);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Objects.hashCode(targetId);
    return result;
  }

  @Override
  public String toString() {
    return "RemoteTargetData{"
        + "target="
        + getTarget()
        + ", targetId="
        + targetId
        + ", sequenceNumber="
        + getSequenceNumber()
        + ", purpose="
        + getPurpose()
        + ", snapshotVersion="
        + getSnapshotVersion()
        + ", lastLimboFreeSnapshotVersion="
        + getLastLimboFreeSnapshotVersion()
        + ", resumeToken="
        + getResumeToken()
        + ", expectedCount="
        + getExpectedCount()
        + '}';
  }
}
