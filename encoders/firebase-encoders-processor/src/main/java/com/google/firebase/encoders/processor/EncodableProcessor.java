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

import androidx.annotation.VisibleForTesting;
import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.firebase.encoders.annotations.Encodable;
import com.google.firebase.encoders.processor.getters.AnnotationDescriptor;
import com.google.firebase.encoders.processor.getters.AnnotationProperty;
import com.google.firebase.encoders.processor.getters.Getter;
import com.google.firebase.encoders.processor.getters.GetterFactory;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

  private static final String CODEGEN_VERSION = "2";
  static final String ENCODABLE_ANNOTATION = "com.google.firebase.encoders.annotations.Encodable";
  private Elements elements;
  private Types types;
  private GetterFactory getterFactory;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

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
    if (types.isAssignable(
        element.asType(), elements.getTypeElement("java.lang.annotation.Annotation").asType())) {
      return;
    }

    // generates class of the following shape:
    //
    // public class AutoFooEncoder implements Configurator {
    //   public static final Configurator CONFIG = new AutoFooEncoder();
    // }
    ClassName className =
        ClassName.bestGuess("Auto" + Names.generatedClassName(element) + "Encoder");
    ClassName configurator = ClassName.get("com.google.firebase.encoders.config", "Configurator");

    // TODO(vkryachko): add @Generated annotation in a way that is compatible with Java versions
    // before and after 9. See https://github.com/google/dagger/pull/882
    TypeSpec.Builder encoderBuilder =
        TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(configurator)
            .addField(
                FieldSpec.builder(
                        TypeName.INT,
                        "CODEGEN_VERSION",
                        Modifier.PUBLIC,
                        Modifier.STATIC,
                        Modifier.FINAL)
                    .initializer(CODEGEN_VERSION)
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

    Multimap<String, TypeSpec> autoValueSupportClasses = ArrayListMultimap.create();

    for (Encoder encoder : encoders) {
      encoderBuilder.addType(encoder.code());

      configureMethod.addCode(
          "cfg.registerEncoder($T.class, $N.INSTANCE);\n",
          types.erasure(encoder.type()),
          encoder.code());
      autoValueSupport(
              Names.packageName(element),
              element.getSimpleName().toString(),
              encoder,
              configureMethod)
          .ifPresent(
              spec -> {
                String packageName = Names.packageName(types.asElement(encoder.type()));
                autoValueSupportClasses.put(packageName, spec);
              });
    }
    encoderBuilder.addMethod(configureMethod.build());

    JavaFile file = JavaFile.builder(Names.packageName(element), encoderBuilder.build()).build();

    try {
      file.writeTo(processingEnv.getFiler());
      for (Map.Entry<String, TypeSpec> autoValue : autoValueSupportClasses.entries()) {
        JavaFile.builder(autoValue.getKey(), autoValue.getValue())
            .build()
            .writeTo(processingEnv.getFiler());
      }
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private Optional<TypeSpec> autoValueSupport(
      String rootPackageName,
      String containingClassName,
      Encoder encoder,
      MethodSpec.Builder configureMethod) {
    Element element = types.asElement(encoder.type());
    AutoValue autoValue = element.getAnnotation(AutoValue.class);
    if (autoValue == null) {
      return Optional.empty();
    }

    String typePackageName = Names.packageName(element);
    ClassName autoValueClass =
        ClassName.get(
            typePackageName,
            Names.autoValueClassName(types.asElement(types.erasure(encoder.type()))));

    if (rootPackageName.equals(typePackageName)) {
      configureMethod.addCode(
          "cfg.registerEncoder($T.class, $N.INSTANCE);\n", autoValueClass, encoder.code());
      return Optional.empty();
    }

    // the generated class has a rather long name but provides uniqueness guarantees.
    TypeSpec supportClass =
        TypeSpec.classBuilder(
                String.format(
                    "Encodable%s%s%sAutoValueSupport",
                    packageNameToCamelCase(rootPackageName),
                    containingClassName,
                    Names.generatedClassName(element)))
            .addModifiers(Modifier.FINAL, Modifier.PUBLIC)
            .addField(
                FieldSpec.builder(
                        ParameterizedTypeName.get(
                            ClassName.get(Class.class),
                            WildcardTypeName.subtypeOf(TypeName.get(encoder.type()))),
                        "TYPE",
                        Modifier.PUBLIC,
                        Modifier.STATIC,
                        Modifier.FINAL)
                    .initializer("$T.class", autoValueClass)
                    .build())
            .build();

    configureMethod.addCode(
        "cfg.registerEncoder($L.$N.TYPE, $N.INSTANCE);\n",
        typePackageName,
        supportClass,
        encoder.code());

    return Optional.of(supportClass);
  }

  @VisibleForTesting
  static String packageNameToCamelCase(String packageName) {
    if (packageName.isEmpty()) {
      return packageName;
    }
    packageName = Character.toUpperCase(packageName.charAt(0)) + packageName.substring(1);

    int dotIndex = packageName.indexOf('.');
    while (dotIndex > -1) {
      String prefix = packageName.substring(0, dotIndex);
      String suffix = "";
      if (dotIndex < packageName.length() - 1) {
        suffix =
            Character.toUpperCase(packageName.charAt(dotIndex + 1))
                + (dotIndex < packageName.length() - 2 ? packageName.substring(dotIndex + 2) : "");
      }
      packageName = prefix + suffix;
      dotIndex = packageName.indexOf('.');
    }
    return packageName;
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
              .addAnnotation(Override.class);

      Set<TypeMirror> result = new LinkedHashSet<>();
      Set<FieldSpec> descriptorFields = new LinkedHashSet<>();
      ClassName fieldDescriptor = ClassName.get("com.google.firebase.encoders", "FieldDescriptor");
      for (Getter getter : getterFactory.allGetters((DeclaredType) type)) {
        result.addAll(getTypesToVisit(getter.getUnderlyingType()));
        if (getter.inline()) {
          methodBuilder.addCode("ctx.inline(value.$L);\n", getter.expression());
        } else {
          CodeBlock.Builder codeBuilder;
          if (getter.annotationDescriptors().isEmpty()) {
            codeBuilder = CodeBlock.builder().add("$T.of($S)", fieldDescriptor, getter.name());
          } else {
            codeBuilder =
                CodeBlock.builder()
                    .add("$T.builder($S)\n", fieldDescriptor, getter.name())
                    .indent()
                    .indent();
            for (AnnotationDescriptor desc : getter.annotationDescriptors()) {
              ClassName annotationBuilder =
                  builderName(
                      ClassName.get((TypeElement) desc.type().getAnnotationType().asElement()));
              codeBuilder.add(".withProperty($T.builder()\n", annotationBuilder);
              for (AnnotationProperty property : desc.properties()) {
                codeBuilder.add("$>.$L($L)\n$<", property.name(), property.value());
              }
              codeBuilder.add("$>.build())\n$<");
            }
            codeBuilder.add(".build()").unindent().unindent();
          }
          descriptorFields.add(
              FieldSpec.builder(
                      fieldDescriptor,
                      getter.name().toUpperCase() + "_DESCRIPTOR",
                      Modifier.PRIVATE,
                      Modifier.FINAL,
                      Modifier.STATIC)
                  .initializer(codeBuilder.build())
                  .build());
          methodBuilder.addCode(
              "ctx.add($L_DESCRIPTOR, value.$L);\n",
              getter.name().toUpperCase(),
              getter.expression());
        }
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
              .addFields(descriptorFields)
              .addMethod(methodBuilder.build())
              .build();
      encoded.put(types.erasure(type), encoder);
      return VisitResult.of(result, Encoder.create(types.erasure(type), encoder));
    }

    private ClassName builderName(ClassName annotation) {
      return ClassName.get(annotation.packageName(), compositeName(annotation));
    }

    private String compositeName(ClassName annotation) {
      ClassName parentName = annotation.enclosingClassName();
      if (parentName == null) {
        return "At" + annotation.simpleName();
      }
      return compositeName(parentName) + annotation.simpleName();
    }

    private Set<TypeMirror> getTypesToVisit(TypeMirror type) {
      TypeMirror date = elements.getTypeElement("java.util.Date").asType();
      TypeMirror enumType = types.erasure(elements.getTypeElement("java.lang.Enum").asType());
      if (type.getKind().isPrimitive()
          || types.isAssignable(type, date)
          || types.isAssignable(type, enumType)
          || "java.lang".equals(Names.packageName(types.asElement(type)))) {
        return Collections.emptySet();
      }
      if (!(type instanceof DeclaredType)) {
        return Collections.emptySet();
      }

      DeclaredType dType = (DeclaredType) type;

      TypeMirror collection =
          types.erasure(elements.getTypeElement("java.util.Collection").asType());
      TypeMirror map = types.erasure(elements.getTypeElement("java.util.Map").asType());
      if (types.isAssignable(dType, collection) || types.isAssignable(dType, map)) {
        return dType.getTypeArguments().stream()
            .flatMap(t -> getTypesToVisit(t).stream())
            .collect(Collectors.toSet());
      }
      return Collections.singleton(type);
    }
  }
}
