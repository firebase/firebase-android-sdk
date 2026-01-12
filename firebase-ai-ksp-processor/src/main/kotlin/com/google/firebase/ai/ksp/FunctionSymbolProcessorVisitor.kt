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

import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import java.util.Locale
import javax.annotation.processing.Generated

internal class FunctionSymbolProcessorVisitor(
  private val func: KSFunctionDeclaration,
  private val resolver: Resolver,
  private val logger: KSPLogger,
  private val codeGenerator: CodeGenerator
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
        returnType.resolve().toClassName().canonicalName == "kotlinx.serialization.json.JsonObject")
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
