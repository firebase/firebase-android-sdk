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

package com.google.firebase.encoders.processor;

import com.google.auto.service.AutoService;
import com.google.firebase.encoders.annotations.ExtraProperty;
import com.google.firebase.encoders.processor.annotations.AnnotationBuilder;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

@AutoService(Processor.class)
@SupportedAnnotationTypes("com.google.firebase.encoders.annotations.ExtraProperty")
public class ExtraPropertyProcessor extends AbstractProcessor {
  private Filer filer;
  private Messager messager;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);
    filer = processingEnvironment.getFiler();
    messager = processingEnvironment.getMessager();
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(ExtraProperty.class)) {
      processAnnotation((TypeElement) element);
    }
    return false;
  }

  private void processAnnotation(TypeElement element) {
    if (!validateAnnotation(element)) {
      return;
    }
    TypeSpec builder = AnnotationBuilder.generate(element);

    try {
      JavaFile.builder(ClassName.get(element).packageName(), builder).build().writeTo(filer);
    } catch (IOException e) {
      throw new RuntimeException("Unable to save class file " + builder.name);
    }
  }

  private boolean validateAnnotation(TypeElement element) {
    if (!ElementKind.ANNOTATION_TYPE.equals(element.getKind())) {
      messager.printMessage(
          Kind.ERROR, String.format("Element %s is not an annotation type.", element), element);
      return false;
    }
    return true;
  }
}
