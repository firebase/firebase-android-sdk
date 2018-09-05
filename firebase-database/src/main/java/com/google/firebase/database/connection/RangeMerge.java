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

import java.util.List;

public class RangeMerge {
  private final List<String> optExclusiveStart;
  private final List<String> optInclusiveEnd;
  private final Object snap;

  public RangeMerge(List<String> optExclusiveStart, List<String> optInclusiveEnd, Object snap) {
    this.optExclusiveStart = optExclusiveStart;
    this.optInclusiveEnd = optInclusiveEnd;
    this.snap = snap;
  }

  public List<String> getOptExclusiveStart() {
    return this.optExclusiveStart;
  }

  public List<String> getOptInclusiveEnd() {
    return this.optInclusiveEnd;
  }

  public Object getSnap() {
    return this.snap;
  }
}
