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
import java.util.Arrays;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;

/**
 * Generates compliant hashCode() method implementation.
 *
 * @see <a
 *     href="https://docs.oracle.com/javase/7/docs/api/java/lang/annotation/Annotation.html#hashCode()">Annotation#hashCode()</a>
 *     requirements.
 */
class HashCodeMethod {
  static MethodSpec generate(TypeElement annotation) {
    MethodSpec.Builder hashCodeBuilder =
        MethodSpec.methodBuilder("hashCode")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(int.class);

    List<ExecutableElement> methods = ElementFilter.methodsIn(annotation.getEnclosedElements());
    if (methods.isEmpty()) {
      hashCodeBuilder.addCode("return 0;\n");
      return hashCodeBuilder.build();
    }

    CodeBlock.Builder code = CodeBlock.builder().add("return ");
    for (ExecutableElement method : methods) {
      code.add(
          "+ ($L ^ $L)\n",
          127 * method.getSimpleName().toString().hashCode(),
          hashExpression(method));
    }
    code.add(";\n");
    hashCodeBuilder.addCode(code.build());

    return hashCodeBuilder.build();
  }

  private static CodeBlock hashExpression(ExecutableElement method) {
    TypeKind returnKind = method.getReturnType().getKind();
    if (returnKind.isPrimitive()) {
      if (returnKind.equals(TypeKind.FLOAT)) {
        return CodeBlock.builder()
            .add("(Float.floatToIntBits($L))", method.getSimpleName())
            .build();
      }
      if (returnKind.equals(TypeKind.DOUBLE)) {
        return CodeBlock.builder()
            .add(
                "((int) ((Double.doubleToLongBits($1L) >>> 32) ^ Double.doubleToLongBits($1L)))",
                method.getSimpleName())
            .build();
      }
      if (returnKind.equals(TypeKind.BOOLEAN)) {
        return CodeBlock.builder().add("($L ? 1231 : 1237)", method.getSimpleName()).build();
      }
      if (returnKind.equals(TypeKind.LONG)) {
        return CodeBlock.builder()
            .add("((int)($1L ^ ($1L >>> 32)))", method.getSimpleName())
            .build();
      }
      return CodeBlock.builder().add("((int)$L)", method.getSimpleName()).build();
    }
    if (returnKind.equals(TypeKind.ARRAY)) {
      return CodeBlock.builder()
          .add("$T.hashCode($L)", Arrays.class, method.getSimpleName())
          .build();
    }
    return CodeBlock.builder().add("$L.hashCode()", method.getSimpleName()).build();
  }
}
