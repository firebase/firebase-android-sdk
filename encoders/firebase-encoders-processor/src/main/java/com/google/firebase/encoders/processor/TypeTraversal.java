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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

final class TypeTraversal {

  /**
   * Traverses the type graph starting at {@code type}.
   *
   * <p>{@link TypeVisitor} is used to process each type in the graph as well as return a list of
   * types to traverse next based on the type in receives.
   */
  static <T> Set<T> traverse(TypeMirror type, TypeVisitor<T> visitor) {
    Set<String> visited = new HashSet<>();
    Set<T> accumulated = new LinkedHashSet<>();
    traverseType(type, visitor, visited, accumulated);
    return accumulated;
  }

  private static <T> void traverseType(
      TypeMirror type, TypeVisitor<T> visitor, Set<String> visited, Set<T> accumulated) {
    if (visited.contains(type.toString())) {
      return;
    }
    visited.add(type.toString());

    VisitResult<T> result = visitor.visit(type);
    if (result.isEmpty()) {
      return;
    }

    accumulated.add(result.result());

    result.pendingToVisit().forEach(t -> traverseType(t, visitor, visited, accumulated));
  }
}
