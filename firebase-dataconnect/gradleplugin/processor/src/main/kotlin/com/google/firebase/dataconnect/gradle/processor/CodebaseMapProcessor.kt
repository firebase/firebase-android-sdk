package com.google.firebase.dataconnect.gradle.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import java.util.concurrent.atomic.AtomicBoolean

class CodebaseMapProcessor(private val codeGenerator: CodeGenerator) : SymbolProcessor {

    private val hasRun = AtomicBoolean(false)

    override fun process(resolver: Resolver): List<KSAnnotated> {
      if (!hasRun.compareAndSet(false, true)) {
        return emptyList()
      }

      val filesToInclude = resolver.getFilesToIncludeInCodebaseMap().toList()
      val classMapInfoList = filesToInclude.flatMap {
        it.toClassMapInfoSequence()
      }

      if (classMapInfoList.isNotEmpty()) {
        val dependencies = Dependencies(aggregating = true, *filesToInclude.toTypedArray())
        val outputStream = codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = "",
            fileName = "codebase-map",
            extensionName = "md"
        )
        writeCodebaseMap(outputStream, classMapInfoList)
      }

      return emptyList()
    }

}
