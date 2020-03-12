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

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;

public class Names {
  /**
   * Returns the class name that corresponds to a given element.
   *
   * <p>For top level classes returns the class name unchanged. For nested classes returns {@code
   * ParentNested} for {@code Parent$Nested} classes.
   */
  static String generatedClassName(Element element) {
    StringBuilder sb = new StringBuilder(element.getSimpleName().toString());
    Element enclosingElement = element.getEnclosingElement();
    while (!(enclosingElement instanceof PackageElement)) {
      sb.insert(0, enclosingElement.getSimpleName().toString());
      enclosingElement = enclosingElement.getEnclosingElement();
    }
    return sb.toString();
  }

  /** Returns the AutoValue formatted class name for a given element. */
  static String autoValueClassName(Element element) {
    StringBuilder sb = new StringBuilder(element.getSimpleName().toString());
    Element enclosingElement = element.getEnclosingElement();
    while (!(enclosingElement instanceof PackageElement)) {
      sb.insert(0, '_');
      sb.insert(0, enclosingElement.getSimpleName().toString());
      enclosingElement = enclosingElement.getEnclosingElement();
    }
    sb.insert(0, "AutoValue_");
    return sb.toString();
  }

  /** Returns the package name of an element. */
  static String packageName(Element element) {
    Element enclosingElement = element.getEnclosingElement();
    while (!(enclosingElement instanceof PackageElement)) {
      enclosingElement = enclosingElement.getEnclosingElement();
    }
    return ((PackageElement) enclosingElement).getQualifiedName().toString();
  }
}
