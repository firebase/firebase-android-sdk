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

package com.google.firebase.firestore.model.mutation;

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.SnapshotVersion;

/**
 * Encodes a precondition for a mutation. This follows the model that the backend accepts with the
 * special case of an explicit "empty" precondition (meaning no precondition).
 */
public final class Precondition {

  public static final Precondition NONE = new Precondition(null, null);

  /** If set, preconditions a mutation based on the last updateTime. */
  private final @Nullable SnapshotVersion updateTime;

  /** If set, preconditions a mutation based on whether the document exists. */
  private final @Nullable Boolean exists;

  private Precondition(@Nullable SnapshotVersion updateTime, @Nullable Boolean exists) {
    hardAssert(
        updateTime == null || exists == null,
        "Precondition can specify \"exists\" or \"updateTime\" but not both");
    this.updateTime = updateTime;
    this.exists = exists;
  }

  /** Creates a new Precondition with an exists flag. */
  public static Precondition exists(boolean exists) {
    return new Precondition(null, exists);
  }

  /** Creates a new Precondition based on a version a document exists at. */
  public static Precondition updateTime(SnapshotVersion version) {
    return new Precondition(version, null);
  }

  /** Returns whether this Precondition is empty. */
  public boolean isNone() {
    return this.updateTime == null && this.exists == null;
  }

  @Nullable
  public SnapshotVersion getUpdateTime() {
    return this.updateTime;
  }

  @Nullable
  public Boolean getExists() {
    return this.exists;
  }

  /**
   * Returns true if the preconditions is valid for the given document (or null if no document is
   * available).
   */
  public boolean isValidFor(@Nullable MaybeDocument maybeDoc) {
    if (this.updateTime != null) {
      return (maybeDoc instanceof Document) && maybeDoc.getVersion().equals(this.updateTime);
    } else if (this.exists != null) {
      return this.exists == (maybeDoc instanceof Document);
    } else {
      hardAssert(this.isNone(), "Precondition should be empty");
      return true;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Precondition that = (Precondition) o;

    if (updateTime != null ? !updateTime.equals(that.updateTime) : that.updateTime != null) {
      return false;
    }
    return exists != null ? exists.equals(that.exists) : that.exists == null;
  }

  @Override
  public int hashCode() {
    int result = updateTime != null ? updateTime.hashCode() : 0;
    result = 31 * result + (exists != null ? exists.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    if (isNone()) {
      return "Precondition{<none>}";
    } else if (updateTime != null) {
      return "Precondition{updateTime=" + updateTime + "}";
    } else if (exists != null) {
      return "Precondition{exists=" + exists + "}";
    } else {
      throw fail("Invalid Precondition");
    }
  }
}
