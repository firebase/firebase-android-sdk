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

package com.google.firebase.database.core.operation;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.utilities.ImmutableTree;
import com.google.firebase.database.snapshot.ChildKey;

public class AckUserWrite extends Operation {

  private final boolean revert;
  // A tree containing true for each affected path.  Affected paths can't overlap.
  private final ImmutableTree<Boolean> affectedTree;

  public AckUserWrite(Path path, ImmutableTree<Boolean> affectedTree, boolean revert) {
    super(OperationType.AckUserWrite, OperationSource.USER, path);
    this.affectedTree = affectedTree;
    this.revert = revert;
  }

  public ImmutableTree<Boolean> getAffectedTree() {
    return this.affectedTree;
  }

  public boolean isRevert() {
    return this.revert;
  }

  @Override
  public Operation operationForChild(ChildKey childKey) {
    if (!this.path.isEmpty()) {
      hardAssert(
          this.path.getFront().equals(childKey), "operationForChild called for unrelated child.");
      return new AckUserWrite(this.path.popFront(), this.affectedTree, this.revert);
    } else if (this.affectedTree.getValue() != null) {
      hardAssert(
          this.affectedTree.getChildren().isEmpty(),
          "affectedTree should not have overlapping affected paths.");
      // All child locations are affected as well; just return same operation.
      return this;
    } else {
      ImmutableTree<Boolean> childTree = this.affectedTree.subtree(new Path(childKey));
      return new AckUserWrite(Path.getEmptyPath(), childTree, this.revert);
    }
  }

  @Override
  public String toString() {
    return String.format(
        "AckUserWrite { path=%s, revert=%s, affectedTree=%s }",
        getPath(), this.revert, this.affectedTree);
  }
}
