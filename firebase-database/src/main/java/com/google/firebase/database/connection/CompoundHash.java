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

package com.google.firebase.database.connection;

import java.util.Collections;
import java.util.List;

public class CompoundHash {
  private final List<List<String>> posts;
  private final List<String> hashes;

  public CompoundHash(List<List<String>> posts, List<String> hashes) {
    if (posts.size() != hashes.size() - 1) {
      throw new IllegalArgumentException(
          "Number of posts need to be n-1 for n hashes in " + "CompoundHash");
    }
    this.posts = posts;
    this.hashes = hashes;
  }

  public List<List<String>> getPosts() {
    return Collections.unmodifiableList(this.posts);
  }

  public List<String> getHashes() {
    return Collections.unmodifiableList(this.hashes);
  }
}
