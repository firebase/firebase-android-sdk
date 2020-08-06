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

package com.google.firebase.encoders.processor.getters;

import com.google.firebase.encoders.annotations.Encodable;
import com.google.firebase.encoders.annotations.ExtraProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

public final class GetterFactory {
  private final Types types;
  private final Elements elements;
  private final Messager messager;

  public GetterFactory(Types types, Elements elements, Messager messager) {
    this.types = types;
    this.elements = elements;
    this.messager = messager;
  }

  /** Returns all getters of a given type. */
  public Set<Getter> allGetters(DeclaredType type) {
    if (types.isSameType(type, elements.getTypeElement("java.lang.Object").asType())) {
      return Collections.emptySet();
    }
    Set<Getter> result = new LinkedHashSet<>();
    TypeMirror superclass = ((TypeElement) types.asElement(type)).getSuperclass();

    if (!superclass.getKind().equals(TypeKind.NONE)) {
      result.addAll(allGetters((DeclaredType) superclass));
    }
    for (ExecutableElement method :
        ElementFilter.methodsIn(types.asElement(type).getEnclosedElements())) {
      create(type, method).ifPresent(result::add);
    }
    return result;
  }

  private Optional<Getter> create(DeclaredType ownerType, ExecutableElement element) {
    if (element.getKind() != ElementKind.METHOD
        || element.getModifiers().contains(Modifier.STATIC)
        || !element.getModifiers().contains(Modifier.PUBLIC)) {
      return Optional.empty();
    }

    ExecutableType method = (ExecutableType) element.asType();
    if (!method.getParameterTypes().isEmpty()) {
      return Optional.empty();
    }

    if (element.getAnnotation(Encodable.Ignore.class) != null) {
      return Optional.empty();
    }
    Optional<String> fieldName = inferName(element);
    if (!fieldName.isPresent()) {
      return Optional.empty();
    }
    TypeMirror returnType = resolveTypeArguments(ownerType, element.getReturnType());
    String getterExpression = element.toString();

    // Fail to compile if Maps with non-string keys are used, if/when we add support for such maps
    // we should delete this.
    TypeMirror map = types.erasure(elements.getTypeElement("java.util.Map").asType());
    if (types.isAssignable(returnType, map)) {
      TypeMirror keyType = ((DeclaredType) returnType).getTypeArguments().get(0);
      if (!types.isSameType(keyType, elements.getTypeElement("java.lang.String").asType())) {
        messager.printMessage(
            Diagnostic.Kind.ERROR,
            "Cannot encode Maps with non-String keys.",
            ((DeclaredType) returnType).asElement());
      }
    }
    if (types.isAssignable(
        returnType, types.erasure(elements.getTypeElement("java.util.Optional").asType()))) {
      returnType = ((DeclaredType) returnType).getTypeArguments().get(0);
      getterExpression = getterExpression + ".orElse(null)";
    }

    Encodable.Field field = element.getAnnotation(Encodable.Field.class);

    return Optional.of(
        Getter.create(
            fieldName.get(),
            inferDescriptors(element),
            getterExpression,
            returnType,
            field != null && field.inline()));
  }

  private Set<AnnotationDescriptor> inferDescriptors(ExecutableElement element) {
    Set<AnnotationDescriptor> annotationDescriptors = new HashSet<>();
    for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
      ExtraProperty extraProperty =
          annotationMirror.getAnnotationType().asElement().getAnnotation(ExtraProperty.class);
      if (extraProperty == null) {
        continue;
      }
      List<AnnotationProperty> annotationValues =
          annotationMirror.getElementValues().entrySet().stream()
              .map(
                  e ->
                      AnnotationProperty.create(
                          e.getKey().getSimpleName().toString(), e.getValue()))
              .collect(Collectors.toList());
      annotationDescriptors.add(AnnotationDescriptor.create(annotationMirror, annotationValues));
    }
    return annotationDescriptors;
  }

  private TypeMirror resolveTypeArguments(DeclaredType ownerType, TypeMirror genericType) {

    if (genericType instanceof DeclaredType) {
      DeclaredType dType = (DeclaredType) genericType;

      if (dType.getTypeArguments().isEmpty()) {
        return genericType;
      }

      TypeElement genericElement = (TypeElement) dType.asElement();

      List<TypeMirror> result = new ArrayList<>();
      for (TypeMirror typeArgument : dType.getTypeArguments()) {
        result.add(resolveTypeArguments(ownerType, typeArgument));
      }

      return types.getDeclaredType(genericElement, result.toArray(new TypeMirror[0]));
    } else if (genericType instanceof TypeVariable) {
      TypeVariable tVar = (TypeVariable) genericType;
      TypeElement ownerElement = (TypeElement) ownerType.asElement();

      int index = ownerElement.getTypeParameters().indexOf(tVar.asElement());
      if (index == -1) {
        messager.printMessage(
            Diagnostic.Kind.ERROR,
            String.format(
                "Could not infer type of %s in %s. Is it a non-static inner class in a generic class?",
                genericType, ownerType));
        return genericType;
      }
      if (ownerType.getTypeArguments().get(index) instanceof TypeVariable) {
        messager.printMessage(
            Diagnostic.Kind.WARNING,
            String.format(
                "%s is a generic type, make sure you register encoders for types of %s that you plan to use.",
                ownerType, genericType));
        return genericType;
      }
      return resolveTypeArguments(ownerType, ownerType.getTypeArguments().get(index));
    }
    return genericType;
  }

  private static Optional<String> inferName(ExecutableElement element) {
    Encodable.Field annotation = element.getAnnotation(Encodable.Field.class);
    if (annotation != null && !annotation.name().isEmpty()) {
      return Optional.of(annotation.name());
    }

    String methodName = element.getSimpleName().toString();
    ExecutableType method = (ExecutableType) element.asType();

    if (methodName.startsWith("is")
        && methodName.length() != 2
        && method.getReturnType().getKind() == TypeKind.BOOLEAN) {
      return Optional.of(Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3));
    }

    if (methodName.startsWith("get")
        && methodName.length() != 3
        && method.getReturnType().getKind() != TypeKind.VOID) {
      return Optional.of(Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4));
    }
    return Optional.empty();
  }
}
