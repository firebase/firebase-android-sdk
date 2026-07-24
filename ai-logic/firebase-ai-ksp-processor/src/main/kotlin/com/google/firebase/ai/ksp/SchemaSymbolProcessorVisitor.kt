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
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
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

internal class SchemaSymbolProcessorVisitor(
  private val logger: KSPLogger,
  private val codeGenerator: CodeGenerator
) : KSVisitorVoid() {
  private val numberTypes = setOf("kotlin.Int", "kotlin.Long", "kotlin.Double", "kotlin.Float")

  override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
    val isDataClass = classDeclaration.modifiers.contains(Modifier.DATA)
    if (!isDataClass) {
      logger.error("${classDeclaration.qualifiedName} is not a data class")
    }
    val containingFile = classDeclaration.containingFile
    if (containingFile == null) {
      logger.warn("${classDeclaration.qualifiedName} must be in a file in the build, skipping")
      return
    }
    val generatedSchemaFile = generateFileSpec(classDeclaration)
    generatedSchemaFile.writeTo(
      codeGenerator,
      Dependencies(true, containingFile),
    )
    val companionFile = generateMlKitCompanionFileSpec(classDeclaration)
    companionFile.writeTo(
      codeGenerator,
      Dependencies(true, containingFile),
    )
  }

  fun generateFileSpec(classDeclaration: KSClassDeclaration): FileSpec {
    return FileSpec.builder(
        classDeclaration.packageName.asString(),
        "${classDeclaration.simpleName.asString()}GeneratedSchema",
      )
      .addImport("com.google.firebase.ai.type", "JsonSchema")
      .addFunction(
        FunSpec.builder("firebaseAISchema")
          .receiver(
            ClassName(
              classDeclaration.packageName.asString(),
              classDeclaration.simpleName.asString() + ".Companion"
            )
          )
          .returns(
            ClassName("com.google.firebase.ai.type", "JsonSchema")
              .parameterizedBy(
                ClassName(
                  classDeclaration.packageName.asString(),
                  classDeclaration.simpleName.asString()
                )
              )
          )
          .addAnnotation(Generated::class)
          .addCode(
            CodeBlock.builder()
              .add("return ")
              .add(generateCodeBlockForSchema(type = classDeclaration.asType(emptyList())))
              .build()
          )
          .build()
      )
      .build()
  }

  fun generateCodeBlockForSchema(
    type: KSType,
    description: String? = null,
    name: String? = null,
    parentType: KSType? = null,
    guideAnnotation: KSAnnotation? = null,
  ): CodeBlock {
    val parameterizedName = type.toTypeName() as? ParameterizedTypeName
    val className = parameterizedName?.rawType ?: type.toClassName()
    val qualifiedName = type.declaration.qualifiedName
    if (qualifiedName == null) {
      logger.error("$type has no qualified name.")
      throw RuntimeException()
    }
    val kdocString = type.declaration.docString ?: ""
    val baseKdoc = extractBaseKdoc(kdocString)
    val propertyDocs = extractPropertyKdocs(kdocString)
    val generableClassAnnotation =
      type.annotations.firstOrNull { it.shortName.getShortName() == "Generable" }

    val guideValues =
      getGuideValuesFromAnnotation(
        guideAnnotation,
        getDescriptionFromAnnotations(
          guideAnnotation,
          generableClassAnnotation,
          description,
          baseKdoc
        )
      )
    val builder = CodeBlock.builder()
    when (className.canonicalName) {
      "kotlin.Int" -> {
        builder.addStatement("JsonSchema.integer(").indent()
      }
      "kotlin.Long" -> {
        builder.addStatement("JsonSchema.long(").indent()
      }
      "kotlin.Boolean" -> {
        builder.addStatement("JsonSchema.boolean(").indent()
      }
      "kotlin.Float" -> {
        builder.addStatement("JsonSchema.float(").indent()
      }
      "kotlin.Double" -> {
        builder.addStatement("JsonSchema.double(").indent()
      }
      "kotlin.String" -> {
        builder.addStatement("JsonSchema.string(").indent()
      }
      "kotlin.collections.List" -> {

        val listTypeParam = type.arguments.first().type
        if (listTypeParam == null) {
          logger.error(
            "${parentType?.toClassName()?.simpleName?.let { "$it." }}$name must be a" +
              " parameterized list type."
          )
          throw RuntimeException()
        }
        val listParamCodeBlock =
          generateCodeBlockForSchema(type = listTypeParam.resolve(), parentType = type)
        builder
          .addStatement("JsonSchema.array(")
          .indent()
          .addStatement("items = ")
          .add(listParamCodeBlock)
          .addStatement(",")
      }
      else -> {
        if ((type.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS) {
          val enumValues =
            (type.declaration as KSClassDeclaration)
              .declarations
              .filterIsInstance(KSClassDeclaration::class.java)
              .map { it.simpleName.asString() }
              .toList()
          builder
            .addStatement("JsonSchema.enumeration(")
            .indent()
            .addStatement("clazz = ${qualifiedName.asString()}::class,")
            .addStatement("values = listOf(")
            .indent()
            .addStatement(enumValues.joinToString { "\"$it\"" })
            .unindent()
            .addStatement("),")
        } else {
          builder
            .addStatement("JsonSchema.obj(")
            .indent()
            .addStatement("clazz = ${qualifiedName.asString()}::class,")
            .addStatement("properties = mapOf(")
            .indent()
          val properties =
            (type.declaration as KSClassDeclaration).getAllProperties().associate { property ->
              val propertyName = property.simpleName.asString()
              propertyName to
                generateCodeBlockForSchema(
                  type = property.type.resolve(),
                  description = propertyDocs[propertyName],
                  name = propertyName,
                  parentType = type,
                  guideAnnotation =
                    property.annotations.firstOrNull { it.shortName.getShortName() == "Guide" },
                )
            }
          properties.entries.forEach {
            builder
              .addStatement("%S to ", it.key)
              .indent()
              .add(it.value)
              .unindent()
              .addStatement(", ")
          }
          builder.unindent().addStatement("),")
        }
      }
    }
    name?.let { builder.addStatement("title = %S,", it) }
    guideValues.description?.let { builder.addStatement("description = %S,", it) }

    if (
      (guideValues.minimum != null || guideValues.maximum != null) &&
        !numberTypes.contains(className.canonicalName)
    ) {
      logger.warn(
        "${parentType?.toClassName()?.simpleName?.let { "$it." }}$name is not a number type, " +
          "minimum and maximum are not valid parameters to specify in @Guide"
      )
    }
    if (
      (guideValues.minItems != null || guideValues.maxItems != null) &&
        className.canonicalName != "kotlin.collections.List"
    ) {
      logger.warn(
        "${parentType?.toClassName()?.simpleName?.let { "$it." }}$name is not a List type, " +
          "minItems and maxItems are not valid parameters to specify in @Guide"
      )
    }
    if ((guideValues.format != null) && className.canonicalName != "kotlin.String") {
      logger.warn(
        "${parentType?.toClassName()?.simpleName?.let { "$it." }}$name is not a String type, " +
          "format is not a valid parameter to specify in @Guide"
      )
    }
    guideValues.minimum?.let { builder.addStatement("minimum = %L,", it) }
    guideValues.maximum?.let { builder.addStatement("maximum = %L,", it) }
    guideValues.minItems?.let { builder.addStatement("minItems = %L,", it) }

    guideValues.maxItems?.let { builder.addStatement("maxItems = %L,", it) }
    guideValues.format?.let { builder.addStatement("format = %S,", it) }
    builder.addStatement("nullable = %L)", className.isNullable).unindent()
    return builder.build()
  }

  private fun isGenerableClass(type: KSType): Boolean {
    return type.declaration.annotations.any { it.shortName.getShortName() == "Generable" } ||
      (type.declaration as? KSClassDeclaration)?.modifiers?.contains(Modifier.DATA) == true &&
        !type.declaration.qualifiedName?.asString()!!.startsWith("kotlin.")
  }

  private fun isListOfGenerableClass(type: KSType): Boolean {
    val qualifiedName = type.declaration.qualifiedName?.asString()
    if (qualifiedName == "kotlin.collections.List" || qualifiedName == "java.util.List") {
      val argType = type.arguments.firstOrNull()?.type?.resolve()
      if (argType != null) {
        return isGenerableClass(argType)
      }
    }
    return false
  }

  private fun mapToMlKitCompanionType(type: KSType, packageName: String): TypeName {
    if (isListOfGenerableClass(type)) {
      val argType = type.arguments.first()!!.type!!.resolve()
      val argClassName =
        ClassName(
          argType.declaration.packageName.asString(),
          "${argType.declaration.simpleName.asString()}_MlKitCompanion"
        )
      return ClassName("kotlin.collections", "List").parameterizedBy(argClassName)
    } else if (isGenerableClass(type)) {
      return ClassName(
        type.declaration.packageName.asString(),
        "${type.declaration.simpleName.asString()}_MlKitCompanion"
      )
    }
    return type.toTypeName()
  }

  fun generateMlKitCompanionFileSpec(classDeclaration: KSClassDeclaration): FileSpec {
    val packageName = classDeclaration.packageName.asString()
    val companionClassName = "${classDeclaration.simpleName.asString()}_MlKitCompanion"
    val fileBuilder =
      FileSpec.builder(packageName, companionClassName).addAnnotation(Generated::class)

    val classBuilder = TypeSpec.classBuilder(companionClassName).addModifiers(KModifier.DATA)
    val keepAnnotation = AnnotationSpec.builder(ClassName("androidx.annotation", "Keep")).build()
    classBuilder.addAnnotation(keepAnnotation)

    val generableAnn =
      classDeclaration.annotations.firstOrNull { it.shortName.getShortName() == "Generable" }
    val classDesc = getStringFromAnnotation(generableAnn, "description")
    val mlkitGenerableBuilder =
      AnnotationSpec.builder(
        ClassName("com.google.mlkit.genai.structuredoutput.annotations", "Generable")
      )
    if (!classDesc.isNullOrEmpty()) {
      mlkitGenerableBuilder.addMember("description = %S", classDesc)
    }
    classBuilder.addAnnotation(mlkitGenerableBuilder.build())

    val primaryConstructor = FunSpec.constructorBuilder()
    val toSdkBuilder =
      FunSpec.builder("toSdk")
        .addAnnotation(keepAnnotation)
        .returns(ClassName(packageName, classDeclaration.simpleName.asString()))
    val toSdkArgs = mutableListOf<String>()

    classDeclaration.getAllProperties().forEach { property ->
      val propName = property.simpleName.asString()
      val propType = property.type.resolve()
      val typeName = mapToMlKitCompanionType(propType, packageName)

      val paramBuilder = ParameterSpec.builder(propName, typeName)
      val propBuilder = PropertySpec.builder(propName, typeName).initializer(propName)

      val guideAnn = property.annotations.firstOrNull { it.shortName.getShortName() == "Guide" }
      if (guideAnn != null) {
        val guideValues =
          getGuideValuesFromAnnotation(guideAnn, getStringFromAnnotation(guideAnn, "description"))
        val mlkitGuideBuilder =
          AnnotationSpec.builder(
            ClassName("com.google.mlkit.genai.structuredoutput.annotations", "Guide")
          )
        if (!guideValues.description.isNullOrEmpty())
          mlkitGuideBuilder.addMember("description = %S", guideValues.description)
        if (guideValues.minimum != null)
          mlkitGuideBuilder.addMember("minimum = %L", guideValues.minimum)
        if (guideValues.maximum != null)
          mlkitGuideBuilder.addMember("maximum = %L", guideValues.maximum)
        if (guideValues.minItems != null)
          mlkitGuideBuilder.addMember("minItems = %L", guideValues.minItems)
        if (guideValues.maxItems != null)
          mlkitGuideBuilder.addMember("maxItems = %L", guideValues.maxItems)
        if (!guideValues.format.isNullOrEmpty())
          mlkitGuideBuilder.addMember("format = %S", guideValues.format)
        paramBuilder.addAnnotation(mlkitGuideBuilder.build())
      }

      primaryConstructor.addParameter(paramBuilder.build())
      classBuilder.addProperty(propBuilder.build())

      if (isListOfGenerableClass(propType)) {
        toSdkArgs.add("$propName = this.$propName.map { it.toSdk() }")
      } else if (isGenerableClass(propType)) {
        toSdkArgs.add("$propName = this.$propName.toSdk()")
      } else {
        toSdkArgs.add("$propName = this.$propName")
      }
    }

    classBuilder.primaryConstructor(primaryConstructor.build())
    toSdkBuilder.addStatement(
      "return %T(\n  ${toSdkArgs.joinToString(",\n  ")}\n)",
      ClassName(packageName, classDeclaration.simpleName.asString())
    )
    classBuilder.addFunction(toSdkBuilder.build())

    fileBuilder.addType(classBuilder.build())
    return fileBuilder.build()
  }
}
