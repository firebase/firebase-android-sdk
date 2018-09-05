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

import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Node;

public class SnapshotHolder {

  private Node rootNode;

  SnapshotHolder() {
    rootNode = EmptyNode.Empty();
  }

  public SnapshotHolder(Node node) {
    rootNode = node;
  }

  public Node getRootNode() {
    return rootNode;
  }

  public Node getNode(Path path) {
    return rootNode.getChild(path);
  }

  public void update(Path path, Node node) {
    rootNode = rootNode.updateChild(path, node);
  }
}
