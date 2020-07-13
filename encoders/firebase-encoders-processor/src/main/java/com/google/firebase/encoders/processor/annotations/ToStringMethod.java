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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.MethodSpec.Builder;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

final class ToStringMethod {
  static MethodSpec generate(TypeElement element) {
    ClassName.get(element).reflectionName();
    Builder result =
        MethodSpec.methodBuilder("toString")
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addAnnotation(Override.class)
            .addCode(
                CodeBlock.builder()
                    .add(
                        "StringBuilder sb = new StringBuilder(\"@$L\");\n",
                        ClassName.get(element).reflectionName().replace('$', '.'))
                    .build());
    List<ExecutableElement> methods = ElementFilter.methodsIn(element.getEnclosedElements());
    if (!methods.isEmpty()) {
      result.addCode("sb.append('(');\n");
    }
    for (ExecutableElement method : methods) {
      result.addCode("sb.append(\"$1L=\").append($1L);\n", method.getSimpleName());
    }
    if (!methods.isEmpty()) {
      result.addCode("sb.append(')');\n");
    }
    result.addCode("return sb.toString();\n");
    return result.build();
  }
}
