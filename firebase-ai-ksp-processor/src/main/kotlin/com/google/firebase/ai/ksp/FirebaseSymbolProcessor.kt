/*
 * Copyright 2025 Google LLC
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

import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import java.util.Locale
import javax.annotation.processing.Generated

public class FirebaseSymbolProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
) : SymbolProcessor {
  // This regex extracts everything in the kdocs until it hits either the end of the kdocs, or
  // the first @<tag> like @property or @see, extracting the main body text of the kdoc
  private val baseKdocRegex = Regex("""^\s*(.*?)((@\w* .*)|\z)""", RegexOption.DOT_MATCHES_ALL)
  // This regex extracts two capture groups from @property tags, the first is the name of the
  // property, and the second is the documentation associated with that property
  private val propertyKdocRegex =
    Regex("""\s*@property (\w*) (.*?)(?=@\w*|\z)""", RegexOption.DOT_MATCHES_ALL)

  override fun process(resolver: Resolver): List<KSAnnotated> {
    resolver
      .getSymbolsWithAnnotation("com.google.firebase.ai.annotations.Generable")
      .filterIsInstance<KSClassDeclaration>()
      .map { it to SchemaSymbolProcessorVisitor() }
      .forEach { (klass, visitor) -> visitor.visitClassDeclaration(klass, Unit) }

    resolver
      .getSymbolsWithAnnotation("com.google.firebase.ai.annotations.Tool")
      .filterIsInstance<KSFunctionDeclaration>()
      .map { it to FunctionSymbolProcessorVisitor(it, resolver) }
      .forEach { (function, visitor) -> visitor.visitFunctionDeclaration(function, Unit) }

    return emptyList()
  }

  private inner class FunctionSymbolProcessorVisitor(
    private val func: KSFunctionDeclaration,
    private val resolver: Resolver,
  ) : KSVisitorVoid() {
    override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
      var shouldError = false
      val fullFunctionName = function.qualifiedName?.asString()
      if (fullFunctionName == null) {
        logger.warn("Error extracting function name.")
        shouldError = true
      }
      if (!function.isPublic()) {
        logger.warn("$fullFunctionName must be public.")
        shouldError = true
      }
      val containingClass = function.parentDeclaration as? KSClassDeclaration
      val containingClassQualifiedName = containingClass?.qualifiedName?.asString()
      if (containingClassQualifiedName == null) {
        logger.warn("Could not extract name of containing class of $fullFunctionName.")
        shouldError = true
      }
      if (containingClass == null || !containingClass.isCompanionObject) {
        logger.warn(
          "$fullFunctionName must be within a companion object $containingClassQualifiedName."
        )
        shouldError = true
      }
      if (function.parameters.size != 1) {
        logger.warn("$fullFunctionName must have exactly one parameter")
        shouldError = true
      }
      val parameter = function.parameters.firstOrNull()?.type?.resolve()?.declaration
      if (parameter != null) {
        if (parameter.annotations.find { it.shortName.getShortName() == "Generable" } == null) {
          logger.warn("$fullFunctionName parameter must be annotated @Generable")
          shouldError = true
        }
        if (parameter.annotations.find { it.shortName.getShortName() == "Serializable" } == null) {
          logger.warn("$fullFunctionName parameter must be annotated @Serializable")
          shouldError = true
        }
      }
      val output = function.returnType?.resolve()
      if (
        output != null &&
          output.toClassName().canonicalName != "kotlinx.serialization.json.JsonObject"
      ) {
        if (
          output.declaration.annotations.find { it.shortName.getShortName() != "Generable" } == null
        ) {
          logger.warn("$fullFunctionName output must be annotated @Generable")
          shouldError = true
        }
        if (
          output.declaration.annotations.find { it.shortName.getShortName() != "Serializable" } ==
            null
        ) {
          logger.warn("$fullFunctionName output must be annotated @Serializable")
          shouldError = true
        }
      }
      if (shouldError) {
        logger.error("$fullFunctionName has one or more errors, please resolve them.")
      }
      val generatedFunctionFile = generateFileSpec(function)
      val containingFile = function.containingFile
      if (containingFile == null) {
        logger.error("$fullFunctionName must be in a file in the build.")
        throw RuntimeException()
      }
      generatedFunctionFile.writeTo(
        codeGenerator,
        Dependencies(true, containingFile),
      )
    }

    private fun generateFileSpec(functionDeclaration: KSFunctionDeclaration): FileSpec {
      val functionFileName =
        functionDeclaration.simpleName.asString().replaceFirstChar {
          if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        } + "FirebaseAISchema"
      val functionName = functionFileName.replaceFirstChar { it.lowercaseChar() }
      val containingClassType =
        ClassName(
          functionDeclaration.packageName.asString(),
          functionDeclaration.parentDeclaration!!.parentDeclaration!!.simpleName.asString() +
            ".Companion"
        )
      return FileSpec.builder(functionDeclaration.packageName.asString(), functionFileName)
        .addImport("com.google.firebase.ai.type", "AutoFunctionDeclaration")
        .addFunction(
          FunSpec.builder(functionName)
            .addAnnotation(Generated::class)
            .receiver(containingClassType)
            .returns(
              ClassName("com.google.firebase.ai.type", "AutoFunctionDeclaration")
                .parameterizedBy(
                  functionDeclaration.parameters.first().type.resolve().toClassName(),
                  functionDeclaration.returnType?.resolve()?.toClassName()
                    ?: ClassName("kotlinx.serialization.json", "JsonObject")
                )
            )
            .addCode(
              CodeBlock.builder()
                .add("return ")
                .add(generateCodeBlockForFunctionDeclaration(functionDeclaration))
                .build()
            )
            .build()
        )
        .build()
    }

    fun generateCodeBlockForFunctionDeclaration(
      functionDeclaration: KSFunctionDeclaration
    ): CodeBlock {
      val builder = CodeBlock.builder()
      val returnType = functionDeclaration.returnType
      val hasTypedOutput =
        !(returnType == null ||
          returnType.resolve().toClassName().canonicalName ==
            "kotlinx.serialization.json.JsonObject")
      val kdocDescription = functionDeclaration.docString?.let { extractBaseKdoc(it) }
      val annotationDescription =
        getStringFromAnnotation(
          functionDeclaration.annotations.find { it.shortName.getShortName() == "Tool" },
          "description"
        )
      val description = annotationDescription ?: kdocDescription ?: ""
      val inputSchemaName =
        "${
          functionDeclaration.parameters.first().type.resolve().toClassName().canonicalName
        }.firebaseAISchema()"
      builder
        .addStatement("AutoFunctionDeclaration.create(")
        .indent()
        .addStatement("functionName = %S,", functionDeclaration.simpleName.getShortName())
        .addStatement("description = %S,", description)
        .addStatement("inputSchema = $inputSchemaName,")
      if (hasTypedOutput) {
        val outputSchemaName =
          "${
            functionDeclaration.returnType!!.resolve().toClassName().canonicalName
          }.firebaseAISchema()"
        builder.addStatement("outputSchema = $outputSchemaName,")
      }
      builder.addStatement(
        "functionReference = " +
          functionDeclaration.qualifiedName!!.getQualifier() +
          "::${functionDeclaration.qualifiedName!!.getShortName()},"
      )
      builder.unindent().addStatement(")")
      return builder.build()
    }
  }

  private inner class SchemaSymbolProcessorVisitor() : KSVisitorVoid() {
    private val numberTypes = setOf("kotlin.Int", "kotlin.Long", "kotlin.Double", "kotlin.Float")

    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
      val isDataClass = classDeclaration.modifiers.contains(Modifier.DATA)
      if (!isDataClass) {
        logger.error("${classDeclaration.qualifiedName} is not a data class")
      }
      val containingFile = classDeclaration.containingFile
      if (containingFile == null) {
        logger.error("${classDeclaration.qualifiedName} must be in a file in the build")
        throw RuntimeException()
      }
      val generatedSchemaFile = generateFileSpec(classDeclaration)
      generatedSchemaFile.writeTo(
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
        type.annotations.firstOrNull() { it.shortName.getShortName() == "Generable" }
      val description =
        getDescriptionFromAnnotations(
          guideAnnotation,
          generableClassAnnotation,
          description,
          baseKdoc
        )
      val minimum = getDoubleFromAnnotation(guideAnnotation, "minimum")
      val maximum = getDoubleFromAnnotation(guideAnnotation, "maximum")
      val minItems = getIntFromAnnotation(guideAnnotation, "minItems")
      val maxItems = getIntFromAnnotation(guideAnnotation, "maxItems")
      val format = getStringFromAnnotation(guideAnnotation, "format")
      val pattern = getStringFromAnnotation(guideAnnotation, "pattern")
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
                      property.annotations.firstOrNull() { it.shortName.getShortName() == "Guide" },
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
      if (name != null) {
        builder.addStatement("title = %S,", name)
      }
      if (description != null) {
        builder.addStatement("description = %S,", description)
      }
      if ((minimum != null || maximum != null) && !numberTypes.contains(className.canonicalName)) {
        logger.warn(
          "${parentType?.toClassName()?.simpleName?.let { "$it." }}$name is not a number type, " +
            "minimum and maximum are not valid parameters to specify in @Guide"
        )
      }
      if (
        (minItems != null || maxItems != null) &&
          className.canonicalName != "kotlin.collections.List"
      ) {
        logger.warn(
          "${parentType?.toClassName()?.simpleName?.let { "$it." }}$name is not a List type, " +
            "minItems and maxItems are not valid parameters to specify in @Guide"
        )
      }
      if ((format != null || pattern != null) && className.canonicalName != "kotlin.String") {
        logger.warn(
          "${parentType?.toClassName()?.simpleName?.let { "$it." }}$name is not a String type, " +
            "format and pattern are not a valid parameter to specify in @Guide"
        )
      }
      if (minimum != null) {
        builder.addStatement("minimum = %L,", minimum)
      }
      if (maximum != null) {
        builder.addStatement("maximum = %L,", maximum)
      }
      if (minItems != null) {
        builder.addStatement("minItems = %L,", minItems)
      }
      if (maxItems != null) {
        builder.addStatement("maxItems = %L,", maxItems)
      }
      if (format != null) {
        builder.addStatement("format = %S,", format)
      }
      if (pattern != null) {
        builder.addStatement("pattern = %S,", pattern)
      }
      builder.addStatement("nullable = %L)", className.isNullable).unindent()
      return builder.build()
    }
  }

  private fun getDescriptionFromAnnotations(
    guideAnnotation: KSAnnotation?,
    generableClassAnnotation: KSAnnotation?,
    description: String?,
    baseKdoc: String?,
  ): String? {
    val guidePropertyDescription = getStringFromAnnotation(guideAnnotation, "description")

    val guideClassDescription = getStringFromAnnotation(generableClassAnnotation, "description")

    return guidePropertyDescription ?: guideClassDescription ?: description ?: baseKdoc
  }
  private fun getDoubleFromAnnotation(
    guideAnnotation: KSAnnotation?,
    doubleName: String,
  ): Double? {
    val guidePropertyDoubleValue =
      guideAnnotation
        ?.arguments
        ?.firstOrNull { it.name?.getShortName()?.equals(doubleName) == true }
        ?.value as? Double
    if (guidePropertyDoubleValue == null || guidePropertyDoubleValue == -1.0) {
      return null
    }
    return guidePropertyDoubleValue
  }

  private fun getIntFromAnnotation(guideAnnotation: KSAnnotation?, intName: String): Int? {
    val guidePropertyIntValue =
      guideAnnotation
        ?.arguments
        ?.firstOrNull { it.name?.getShortName()?.equals(intName) == true }
        ?.value as? Int
    if (guidePropertyIntValue == null || guidePropertyIntValue == -1) {
      return null
    }
    return guidePropertyIntValue
  }

  private fun getStringFromAnnotation(
    guideAnnotation: KSAnnotation?,
    stringName: String,
  ): String? {
    val guidePropertyStringValue =
      guideAnnotation
        ?.arguments
        ?.firstOrNull { it.name?.getShortName()?.equals(stringName) == true }
        ?.value as? String
    if (guidePropertyStringValue.isNullOrEmpty()) {
      return null
    }
    return guidePropertyStringValue
  }

  private fun extractBaseKdoc(kdoc: String): String? {
    return baseKdocRegex.matchEntire(kdoc)?.groups?.get(1)?.value?.trim().let {
      if (it.isNullOrEmpty()) null else it
    }
  }

  private fun extractPropertyKdocs(kdoc: String): Map<String, String> {
    return propertyKdocRegex
      .findAll(kdoc)
      .map { it.groups[1]!!.value to it.groups[2]!!.value.replace("\n", "").trim() }
      .toMap()
  }
}
