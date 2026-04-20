package com.google.firebase.dataconnect.gradle.processor

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Visibility

fun Resolver.getFilesToIncludeInCodebaseMap(): Sequence<KSFile> = getAllFiles().filter { it.origin == Origin.KOTLIN }

fun KSFile.toClassMapInfoSequence(): Sequence<ClassMapInfo> =
    declarations
      .filter(::isCodebaseMapDeclaration)
      .mapNotNull { it.toClassMapInfo() }

private fun isCodebaseMapDeclaration(declaration: KSDeclaration): Boolean {
  if (declaration.getVisibility() == Visibility.PRIVATE) {
    return false
  }

  return when (declaration) {
    is KSClassDeclaration -> !declaration.isCompanionObject
    is KSTypeAlias -> true
    is KSFunctionDeclaration -> true
    is KSPropertyDeclaration -> true
    else -> false
  }
}

private fun KSDeclaration.toClassMapInfo(): ClassMapInfo? {
  val filePath = containingFile?.filePath ?: return null
  val packageName = packageName.asString()

  val classParts = when (this) {
      is KSFunctionDeclaration -> {
          val receiverStr = extensionReceiver?.toString()?.let { "$it." } ?: ""
          val funcName = simpleName.asString()
          listOf("${receiverStr}${funcName}")
      }
      is KSPropertyDeclaration -> {
          val receiverStr = extensionReceiver?.toString()?.let { "$it." } ?: ""
          val propName = simpleName.asString()
          listOf("${receiverStr}${propName}")
      }
      else -> {
          val qualifiedName = qualifiedName?.asString() ?: return null
          val className = if (packageName.isEmpty()) {
              qualifiedName
          } else {
              qualifiedName.removePrefix("$packageName.")
          }
          className.split(".")
      }
  }

  val relativePath = filePath.substringAfter("firebase-dataconnect/")
  val directory = if (relativePath.contains("/")) relativePath.substringBeforeLast("/") + "/" else ""
  val fileName = relativePath.substringAfterLast("/")

  return ClassMapInfo(
    packageName=packageName,
    directory=directory,
    fileName=fileName,
    classParts=classParts,
  )
}