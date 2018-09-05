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
import java.util.Comparator;

public abstract class Index implements Comparator<NamedNode> {

  public abstract boolean isDefinedOn(Node a);

  public boolean indexedValueChanged(Node oldNode, Node newNode) {
    NamedNode oldWrapped = new NamedNode(ChildKey.getMinName(), oldNode);
    NamedNode newWrapped = new NamedNode(ChildKey.getMinName(), newNode);
    return this.compare(oldWrapped, newWrapped) != 0;
  }

  public abstract NamedNode makePost(ChildKey name, Node value);

  public NamedNode minPost() {
    return NamedNode.getMinNode();
  }

  public abstract NamedNode maxPost();

  public abstract String getQueryDefinition();

  public static Index fromQueryDefinition(String str) {
    if (str.equals(".value")) {
      return ValueIndex.getInstance();
    } else if (str.equals(".key")) {
      return KeyIndex.getInstance();
    } else if (str.equals(".priority")) {
      throw new IllegalStateException(
          "queryDefinition shouldn't ever be .priority since it's the default");
    } else {
      return new PathIndex(new Path(str));
    }
  }

  public int compare(NamedNode one, NamedNode two, boolean reverse) {
    if (reverse) {
      return this.compare(two, one);
    } else {
      return this.compare(one, two);
    }
  }
}
