// Copyright 2020 Google LLC
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

package com.google.firebase.encoders.processor.annotations;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;

/**
 * Generates compliant equals() method implementation.
 *
 * @see <a
 *     href="https://docs.oracle.com/javase/7/docs/api/java/lang/annotation/Annotation.html#equals(java.lang.Object)">Annotation#equals()</a>
 *     requirements.
 */
class EqualsMethod {
  static MethodSpec generate(TypeElement annotation) {
    MethodSpec.Builder equalsBuilder =
        MethodSpec.methodBuilder("equals")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(boolean.class)
            .addParameter(Object.class, "other");
    equalsBuilder.addCode(
        CodeBlock.builder()
            .add("if (this == other) return true;\n")
            .add("if (!(other instanceof $T)) return false;\n", annotation)
            .add("$1T that = ($1T) other;\n\n", annotation)
            .build());

    List<CodeBlock> equalExpressions = new ArrayList<>();

    for (ExecutableElement method : ElementFilter.methodsIn(annotation.getEnclosedElements())) {
      TypeKind resultKind = method.getReturnType().getKind();
      if (resultKind.isPrimitive()) {
        if (resultKind.equals(TypeKind.FLOAT)) {
          equalExpressions.add(
              CodeBlock.builder()
                  .add(
                      "(Float.floatToIntBits($1L) == Float.floatToIntBits(that.$1L()))",
                      method.getSimpleName().toString())
                  .build());
          continue;
        }
        if (resultKind.equals(TypeKind.DOUBLE)) {
          equalExpressions.add(
              CodeBlock.builder()
                  .add(
                      "(Double.doubleToLongBits($1L) == Double.doubleToLongBits(that.$1L()))",
                      method.getSimpleName().toString())
                  .build());
          continue;
        }
        equalExpressions.add(
            CodeBlock.builder()
                .add("($1L == that.$1L())", method.getSimpleName().toString())
                .build());
      } else {
        if (resultKind.equals(TypeKind.ARRAY)) {
          equalExpressions.add(
              CodeBlock.builder()
                  .add(
                      "($1T.equals($2L, that.$2L()))",
                      Arrays.class,
                      method.getSimpleName().toString())
                  .build());
        }
        equalExpressions.add(
            CodeBlock.builder()
                .add("($1L.equals(that.$1L()))", method.getSimpleName().toString())
                .build());
      }
    }
    if (equalExpressions.isEmpty()) {
      equalsBuilder.addCode("return true;\n");
    } else {
      equalsBuilder.addCode("return $L;\n", CodeBlock.join(equalExpressions, "\n    && "));
    }
    return equalsBuilder.build();
  }
}
