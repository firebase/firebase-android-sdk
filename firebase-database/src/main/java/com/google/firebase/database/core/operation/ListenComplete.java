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
import com.google.firebase.database.snapshot.ChildKey;

public class ListenComplete extends Operation {

  public ListenComplete(OperationSource source, Path path) {
    super(OperationType.ListenComplete, source, path);
    hardAssert(!source.isFromUser(), "Can't have a listen complete from a user source");
  }

  @Override
  public Operation operationForChild(ChildKey childKey) {
    if (this.path.isEmpty()) {
      return new ListenComplete(this.source, Path.getEmptyPath());
    } else {
      return new ListenComplete(this.source, this.path.popFront());
    }
  }

  @Override
  public String toString() {
    return String.format("ListenComplete { path=%s, source=%s }", getPath(), getSource());
  }
}
