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

package com.google.firebase.firestore.spec;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.ViewSnapshot;

/** Object that contains exactly one of either a view snapshot or an error for the given query. */
public class QueryEvent {
  public Query query;
  public @Nullable ViewSnapshot view;
  public @Nullable FirebaseFirestoreException error;

  @Override
  public String toString() {
    return "QueryEvent(" + query + ", " + view + ", " + error + ")";
  }
}
