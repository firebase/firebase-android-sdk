/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.ai.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import javax.annotation.processing.Generated

internal class MlKitClassSymbolProcessorVisitor(
  private val logger: KSPLogger,
  private val codeGenerator: CodeGenerator
) : KSVisitorVoid() {

  private val visitedClasses = mutableSetOf<String>()

  override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
    val qualifiedName = classDeclaration.qualifiedName?.asString() ?: return
    if (visitedClasses.contains(qualifiedName)) {
      return
    }
    visitedClasses.add(qualifiedName)

    val isDataClass = classDeclaration.modifiers.contains(Modifier.DATA)
    if (!isDataClass) {
      logger.error("${classDeclaration.qualifiedName} is not a data class")
      return
    }
    val containingFile = classDeclaration.containingFile
    if (containingFile == null) {
      logger.warn("${classDeclaration.qualifiedName} must be in a file in the build, skipping")
      return
    }
    val generatedFile = generateFileSpec(classDeclaration)
    generatedFile.writeTo(
      codeGenerator,
      Dependencies(true, containingFile),
    )
  }

  fun generateFileSpec(classDeclaration: KSClassDeclaration): FileSpec {
    val packageName = classDeclaration.packageName.asString()
    val originalClassName = classDeclaration.simpleName.asString()
    val mlKitClassName = "${originalClassName}MlKit"

    val kdocString = classDeclaration.docString ?: ""
    val baseKdoc = extractBaseKdoc(kdocString)
    val propertyDocs = extractPropertyKdocs(kdocString)
    val generableClassAnnotation =
      classDeclaration.annotations.firstOrNull { it.shortName.getShortName() == "Generable" }
    val classDescription = getStringFromAnnotation(generableClassAnnotation, "description") ?: baseKdoc

    val classBuilder = TypeSpec.classBuilder(mlKitClassName)
      .addModifiers(KModifier.DATA)
      .addAnnotation(Generated::class)

    val isSerializable = classDeclaration.annotations.any {
      it.shortName.getShortName() == "Serializable" || it.shortName.asString() == "Serializable"
    }
    if (isSerializable) {
      classBuilder.addAnnotation(ClassName("kotlinx.serialization", "Serializable"))
    }

    val generableAnnotationBuilder = AnnotationSpec.builder(
      ClassName("com.google.mlkit.genai.schema.annotations", "Generable")
    )
    if (!classDescription.isNullOrEmpty()) {
      generableAnnotationBuilder.addMember("description = %S", classDescription)
    }
    classBuilder.addAnnotation(generableAnnotationBuilder.build())

    val primaryConstructorBuilder = FunSpec.constructorBuilder()

    val parameters = classDeclaration.primaryConstructor?.parameters ?: emptyList()
    parameters.forEach { param ->
      val paramName = param.name?.asString() ?: return@forEach
      val paramType = mapToMlKitType(param.type.resolve())

      primaryConstructorBuilder.addParameter(ParameterSpec.builder(paramName, paramType).build())

      val propertySpecBuilder = PropertySpec.builder(paramName, paramType)
        .initializer(paramName)

      val property = classDeclaration.getAllProperties().firstOrNull { it.simpleName.asString() == paramName }
      val guideAnnotation = property?.annotations?.firstOrNull { it.shortName.getShortName() == "Guide" }
        ?: param.annotations.firstOrNull { it.shortName.getShortName() == "Guide" }
      val description = getStringFromAnnotation(guideAnnotation, "description") ?: propertyDocs[paramName]

      val minItems = getIntFromAnnotation(guideAnnotation, "minItems")
      val maxItems = getIntFromAnnotation(guideAnnotation, "maxItems")
      val minimum = getDoubleFromAnnotation(guideAnnotation, "minimum")
      val maximum = getDoubleFromAnnotation(guideAnnotation, "maximum")
      val enumValues = getStringListFromAnnotation(guideAnnotation, "enumValues")

      val shouldAddGuide = guideAnnotation != null || !description.isNullOrEmpty() ||
        (minItems != null && minItems != -1) ||
        (maxItems != null && maxItems != -1) ||
        (minimum != null && !minimum.isNaN() && minimum != -1.0) ||
        (maximum != null && !maximum.isNaN() && maximum != -1.0) ||
        !enumValues.isNullOrEmpty()

      if (shouldAddGuide) {
        val guideBuilder = AnnotationSpec.builder(
          ClassName("com.google.mlkit.genai.schema.annotations", "Guide")
        )
        if (!description.isNullOrEmpty()) {
          guideBuilder.addMember("description = %S", description)
        }
        if (minItems != null && minItems != -1) {
          guideBuilder.addMember("minItems = %L", minItems)
        }
        if (maxItems != null && maxItems != -1) {
          guideBuilder.addMember("maxItems = %L", maxItems)
        }
        if (minimum != null && !minimum.isNaN() && minimum != -1.0) {
          guideBuilder.addMember("minimum = %L", minimum)
        }
        if (maximum != null && !maximum.isNaN() && maximum != -1.0) {
          guideBuilder.addMember("maximum = %L", maximum)
        }
        if (!enumValues.isNullOrEmpty()) {
          val enumArgsString = enumValues.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
          guideBuilder.addMember("enumValues = %L", enumArgsString)
        }
        propertySpecBuilder.addAnnotation(guideBuilder.build())
      }

      classBuilder.addProperty(propertySpecBuilder.build())
    }

    classBuilder.primaryConstructor(primaryConstructorBuilder.build())

    return FileSpec.builder(packageName, mlKitClassName)
      .addType(classBuilder.build())
      .build()
  }

  private fun mapToMlKitType(type: KSType): TypeName {
    val declaration = type.declaration
    val isGenerable = declaration is KSClassDeclaration &&
      declaration.classKind == ClassKind.CLASS &&
      declaration.annotations.any {
        it.shortName.getShortName() == "Generable" || it.shortName.asString() == "Generable"
      }

    val typeName = type.toTypeName()
    if (typeName is ParameterizedTypeName) {
      if (type.arguments.isNotEmpty()) {
        val mappedArgs = type.arguments.map { arg ->
          val argType = arg.type?.resolve()
          if (argType != null) mapToMlKitType(argType) else arg.toTypeName()
        }
        return typeName.rawType.parameterizedBy(mappedArgs).copy(nullable = type.isMarkedNullable)
      }
    }

    if (isGenerable && declaration is KSClassDeclaration) {
      visitClassDeclaration(declaration, Unit)
      val pkg = declaration.packageName.asString()
      val name = "${declaration.simpleName.asString()}MlKit"
      return ClassName(pkg, name).copy(nullable = type.isMarkedNullable)
    }

    return typeName
  }
}
