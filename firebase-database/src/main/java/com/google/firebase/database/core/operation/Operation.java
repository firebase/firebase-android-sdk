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

import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.ChildKey;

public abstract class Operation {
  /** */
  public static enum OperationType {
    Overwrite,
    Merge,
    AckUserWrite,
    ListenComplete
  }

  protected final OperationType type;
  protected final OperationSource source;
  protected final Path path;

  protected Operation(OperationType type, OperationSource source, Path path) {
    this.type = type;
    this.source = source;
    this.path = path;
  }

  public Path getPath() {
    return this.path;
  }

  public OperationSource getSource() {
    return this.source;
  }

  public OperationType getType() {
    return this.type;
  }

  public abstract Operation operationForChild(ChildKey childKey);
}
