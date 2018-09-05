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

package com.google.firebase.database.snapshot;

import com.google.firebase.database.core.utilities.Utilities;

public class LongNode extends LeafNode<LongNode> {

  private final long value;

  public LongNode(Long value, Node priority) {
    super(priority);
    this.value = value;
  }

  @Override
  public Object getValue() {
    return value;
  }

  @Override
  public String getHashRepresentation(HashVersion version) {
    String toHash = getPriorityHash(version);
    toHash += "number:";
    toHash += Utilities.doubleToHashString((double) value);
    return toHash;
  }

  @Override
  public LongNode updatePriority(Node priority) {
    return new LongNode(value, priority);
  }

  @Override
  protected LeafType getLeafType() {
    // TODO: unify number nodes
    return LeafType.Number;
  }

  @Override
  protected int compareLeafValues(LongNode other) {
    return Utilities.compareLongs(this.value, other.value);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof LongNode)) {
      return false;
    }
    LongNode otherLongNode = (LongNode) other;
    return value == otherLongNode.value && priority.equals(otherLongNode.priority);
  }

  @Override
  public int hashCode() {
    return (int) (value ^ (value >>> 32)) + priority.hashCode();
  }
}
