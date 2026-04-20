package com.google.firebase.dataconnect.gradle.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class CodebaseMapProcessor(private val outputFile: File) : SymbolProcessor {

    private val hasRun = AtomicBoolean(false)

    override fun process(resolver: Resolver): List<KSAnnotated> {
      if (!hasRun.compareAndSet(false, true)) {
        return emptyList()
      }

      val classMapInfoList = buildList {
        resolver.getFilesToIncludeInCodebaseMap().forEach { file ->
          val classMapInfoSequence = file.toClassMapInfoSequence()
          addAll(classMapInfoSequence)
        }
      }

      if (classMapInfoList.isNotEmpty()) {
        println("zzyzx classMapInfoList.size(): ${classMapInfoList.size}")
        classMapInfoList.forEachIndexed { index, info ->
          println("zzyzx $index: $info")
        }
        writeCodebaseMap(outputFile, classMapInfoList)
      }

      return emptyList()
    }

}
