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

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import java.util.Map;

public class DeferredValueNode extends LeafNode<DeferredValueNode> {

  private Map<Object, Object> value;

  public DeferredValueNode(Map<Object, Object> value, Node priority) {
    super(priority);
    this.value = value;
  }

  @Override
  public Object getValue() {
    return value;
  }

  @Override
  public String getHashRepresentation(HashVersion version) {
    return getPriorityHash(version) + "deferredValue:" + value;
  }

  @Override
  public DeferredValueNode updatePriority(Node priority) {
    hardAssert(PriorityUtilities.isValidPriority(priority));
    return new DeferredValueNode(value, priority);
  }

  @Override
  protected LeafType getLeafType() {
    return LeafType.DeferredValue;
  }

  @Override
  protected int compareLeafValues(DeferredValueNode other) {
    // Deferred value nodes are always equal
    return 0;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof DeferredValueNode)) {
      return false;
    }
    DeferredValueNode otherDeferredValueNode = (DeferredValueNode) other;
    return value.equals(otherDeferredValueNode.value)
        && priority.equals(otherDeferredValueNode.priority);
  }

  @Override
  public int hashCode() {
    return value.hashCode() + this.priority.hashCode();
  }
}
