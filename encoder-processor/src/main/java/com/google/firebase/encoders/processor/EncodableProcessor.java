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

import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

@AutoService(Processor.class)
@SupportedAnnotationTypes(EncodableProcessor.ENCODABLE_ANNOTATION)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class EncodableProcessor extends AbstractProcessor {

  public static final String ENCODABLE_ANNOTATION =
      "com.google.firebase.encoders.annotations.Encodable";
  private Elements elements;
  private Types types;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);
    elements = processingEnvironment.getElementUtils();
    types = processingEnvironment.getTypeUtils();
  }

  @Override
  public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
    for (Element element :
        roundEnvironment.getElementsAnnotatedWith(elements.getTypeElement(ENCODABLE_ANNOTATION))) {
      processClass(element);
    }
    return false;
  }

  private void processClass(Element element) {

    // generates class of the following shape:
    //
    // public class AutoFooEncoder {
    //   public String encode(Foo foo) throws EncodingException;
    //   public void encode(Foo foo, Writer writer) throws IOException, EncodingException;
    // }
    TypeSpec.Builder encoderBuilder =
        TypeSpec.classBuilder("Auto" + getGeneratedClassName(element) + "Encoder")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(
                AnnotationSpec.builder(ClassName.get("javax.annotation", "Generated"))
                    .addMember("value", "$S", getClass().getName())
                    .build())
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
            .addField(
                FieldSpec.builder(
                        ClassName.get("com.google.firebase.encoders", "DataEncoder"),
                        "ENCODER",
                        Modifier.PRIVATE,
                        Modifier.STATIC,
                        Modifier.FINAL)
                    .build());

    Map<TypeMirror, TypeSpec> outTypeEncoders = new HashMap<>();

    // walks the types starting with target class and recursively generates encoders for all
    // required classes.
    traverseElement(element, outTypeEncoders);

    // creates a JSON DataEncoder that can encode all classes generated above
    CodeBlock.Builder initializer =
        CodeBlock.builder()
            .add(
                "ENCODER = new $T()\n",
                ClassName.get("com.google.firebase.encoders.json", "JsonDataEncoderBuilder"))
            .add(CodeBlock.builder().indent().build());
    for (Map.Entry<TypeMirror, TypeSpec> entry : outTypeEncoders.entrySet()) {
      encoderBuilder.addType(entry.getValue());
      initializer.add(".registerEncoder($T.class, new $N())\n", entry.getKey(), entry.getValue());
    }
    initializer.add(".build();\n");
    encoderBuilder.addStaticBlock(initializer.build());

    encoderBuilder.addMethod(
        MethodSpec.methodBuilder("encode")
            .returns(String.class)
            .addModifiers(Modifier.STATIC, Modifier.PUBLIC)
            .addParameter(TypeName.get(element.asType()), "value")
            .addException(ClassName.get("com.google.firebase.encoders", "EncodingException"))
            .addCode("return ENCODER.encode(value);\n")
            .build());
    encoderBuilder.addMethod(
        MethodSpec.methodBuilder("encode")
            .addModifiers(Modifier.STATIC, Modifier.PUBLIC)
            .addParameter(TypeName.get(element.asType()), "value")
            .addParameter(Writer.class, "writer")
            .addException(ClassName.get("com.google.firebase.encoders", "EncodingException"))
            .addException(IOException.class)
            .addCode("ENCODER.encode(value, writer);\n")
            .build());

    JavaFile file = JavaFile.builder(getPackageName(element), encoderBuilder.build()).build();

    try {
      file.writeTo(processingEnv.getFiler());
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private String getGeneratedClassName(Element element) {
    StringBuilder sb = new StringBuilder(element.getSimpleName().toString());
    Element enclosingElement = element.getEnclosingElement();
    while (!(enclosingElement instanceof PackageElement)) {
      sb.insert(0, enclosingElement.getSimpleName().toString());
      enclosingElement = enclosingElement.getEnclosingElement();
    }
    return sb.toString();
  }

  private void traverseElement(Element element, Map<TypeMirror, TypeSpec> outTypeEncoders) {
    if (outTypeEncoders.containsKey(element.asType())) {
      return;
    }

    // add encoder early so that we don't infinitely recurse in case of types containing themselves.
    outTypeEncoders.put(element.asType(), null);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder("encode")
            .addParameter(TypeName.get(element.asType()), "value")
            .addParameter(
                ClassName.get("com.google.firebase.encoders", "ObjectEncoderContext"), "ctx")
            .addModifiers(Modifier.PUBLIC)
            .addException(IOException.class)
            .addException(ClassName.get("com.google.firebase.encoders", "EncodingException"))
            .addAnnotation(Override.class);

    for (Element enclosedElement : element.getEnclosedElements()) {
      Getter getter = toGetter(enclosedElement);
      if (getter == null) {
        continue;
      }
      if (shouldCreateEncoderFor(getter.getUnderlyingType())) {
        traverseElement(types.asElement(getter.getUnderlyingType()), outTypeEncoders);
      }
      methodBuilder.addCode("ctx.add($S, value.$L());\n", getter.name, getter.methodName);
    }

    TypeSpec encoder =
        TypeSpec.classBuilder(getGeneratedClassName(element) + "Encoder")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .addSuperinterface(
                ParameterizedTypeName.get(
                    ClassName.get("com.google.firebase.encoders", "ObjectEncoder"),
                    TypeName.get(element.asType())))
            .addMethod(methodBuilder.build())
            .build();
    outTypeEncoders.put(element.asType(), encoder);
  }

  private boolean shouldCreateEncoderFor(TypeMirror type) {
    // TODO: skip all supported types like java.util.Date
    if (type.getKind().isPrimitive()) {
      return false;
    }
    TypeMirror collection = types.erasure(elements.getTypeElement("java.util.Collection").asType());
    TypeMirror map = types.erasure(elements.getTypeElement("java.util.Map").asType());
    if (types.isAssignable(type, collection)
        || types.isAssignable(type, map)
        || "java.lang".equals(getPackageName(types.asElement(type)))) {
      return false;
    }
    return true;
  }

  private Getter toGetter(Element element) {
    if (element.getKind() != ElementKind.METHOD
        || element.getModifiers().contains(Modifier.STATIC)
        || !element.getModifiers().contains(Modifier.PUBLIC)) {
      return null;
    }
    String methodName = element.getSimpleName().toString();
    ExecutableType method = (ExecutableType) element.asType();
    if (!method.getParameterTypes().isEmpty()) {
      return null;
    }

    if (methodName.startsWith("is")
        && methodName.length() != 2
        && method.getReturnType().getKind() == TypeKind.BOOLEAN) {
      String getterName = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
      return new Getter(getterName, methodName, types.getPrimitiveType(TypeKind.BOOLEAN));
    }

    if (methodName.startsWith("get")
        && methodName.length() != 3
        && method.getReturnType().getKind() != TypeKind.VOID) {
      String getterName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
      return new Getter(getterName, methodName, method.getReturnType());
    }

    return null;
  }

  private String getPackageName(Element element) {
    Element enclosingElement = element.getEnclosingElement();
    while (!(enclosingElement instanceof PackageElement)) {
      enclosingElement = enclosingElement.getEnclosingElement();
    }
    return ((PackageElement) enclosingElement).getQualifiedName().toString();
  }

  private static class Getter {
    final String name;
    final String methodName;
    final TypeMirror returnType;

    private Getter(String name, String methodName, TypeMirror returnType) {
      this.name = name;
      this.methodName = methodName;
      this.returnType = returnType;
    }

    private TypeMirror getUnderlyingType() {
      if (returnType.getKind() != TypeKind.ARRAY) {
        return returnType;
      }
      return ((ArrayType) returnType).getComponentType();
    }
  }
}
