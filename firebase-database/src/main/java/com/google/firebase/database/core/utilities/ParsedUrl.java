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

import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.RepoInfo;

public final class ParsedUrl {

  public RepoInfo repoInfo;
  public Path path;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ParsedUrl parsedUrl = (ParsedUrl) o;

    if (!repoInfo.equals(parsedUrl.repoInfo)) return false;
    return path.equals(parsedUrl.path);
  }

  @Override
  public int hashCode() {
    int result = repoInfo.hashCode();
    result = 31 * result + path.hashCode();
    return result;
  }
}
