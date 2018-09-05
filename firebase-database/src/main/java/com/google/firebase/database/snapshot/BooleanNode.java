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

public class BooleanNode extends LeafNode<BooleanNode> {

  private final boolean value;

  public BooleanNode(Boolean value, Node priority) {
    super(priority);
    this.value = value;
  }

  @Override
  public Object getValue() {
    return value;
  }

  @Override
  public String getHashRepresentation(HashVersion version) {
    return getPriorityHash(version) + "boolean:" + value;
  }

  @Override
  public BooleanNode updatePriority(Node priority) {
    return new BooleanNode(value, priority);
  }

  @Override
  protected LeafType getLeafType() {
    return LeafType.Boolean;
  }

  @Override
  protected int compareLeafValues(BooleanNode other) {
    return this.value == other.value ? 0 : (value ? 1 : -1);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof BooleanNode)) {
      return false;
    }
    BooleanNode otherBooleanNode = (BooleanNode) other;
    return value == otherBooleanNode.value && priority.equals(otherBooleanNode.priority);
  }

  @Override
  public int hashCode() {
    return (this.value ? 1 : 0) + this.priority.hashCode();
  }
}
