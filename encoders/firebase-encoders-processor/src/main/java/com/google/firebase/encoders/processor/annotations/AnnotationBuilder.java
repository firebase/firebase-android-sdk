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
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;
import com.squareup.javapoet.WildcardTypeName;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

/**
 * Generates an annotation implementation.
 *
 * <p>For example, given the following annotation
 * <pre>{@code
 * @interface MyAnnotation {
 *   int value();
 *   String str() default "hello";
 * }
 * }</pre>
 * The produced code will look like:
 *
 * <pre>{@code
 * public final class AtMyAnnotation {
 *   private int value;
 *   private String str = "hello";
 *
 *   public static AtMyAnnotation builder() {
 *     return new AtMyAnnotation();
 *   }
 *
 *   public MyAnnotation value(int value) { this.value = value; return this; }
 *   public MyAnnotation str(String str) { this.str = str; return this; }
 *
 *   public MyAnnotation build() { return new MyAnnotationImpl(value, str); }
 *
 *   private static class MyAnnotationImpl implements MyAnnotation {
 *     // implementation details ...
 *   }
 *
 * }
 * }</pre>
 */
public final class AnnotationBuilder {
  private AnnotationBuilder() {}

  public static TypeSpec generate(TypeElement element) {
    ClassName annotation = ClassName.get(element);
    ClassName builderName = builderName(annotation);

    return createBuilder(builderName, annotation, createAnnotationImpl(element));
  }

  private static TypeSpec createBuilder(
      ClassName builderName, ClassName annotationName, AnnotationImpl annotationImpl) {
    Builder annotationBuilder =
        TypeSpec.classBuilder(builderName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addType(annotationImpl.typeSpec);
    List<FieldSpec> fields = annotationImpl.typeSpec.fieldSpecs;

    if (fields.isEmpty()) {
      annotationBuilder.addMethod(
          MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());
      annotationBuilder.addField(
          FieldSpec.builder(builderName, "BUILDER")
              .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
              .initializer("new $T()", builderName)
              .build());
      annotationBuilder.addField(
          FieldSpec.builder(annotationName, "INSTANCE")
              .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
              .initializer("new $N()", annotationImpl.typeSpec)
              .build());
      annotationBuilder.addMethod(
          MethodSpec.methodBuilder("builder")
              .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
              .returns(builderName)
              .addCode("return BUILDER;\n")
              .build());
      annotationBuilder.addMethod(
          MethodSpec.methodBuilder("build")
              .addModifiers(Modifier.PUBLIC)
              .returns(annotationName)
              .addCode("return INSTANCE;\n")
              .build());
      return annotationBuilder.build();
    }

    MethodSpec.Builder builderMethod =
        MethodSpec.methodBuilder("builder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(builderName)
            .addCode("return new $T(", builderName);
    for (FieldSpec field : fields) {
      FieldSpec.Builder fieldSpec =
          FieldSpec.builder(field.type, field.name).addModifiers(Modifier.PRIVATE);
      AnnotationValue value = annotationImpl.defaults.get(field);
      if (value != null) {
        fieldSpec.initializer("$L", value);
      }
      annotationBuilder.addMethod(
          MethodSpec.methodBuilder(field.name)
              .addModifiers(Modifier.PUBLIC)
              .addParameter(field.type, field.name)
              .returns(builderName)
              .addCode("this.$1N = $1N;\n", field.name)
              .addCode("return this;\n")
              .build());
      annotationBuilder.addField(fieldSpec.build());
    }
    builderMethod.addCode(");\n");

    MethodSpec.Builder buildMethod =
        MethodSpec.methodBuilder("build")
            .addModifiers(Modifier.PUBLIC)
            .returns(annotationName)
            .addCode("return new $N(", annotationImpl.typeSpec);
    Iterator<FieldSpec> iterator = fields.iterator();
    while (iterator.hasNext()) {
      FieldSpec field = iterator.next();
      buildMethod.addCode("$L", field.name);
      if (iterator.hasNext()) {
        buildMethod.addCode(", ");
      }
    }
    buildMethod.addCode(");\n");

    annotationBuilder.addMethod(builderMethod.build());
    annotationBuilder.addMethod(buildMethod.build());
    return annotationBuilder.build();
  }

  private static ClassName builderName(ClassName annotation) {
    return ClassName.get(annotation.packageName(), compositeName(annotation));
  }

  private static String compositeName(ClassName annotation) {
    ClassName parentName = annotation.enclosingClassName();
    if (parentName == null) {
      return "At" + annotation.simpleName();
    }
    return compositeName(parentName) + annotation.simpleName();
  }

  private static AnnotationImpl createAnnotationImpl(TypeElement annotation) {
    String implName = annotation.getSimpleName().toString() + "Impl";
    Builder annotationImpl =
        TypeSpec.classBuilder(implName)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .addSuperinterface(TypeName.get(annotation.asType()))
            .addMethod(
                MethodSpec.methodBuilder("annotationType")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(
                        ParameterizedTypeName.get(
                            ClassName.get(Class.class),
                            WildcardTypeName.subtypeOf(Annotation.class)))
                    .addCode("return $T.class;\n", annotation)
                    .build());
    List<ParameterSpec> parameters = new ArrayList<>();
    Map<FieldSpec, AnnotationValue> defaults = new HashMap<>();
    for (ExecutableElement method : ElementFilter.methodsIn(annotation.getEnclosedElements())) {
      FieldSpec field =
          FieldSpec.builder(
                  TypeName.get(method.getReturnType()),
                  method.getSimpleName().toString(),
                  Modifier.PRIVATE,
                  Modifier.FINAL)
              .build();
      if (method.getDefaultValue() != null) {
        defaults.put(field, method.getDefaultValue());
      }

      annotationImpl
          .addField(field)
          .addMethod(
              MethodSpec.methodBuilder(field.name)
                  .addModifiers(Modifier.PUBLIC)
                  .addAnnotation(Override.class)
                  .returns(field.type)
                  .addCode("return $N;\n", field)
                  .build());
      parameters.add(
          ParameterSpec.builder(
                  TypeName.get(method.getReturnType()), method.getSimpleName().toString())
              .build());
    }

    MethodSpec.Builder constructorBuilder =
        MethodSpec.constructorBuilder().addParameters(parameters);
    for (ParameterSpec parameter : parameters) {
      constructorBuilder.addCode("this.$1N = $1N;\n", parameter.name);
    }
    annotationImpl.addMethod(constructorBuilder.build());

    annotationImpl.addMethod(EqualsMethod.generate(annotation));
    annotationImpl.addMethod(HashCodeMethod.generate(annotation));
    annotationImpl.addMethod(ToStringMethod.generate(annotation));

    return new AnnotationImpl(annotationImpl.build(), defaults);
  }

  private static class AnnotationImpl {
    final TypeSpec typeSpec;
    final Map<FieldSpec, AnnotationValue> defaults;

    private AnnotationImpl(TypeSpec typeSpec, Map<FieldSpec, AnnotationValue> defaults) {
      this.typeSpec = typeSpec;
      this.defaults = defaults;
    }
  }
}
