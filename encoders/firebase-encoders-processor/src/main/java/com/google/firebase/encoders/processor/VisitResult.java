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

import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;
import java.util.Collections;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

@AutoValue
abstract class VisitResult<T> {
  abstract Set<TypeMirror> pendingToVisit();

  @Nullable
  abstract T result();

  static <T> VisitResult<T> of(Set<TypeMirror> moreToVisit, T result) {
    return new AutoValue_VisitResult<>(moreToVisit, result);
  }

  static <T> VisitResult<T> noResult() {
    return of(Collections.emptySet(), null);
  }

  boolean isEmpty() {
    return result() == null;
  }
}
