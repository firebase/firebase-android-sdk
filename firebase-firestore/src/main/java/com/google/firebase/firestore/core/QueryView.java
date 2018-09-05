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

package com.google.firebase.firestore.core;

/**
 * QueryView contains all of the info that SyncEngine needs to track for a particular query and
 * view.
 */
final class QueryView {
  private final Query query;
  private final int targetId;
  private final View view;

  QueryView(Query query, int targetId, View view) {
    this.query = query;
    this.targetId = targetId;
    this.view = view;
  }

  public Query getQuery() {
    return query;
  }

  public int getTargetId() {
    return targetId;
  }

  public View getView() {
    return view;
  }
}
