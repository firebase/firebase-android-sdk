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

import com.google.firebase.database.core.Path;

public final class PathIndex extends Index {

  private final Path indexPath;

  public PathIndex(Path indexPath) {
    if (indexPath.size() == 1 && indexPath.getFront().isPriorityChildName()) {
      throw new IllegalArgumentException(
          "Can't create PathIndex with '.priority' as key. Please use PriorityIndex instead!");
    }
    this.indexPath = indexPath;
  }

  @Override
  public boolean isDefinedOn(Node snapshot) {
    return !snapshot.getChild(this.indexPath).isEmpty();
  }

  @Override
  public int compare(NamedNode a, NamedNode b) {
    Node aChild = a.getNode().getChild(this.indexPath);
    Node bChild = b.getNode().getChild(this.indexPath);
    int indexCmp = aChild.compareTo(bChild);
    if (indexCmp == 0) {
      return a.getName().compareTo(b.getName());
    } else {
      return indexCmp;
    }
  }

  @Override
  public NamedNode makePost(ChildKey name, Node value) {
    Node node = EmptyNode.Empty().updateChild(this.indexPath, value);
    return new NamedNode(name, node);
  }

  @Override
  public NamedNode maxPost() {
    Node node = EmptyNode.Empty().updateChild(this.indexPath, Node.MAX_NODE);
    return new NamedNode(ChildKey.getMaxName(), node);
  }

  @Override
  public String getQueryDefinition() {
    return this.indexPath.wireFormat();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PathIndex that = (PathIndex) o;

    if (!indexPath.equals(that.indexPath)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return indexPath.hashCode();
  }
}
