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

public class StringNode extends LeafNode<StringNode> {

  private final String value;

  public StringNode(String value, Node priority) {
    super(priority);
    this.value = value;
  }

  @Override
  public Object getValue() {
    return value;
  }

  @Override
  public String getHashRepresentation(HashVersion version) {
    switch (version) {
      case V1:
        return getPriorityHash(version) + "string:" + value;
      case V2:
        {
          return getPriorityHash(version) + "string:" + Utilities.stringHashV2Representation(value);
        }
      default:
        throw new IllegalArgumentException("Invalid hash version for string node: " + version);
    }
  }

  @Override
  public StringNode updatePriority(Node priority) {
    return new StringNode(value, priority);
  }

  @Override
  protected LeafType getLeafType() {
    return LeafType.String;
  }

  @Override
  protected int compareLeafValues(StringNode other) {
    return this.value.compareTo(other.value);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof StringNode)) {
      return false;
    }
    StringNode otherStringNode = (StringNode) other;
    return value.equals(otherStringNode.value) && priority.equals(otherStringNode.priority);
  }

  @Override
  public int hashCode() {
    return this.value.hashCode() + this.priority.hashCode();
  }
}
