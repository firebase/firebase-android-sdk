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

public class KeyIndex extends Index {

  private static final KeyIndex INSTANCE = new KeyIndex();

  public static KeyIndex getInstance() {
    return INSTANCE;
  }

  private KeyIndex() {
    // prevent instantiation
  }

  @Override
  public boolean isDefinedOn(Node a) {
    return true;
  }

  @Override
  public NamedNode makePost(ChildKey name, Node value) {
    hardAssert(value instanceof StringNode);
    // We just use empty node, but it'll never be compared, since our comparator only looks at name
    return new NamedNode(ChildKey.fromString((String) value.getValue()), EmptyNode.Empty());
  }

  @Override
  public NamedNode maxPost() {
    return NamedNode.getMaxNode();
  }

  @Override
  public String getQueryDefinition() {
    return ".key";
  }

  @Override
  public int compare(NamedNode o1, NamedNode o2) {
    return o1.getName().compareTo(o2.getName());
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof KeyIndex;
  }

  @Override
  public int hashCode() {
    // chosen by a fair dice roll. Guaranteed to be random
    return 37;
  }

  @Override
  public String toString() {
    return "KeyIndex";
  }
}
