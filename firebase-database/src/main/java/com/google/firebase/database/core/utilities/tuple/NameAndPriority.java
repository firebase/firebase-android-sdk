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

package com.google.firebase.database.core.utilities.tuple;

import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;

public class NameAndPriority implements Comparable<NameAndPriority> {

  private ChildKey name;

  private Node priority;

  public NameAndPriority(ChildKey name, Node priority) {
    this.name = name;
    this.priority = priority;
  }

  public ChildKey getName() {
    return name;
  }

  public Node getPriority() {
    return priority;
  }

  @Override
  public int compareTo(NameAndPriority o) {
    return NodeUtilities.nameAndPriorityCompare(this.name, this.priority, o.name, o.priority);
  }
}
