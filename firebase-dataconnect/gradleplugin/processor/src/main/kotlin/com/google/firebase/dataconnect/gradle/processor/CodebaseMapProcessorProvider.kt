package com.google.firebase.dataconnect.gradle.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import java.io.File

class CodebaseMapProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val outputFile = environment.options["codebaseMapOutputFile"]
            ?: throw IllegalArgumentException("Missing codebaseMapOutputFile KSP option.")
        return CodebaseMapProcessor(File(outputFile))
    }
}
