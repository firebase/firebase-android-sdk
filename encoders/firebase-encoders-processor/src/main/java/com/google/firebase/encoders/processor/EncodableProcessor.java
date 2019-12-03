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
import com.google.firebase.encoders.annotations.Encodable;
import com.google.firebase.encoders.processor.getters.Getter;
import com.google.firebase.encoders.processor.getters.GetterFactory;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

@AutoService(Processor.class)
@SupportedAnnotationTypes(EncodableProcessor.ENCODABLE_ANNOTATION)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class EncodableProcessor extends AbstractProcessor {

  static final String ENCODABLE_ANNOTATION = "com.google.firebase.encoders.annotations.Encodable";
  private Elements elements;
  private Types types;
  private GetterFactory getterFactory;
  private TypeTraversal traversal;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);
    elements = processingEnvironment.getElementUtils();
    types = processingEnvironment.getTypeUtils();
    getterFactory = new GetterFactory(types, elements, processingEnvironment.getMessager());
  }

  @Override
  public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
    for (Element element : roundEnvironment.getElementsAnnotatedWith(Encodable.class)) {
      processClass(element);
    }
    return false;
  }

  private void processClass(Element element) {
    // generates class of the following shape:
    //
    // public class AutoFooEncoder implements Configurator {
    //   public static final Configurator CONFIG = new AutoFooEncoder();
    // }
    ClassName className =
        ClassName.bestGuess("Auto" + Names.generatedClassName(element) + "Encoder");
    ClassName configurator = ClassName.get("com.google.firebase.encoders.config", "Configurator");
    TypeSpec.Builder encoderBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc("@hide")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(configurator)
            .addAnnotation(
                AnnotationSpec.builder(ClassName.get("javax.annotation", "Generated"))
                    .addMember("value", "$S", getClass().getName())
                    .build())
            .addField(
                FieldSpec.builder(
                        TypeName.INT,
                        "CODEGEN_VERSION",
                        Modifier.PUBLIC,
                        Modifier.STATIC,
                        Modifier.FINAL)
                    .initializer("1")
                    .build())
            .addField(
                FieldSpec.builder(
                        configurator, "CONFIG", Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
                    .initializer("new $T()", className)
                    .build())
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    Set<Encoder> encoders = TypeTraversal.traverse(element.asType(), new GetterVisitor());

    MethodSpec.Builder configureMethod =
        MethodSpec.methodBuilder("configure")
            .addParameter(
                ParameterizedTypeName.get(
                    ClassName.get("com.google.firebase.encoders.config", "EncoderConfig"),
                    WildcardTypeName.subtypeOf(Object.class)),
                "cfg")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class);

    for (Encoder encoder : encoders) {
      encoderBuilder.addType(encoder.code());

      configureMethod.addCode(
          "cfg.registerEncoder($T.class, $N.INSTANCE);\n",
          types.erasure(encoder.type()),
          encoder.code());
    }
    encoderBuilder.addMethod(configureMethod.build());

    JavaFile file = JavaFile.builder(Names.packageName(element), encoderBuilder.build()).build();

    try {
      file.writeTo(processingEnv.getFiler());
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  class GetterVisitor implements TypeVisitor<Encoder> {
    final Map<TypeMirror, TypeSpec> encoded = new LinkedHashMap<>();

    @Override
    public VisitResult<Encoder> visit(TypeMirror type) {
      if (!(type instanceof DeclaredType)) {
        return VisitResult.noResult();
      }

      MethodSpec.Builder methodBuilder =
          MethodSpec.methodBuilder("encode")
              .addParameter(TypeName.get(types.erasure(type)), "value")
              .addParameter(
                  ClassName.get("com.google.firebase.encoders", "ObjectEncoderContext"), "ctx")
              .addModifiers(Modifier.PUBLIC)
              .addException(IOException.class)
              .addException(ClassName.get("com.google.firebase.encoders", "EncodingException"))
              .addAnnotation(Override.class);

      Set<TypeMirror> result = new LinkedHashSet<>();
      for (Getter getter : getterFactory.allGetters((DeclaredType) type)) {
        if (shouldCreateEncoderFor(getter.getUnderlyingType())) {
          result.add(getter.getUnderlyingType());
        }
        methodBuilder.addCode("ctx.add($S, value.$L);\n", getter.name(), getter.expression());
      }

      ClassName className =
          ClassName.bestGuess(Names.generatedClassName(types.asElement(type)) + "Encoder");
      TypeSpec encoder =
          TypeSpec.classBuilder(className)
              .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
              .addSuperinterface(
                  ParameterizedTypeName.get(
                      ClassName.get("com.google.firebase.encoders", "ObjectEncoder"),
                      TypeName.get(types.erasure(type))))
              .addField(
                  FieldSpec.builder(className, "INSTANCE", Modifier.FINAL, Modifier.STATIC)
                      .initializer("new $T()", className)
                      .build())
              .addMethod(methodBuilder.build())
              .build();
      encoded.put(types.erasure(type), encoder);
      return VisitResult.of(result, Encoder.create(types.erasure(type), encoder));
    }

    private boolean shouldCreateEncoderFor(TypeMirror type) {
      if (type.getKind().isPrimitive()) {
        return false;
      }
      TypeMirror collection =
          types.erasure(elements.getTypeElement("java.util.Collection").asType());
      TypeMirror map = types.erasure(elements.getTypeElement("java.util.Map").asType());
      TypeMirror date = elements.getTypeElement("java.util.Date").asType();
      if (types.isAssignable(type, collection)
          || types.isAssignable(type, map)
          || types.isAssignable(type, date)
          || "java.lang".equals(Names.packageName(types.asElement(type)))) {
        return false;
      }
      return true;
    }
  }
}
