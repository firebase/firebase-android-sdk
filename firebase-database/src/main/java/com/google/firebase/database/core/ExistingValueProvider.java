// Copyright 2020 Google LLC
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

import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.Node;

/**
 * An ExistingValueProvider implements the ValueProvider interface for a Node whose value is known.
 */
public class ExistingValueProvider implements ValueProvider {
  final private Node node;

  ExistingValueProvider(Node node) {
    this.node = node;
  }

  @Override
  public ValueProvider getImmediateChild(ChildKey childKey) {
    Node child = node.getImmediateChild(childKey);
    return new ExistingValueProvider(child);
  }

  @Override
  public Node node() {
    return node;
  }
}
