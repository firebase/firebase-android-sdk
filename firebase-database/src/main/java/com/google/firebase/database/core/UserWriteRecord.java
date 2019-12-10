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

package com.google.firebase.database.core;

import com.google.firebase.database.snapshot.Node;

public final class UserWriteRecord {

  private final long writeId;
  private final Path path;
  private final Node overwrite;
  private final CompoundWrite merge;
  private final boolean visible;

  public UserWriteRecord(long writeId, Path path, Node overwrite, boolean visible) {
    this.writeId = writeId;
    this.path = path;
    this.overwrite = overwrite;
    this.merge = null;
    this.visible = visible;
  }

  public UserWriteRecord(long writeId, Path path, CompoundWrite merge) {
    this.writeId = writeId;
    this.path = path;
    this.overwrite = null;
    this.merge = merge;
    this.visible = true;
  }

  public long getWriteId() {
    return writeId;
  }

  public Path getPath() {
    return path;
  }

  public Node getOverwrite() {
    if (overwrite == null) {
      throw new IllegalArgumentException("Can't access overwrite when write is a merge!");
    }
    return overwrite;
  }

  public CompoundWrite getMerge() {
    if (merge == null) {
      throw new IllegalArgumentException("Can't access merge when write is an overwrite!");
    }
    return merge;
  }

  public boolean isMerge() {
    return merge != null;
  }

  public boolean isOverwrite() {
    return overwrite != null;
  }

  public boolean isVisible() {
    return visible;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    UserWriteRecord record = (UserWriteRecord) o;

    if (!(this.writeId == record.writeId)) {
      return false;
    }
    if (!this.path.equals(record.path)) {
      return false;
    }
    if (!(this.visible == record.visible)) {
      return false;
    }
    if (!(this.overwrite != null
        ? this.overwrite.equals(record.overwrite)
        : record.overwrite == null)) {
      return false;
    }
    if (!(this.merge != null ? this.merge.equals(record.merge) : record.merge == null)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = Long.valueOf(this.writeId).hashCode();
    result = 31 * result + Boolean.valueOf(this.visible).hashCode();
    result = 31 * result + this.path.hashCode();
    result = 31 * result + (this.overwrite != null ? this.overwrite.hashCode() : 0);
    result = 31 * result + (this.merge != null ? this.merge.hashCode() : 0);

    return result;
  }

  @Override
  public String toString() {
    return "UserWriteRecord{id="
        + writeId
        + " path="
        + path
        + " visible="
        + visible
        + " overwrite="
        + overwrite
        + " merge="
        + merge
        + "}";
  }
}
