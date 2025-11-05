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

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.Modifier
import com.google.firebase.ai.annotations.Generable
import com.google.firebase.ai.annotations.Guide
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import javax.annotation.processing.Generated

public class SchemaSymbolProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
) : SymbolProcessor {
  override fun process(resolver: Resolver): List<KSAnnotated> {
    resolver
      .getSymbolsWithAnnotation(Generable::class.qualifiedName.orEmpty())
      .filterIsInstance<KSClassDeclaration>()
      .map { it to SchemaSymbolProcessorVisitor(it, resolver) }
      .forEach { it.second.visitClassDeclaration(it.first, Unit) }

    return emptyList()
  }

  private inner class SchemaSymbolProcessorVisitor(
    private val klass: KSClassDeclaration,
    private val resolver: Resolver,
  ) : KSVisitorVoid() {
    private val numberTypes = setOf("kotlin.Int", "kotlin.Long", "kotlin.Double", "kotlin.Float")
    private val baseKdocRegex = Regex("^\\s*(.*?)((@\\w* .*)|\\z)", RegexOption.DOT_MATCHES_ALL)
    private val propertyKdocRegex =
      Regex("\\s*@property (\\w*) (.*?)(?=@\\w*|\\z)", RegexOption.DOT_MATCHES_ALL)

    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
      val isDataClass = classDeclaration.modifiers.contains(Modifier.DATA)
      if (!isDataClass) {
        logger.error("${classDeclaration.qualifiedName} is not a data class")
      }
      val generatedSchemaFile = generateFileSpec(classDeclaration)
      generatedSchemaFile.writeTo(
        codeGenerator,
        Dependencies(true, classDeclaration.containingFile!!),
      )
    }

    fun generateFileSpec(classDeclaration: KSClassDeclaration): FileSpec {
      return FileSpec.builder(
          classDeclaration.packageName.asString(),
          "${classDeclaration.simpleName.asString()}GeneratedSchema",
        )
        .addImport("com.google.firebase.ai.type", "Schema")
        .addType(
          TypeSpec.classBuilder("${classDeclaration.simpleName.asString()}GeneratedSchema")
            .addAnnotation(Generated::class)
            .addType(
              TypeSpec.companionObjectBuilder()
                .addProperty(
                  PropertySpec.builder(
                      "SCHEMA",
                      ClassName("com.google.firebase.ai.type", "Schema"),
                      KModifier.PUBLIC,
                    )
                    .mutable(false)
                    .initializer(
                      CodeBlock.builder()
                        .add(
                          generateCodeBlockForSchema(type = classDeclaration.asType(emptyList()))
                        )
                        .build()
                    )
                    .build()
                )
                .build()
            )
            .build()
        )
        .build()
    }

    @OptIn(KspExperimental::class)
    fun generateCodeBlockForSchema(
      name: String? = null,
      description: String? = null,
      type: KSType,
      parentType: KSType? = null,
      guideAnnotation: KSAnnotation? = null,
    ): CodeBlock {
      val parameterizedName = type.toTypeName() as? ParameterizedTypeName
      val className = parameterizedName?.rawType ?: type.toClassName()
      val kdocString = type.declaration.docString ?: ""
      val baseKdoc = extractBaseKdoc(kdocString)
      val propertyDocs = extractPropertyKdocs(kdocString)
      val guideClassAnnotation =
        type.annotations.firstOrNull() {
          it.shortName.getShortName() == Guide::class.java.simpleName
        }
      val description =
        getDescriptionFromAnnotations(guideAnnotation, guideClassAnnotation, description, baseKdoc)
      val minimum = getDoubleFromAnnotation(guideAnnotation, "minimum")
      val maximum = getDoubleFromAnnotation(guideAnnotation, "maximum")
      val minItems = getIntFromAnnotation(guideAnnotation, "minItems")
      val maxItems = getIntFromAnnotation(guideAnnotation, "maxItems")
      val format = getStringFromAnnotation(guideAnnotation, "format")
      val builder = CodeBlock.builder()
      when (className.canonicalName) {
        "kotlin.Int" -> {
          builder.addStatement("Schema.integer(")
        }
        "kotlin.Long" -> {
          builder.addStatement("Schema.long(")
        }
        "kotlin.Boolean" -> {
          builder.addStatement("Schema.boolean(")
        }
        "kotlin.Float" -> {
          builder.addStatement("Schema.float(")
        }
        "kotlin.Double" -> {
          builder.addStatement("Schema.double(")
        }
        "kotlin.String" -> {
          builder.addStatement("Schema.string(")
        }
        else -> {
          if (className.canonicalName == "kotlin.collections.List") {
            val listTypeParam = type.arguments.first().type!!.resolve()
            val listParamCodeBlock =
              generateCodeBlockForSchema(type = listTypeParam, parentType = type)
            builder.addStatement("Schema.array(items = ").add(listParamCodeBlock).addStatement(",")
          } else {
            builder.addStatement("Schema.obj(properties = ")
            val properties =
              (type.declaration as KSClassDeclaration).getAllProperties().associate { property ->
                val propertyName = property.simpleName.asString()
                propertyName to
                  generateCodeBlockForSchema(
                    type = property.type.resolve(),
                    parentType = type,
                    description = propertyDocs[propertyName],
                    name = propertyName,
                    guideAnnotation =
                      property.annotations.firstOrNull() {
                        it.shortName.getShortName() == Guide::class.java.simpleName
                      },
                  )
              }
            builder.addStatement("mapOf(")
            properties.entries.forEach {
              builder.addStatement("%S to ", it.key).add(it.value).addStatement(", ")
            }
            builder.addStatement("),")
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
          "${parentType?.toClassName()?.simpleName?.let { "$it." }}$name is not a number type, minimum and maximum are not valid parameters to specify in @Guide"
        )
      }
      if (
        (minItems != null || maxItems != null) &&
          className.canonicalName != "kotlin.collections.List"
      ) {
        logger.warn(
          "${parentType?.toClassName()?.simpleName?.let { "$it." }}$name is not a List type, minItems and maxItems are not valid parameters to specify in @Guide"
        )
      }
      if (format != null && className.canonicalName != "kotlin.String") {
        logger.warn(
          "${parentType?.toClassName()?.simpleName?.let { "$it." }}$name is not a String type, format is not a valid parameter to specify in @Guide"
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
      builder.addStatement("nullable = %L)", className.isNullable)
      return builder.build()
    }

    private fun getDescriptionFromAnnotations(
      guideAnnotation: KSAnnotation?,
      guideClassAnnotation: KSAnnotation?,
      description: String?,
      baseKdoc: String?,
    ): String? {
      val guidePropertyDescription = getStringFromAnnotation(guideAnnotation, "description")

      val guideClassDescription = getStringFromAnnotation(guideClassAnnotation, "description")

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
}
