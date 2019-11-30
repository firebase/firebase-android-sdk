// Copyright 2019 Google LLC
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

package com.google.firebase.encoders.processor;

import java.util.Collections;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

class VisitResult<T> {
  final Set<TypeMirror> moreToVisit;
  final T result;

  VisitResult(Set<TypeMirror> moreToVisit, T result) {
    this.moreToVisit = moreToVisit;
    this.result = result;
  }

  static <T> VisitResult<T> noResult() {
    return new VisitResult<>(Collections.emptySet(), null);
  }

  boolean isEmpty() {
    return result == null;
  }
}
