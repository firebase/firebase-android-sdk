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
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

public class FirebaseSymbolProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
) : SymbolProcessor {
  override fun process(resolver: Resolver): List<KSAnnotated> {
    resolver
      .getSymbolsWithAnnotation("com.google.firebase.ai.annotations.Generable")
      .filterIsInstance<KSClassDeclaration>()
      .map { it to SchemaSymbolProcessorVisitor(logger, codeGenerator) }
      .forEach { (klass, visitor) -> visitor.visitClassDeclaration(klass, Unit) }

    resolver
      .getSymbolsWithAnnotation("com.google.firebase.ai.annotations.Tool")
      .filterIsInstance<KSFunctionDeclaration>()
      .map { it to FunctionSymbolProcessorVisitor(it, resolver, logger, codeGenerator) }
      .forEach { (function, visitor) -> visitor.visitFunctionDeclaration(function, Unit) }

    return emptyList()
  }
}
