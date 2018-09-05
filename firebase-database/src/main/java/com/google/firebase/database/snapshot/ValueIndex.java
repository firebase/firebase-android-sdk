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

public class ValueIndex extends Index {

  private static final ValueIndex INSTANCE = new ValueIndex();

  private ValueIndex() {
    // prevent initialization
  }

  public static ValueIndex getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isDefinedOn(Node a) {
    return true;
  }

  @Override
  public NamedNode makePost(ChildKey name, Node value) {
    return new NamedNode(name, value);
  }

  @Override
  public NamedNode maxPost() {
    return new NamedNode(ChildKey.getMaxName(), Node.MAX_NODE);
  }

  @Override
  public String getQueryDefinition() {
    return ".value";
  }

  @Override
  public int compare(NamedNode one, NamedNode two) {
    int indexCmp = one.getNode().compareTo(two.getNode());
    if (indexCmp == 0) {
      return one.getName().compareTo(two.getName());
    } else {
      return indexCmp;
    }
  }

  @Override
  public int hashCode() {
    // chosen by fair dice roll
    return 4;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ValueIndex;
  }

  @Override
  public String toString() {
    return "ValueIndex";
  }
}
