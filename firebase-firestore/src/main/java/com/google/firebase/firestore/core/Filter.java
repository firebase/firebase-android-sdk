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

import com.google.firebase.firestore.model.Document;
import java.util.List;

public abstract class Filter {
  /** Returns true if a document matches the filter. */
  public abstract boolean matches(Document doc);

  /** A unique ID identifying the filter; used when serializing queries. */
  public abstract String getCanonicalId();

  /** Returns a list of all field filters that are contained within this filter */
  public abstract List<FieldFilter> getFlattenedFilters();

  /** Returns a list of all filters that are contained within this filter */
  public abstract List<Filter> getFilters();
}
