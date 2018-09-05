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

package com.google.firebase.database.core.utilities;

import com.google.firebase.database.snapshot.ChildKey;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TreeNode<T> {

  public Map<ChildKey, TreeNode<T>> children;
  public T value;

  public TreeNode() {
    children = new HashMap<ChildKey, TreeNode<T>>();
  }

  String toString(String prefix) {
    String result = prefix + "<value>: " + value + "\n";
    if (children.isEmpty()) {
      return result + prefix + "<empty>";
    } else {
      Iterator<Map.Entry<ChildKey, TreeNode<T>>> iter = children.entrySet().iterator();
      while (iter.hasNext()) {
        Map.Entry<ChildKey, TreeNode<T>> entry = iter.next();
        result += prefix + entry.getKey() + ":\n" + entry.getValue().toString(prefix + "\t") + "\n";
      }
    }
    return result;
  }
}
