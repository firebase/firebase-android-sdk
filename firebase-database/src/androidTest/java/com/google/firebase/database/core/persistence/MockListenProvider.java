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

package com.google.firebase.database.core.persistence;

import com.google.firebase.database.connection.ListenHashProvider;
import com.google.firebase.database.core.SyncTree;
import com.google.firebase.database.core.Tag;
import com.google.firebase.database.core.view.QuerySpec;
import java.util.HashSet;
import java.util.Set;

public class MockListenProvider implements SyncTree.ListenProvider {
  private Set<QuerySpec> listens = new HashSet<QuerySpec>();

  @Override
  public void startListening(
      QuerySpec query,
      Tag tag,
      ListenHashProvider hash,
      SyncTree.CompletionListener onListenComplete) {
    listens.add(query);
  }

  @Override
  public void stopListening(QuerySpec query, Tag tag) {
    listens.remove(query);
  }

  public boolean hasListen(QuerySpec query) {
    return listens.contains(query);
  }
}
