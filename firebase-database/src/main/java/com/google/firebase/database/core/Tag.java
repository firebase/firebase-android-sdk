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

public final class Tag {
  private final long tagNumber;

  public Tag(long tagNumber) {
    this.tagNumber = tagNumber;
  }

  public long getTagNumber() {
    return this.tagNumber;
  }

  @Override
  public String toString() {
    return "Tag{" + "tagNumber=" + tagNumber + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Tag tag = (Tag) o;

    if (tagNumber != tag.tagNumber) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return (int) (tagNumber ^ (tagNumber >>> 32));
  }
}
