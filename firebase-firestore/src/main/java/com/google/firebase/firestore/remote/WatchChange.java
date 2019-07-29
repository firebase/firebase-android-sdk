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

package com.google.firebase.firestore.remote;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import java.util.List;

/**
 * A Watch Change is the internal representation of the watcher API protocol buffers. This is an
 * empty abstract class so that all the different kinds of changes can have a common base class.
 * Note that this class is intended to be sealed within this file.
 */
public abstract class WatchChange {

  private WatchChange() {
    // Private constructor to limit inherited classes to this file
  }

  /**
   * A document change represents a change document and a list of target ids to which this change
   * applies. If the document has been deleted, the deleted document will be provided.
   */
  public static final class DocumentChange extends WatchChange {
    // TODO: figure out if we can actually use arrays here for efficiency
    /** The new document applies to all of these targets. */
    private final List<Integer> updatedTargetIds;

    /** The new document is removed from all of these targets. */
    private final List<Integer> removedTargetIds;

    /** The key of the document for this change. */
    private final DocumentKey documentKey;

    /**
     * The new document or DeletedDocument if it was deleted. Is null if the document went out of
     * view without the server sending a new document.
     */
    @Nullable private final MaybeDocument newDocument;

    public DocumentChange(
        List<Integer> updatedTargetIds,
        List<Integer> removedTargetIds,
        DocumentKey key,
        @Nullable MaybeDocument document) {
      this.updatedTargetIds = updatedTargetIds;
      this.removedTargetIds = removedTargetIds;
      this.documentKey = key;
      this.newDocument = document;
    }

    /** The target IDs for which this document should be updated/added */
    public List<Integer> getUpdatedTargetIds() {
      return updatedTargetIds;
    }

    /** The target IDs for which this document is no longer relevant */
    public List<Integer> getRemovedTargetIds() {
      return removedTargetIds;
    }

    /** The new document of this change */
    @Nullable
    public MaybeDocument getNewDocument() {
      return newDocument;
    }

    public DocumentKey getDocumentKey() {
      return documentKey;
    }

    @Override
    public String toString() {
      return "DocumentChange{"
          + "updatedTargetIds="
          + updatedTargetIds
          + ", removedTargetIds="
          + removedTargetIds
          + ", key="
          + documentKey
          + ", newDocument="
          + newDocument
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      DocumentChange that = (DocumentChange) o;

      if (!updatedTargetIds.equals(that.updatedTargetIds)) {
        return false;
      }
      if (!removedTargetIds.equals(that.removedTargetIds)) {
        return false;
      }
      if (!documentKey.equals(that.documentKey)) {
        return false;
      }
      return newDocument != null ? newDocument.equals(that.newDocument) : that.newDocument == null;
    }

    @Override
    public int hashCode() {
      int result = updatedTargetIds.hashCode();
      result = 31 * result + removedTargetIds.hashCode();
      result = 31 * result + documentKey.hashCode();
      result = 31 * result + (newDocument != null ? newDocument.hashCode() : 0);
      return result;
    }
  }

  /**
   * An ExistenceFilterWatchChange applies to the targets and is required to verify the current
   * client state against expected state sent from the server.
   */
  public static final class ExistenceFilterWatchChange extends WatchChange {
    private final int targetId;

    private final ExistenceFilter existenceFilter;

    public ExistenceFilterWatchChange(int targetId, ExistenceFilter existenceFilter) {
      this.targetId = targetId;
      this.existenceFilter = existenceFilter;
    }

    /** The target ID this existence filter applies to */
    public int getTargetId() {
      return targetId;
    }

    public ExistenceFilter getExistenceFilter() {
      return existenceFilter;
    }

    @Override
    public String toString() {
      return "ExistenceFilterWatchChange{"
          + "targetId="
          + targetId
          + ", existenceFilter="
          + existenceFilter
          + '}';
    }
  }

  /** The kind of change that happened to the watch target. */
  public enum WatchTargetChangeType {
    NoChange,
    Added,
    Removed,
    Current,
    Reset
  }

  /** The state of a target has changed. This can mean removal, addition, current or reset. */
  public static final class WatchTargetChange extends WatchChange {
    private final WatchTargetChangeType changeType;
    private final List<Integer> targetIds;
    private final ByteString resumeToken;

    /** The cause, only valid if changeType == Removal */
    @Nullable private final Status cause;

    public WatchTargetChange(WatchTargetChangeType changeType, List<Integer> targetIds) {
      this(changeType, targetIds, WatchStream.EMPTY_RESUME_TOKEN, null);
    }

    public WatchTargetChange(
        WatchTargetChangeType changeType, List<Integer> targetIds, ByteString resumeToken) {
      this(changeType, targetIds, resumeToken, null);
    }

    public WatchTargetChange(
        WatchTargetChangeType changeType,
        List<Integer> targetIds,
        ByteString resumeToken,
        @Nullable Status cause) {
      // cause != null implies removal
      hardAssert(
          cause == null || changeType == WatchTargetChangeType.Removed,
          "Got cause for a target change that was not a removal");
      this.changeType = changeType;
      this.targetIds = targetIds;
      this.resumeToken = resumeToken;
      // We can get a cause that is considered ok, but everywhere we assume that
      // any non-null cause is an error.
      if (cause != null && !cause.isOk()) {
        this.cause = cause;
      } else {
        this.cause = null;
      }
    }

    /** What kind of change occurred to the watch target. */
    public WatchTargetChangeType getChangeType() {
      return changeType;
    }

    /** The list of targets this change applies to */
    public List<Integer> getTargetIds() {
      return targetIds;
    }

    /**
     * Returns the opaque, server-assigned token that allows watching a query to be resumed after
     * disconnecting without retransmitting all the data that matches the query. The resume token
     * essentially identifies a point in time from which the server should resume sending results.
     */
    public ByteString getResumeToken() {
      return resumeToken;
    }

    /** The cause, only valid if changeType == Removal */
    @Nullable
    public Status getCause() {
      return cause;
    }

    @Override
    public String toString() {
      return "WatchTargetChange{changeType=" + changeType + ", targetIds=" + targetIds + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      WatchTargetChange that = (WatchTargetChange) o;

      if (changeType != that.changeType) {
        return false;
      }
      if (!targetIds.equals(that.targetIds)) {
        return false;
      }
      if (!resumeToken.equals(that.resumeToken)) {
        return false;
      }
      // io.grpc.Status does not implement equals, so we compare on status code
      if (cause != null) {
        return that.cause != null && cause.getCode().equals(that.cause.getCode());
      } else {
        return that.cause == null;
      }
    }

    @Override
    public int hashCode() {
      int result = changeType.hashCode();
      result = 31 * result + targetIds.hashCode();
      result = 31 * result + resumeToken.hashCode();
      result = 31 * result + (cause != null ? cause.getCode().hashCode() : 0);
      return result;
    }
  }
}
