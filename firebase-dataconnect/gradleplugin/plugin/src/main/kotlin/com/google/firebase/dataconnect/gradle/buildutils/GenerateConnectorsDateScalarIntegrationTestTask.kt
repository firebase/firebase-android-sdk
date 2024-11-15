/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.gradle.buildutils

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@Suppress("unused")
abstract class GenerateConnectorsDateScalarIntegrationTestTask : DefaultTask() {

  @get:InputFile abstract val testSrcFile: RegularFileProperty

  @get:InputFile abstract val testBaseSrcFile: RegularFileProperty

  @get:InputFile abstract val connectorFactorySrcFile: RegularFileProperty

  @get:OutputFile abstract val testDestFile: RegularFileProperty

  @get:OutputFile abstract val testBaseDestFile: RegularFileProperty

  @get:OutputFile abstract val connectorFactoryDestFile: RegularFileProperty

  @get:Input abstract val connectorPackageName: Property<String>

  @get:Input abstract val connectorClassName: Property<String>

  @get:Input abstract val testKotlinPackage: Property<String>

  @get:Input abstract val testClassName: Property<String>

  @get:Input abstract val testBaseKotlinPackage: Property<String>

  @get:Input abstract val testBaseClassName: Property<String>

  @get:Input abstract val connectorFactoryKotlinPackage: Property<String>

  @get:Input abstract val connectorFactoryClassName: Property<String>

  @get:Input abstract val localDateFullyQualifiedClassName: Property<String>

  @get:Input
  @get:Optional
  abstract val convertFromDataConnectLocalDateFunctionName: Property<String>

  @get:Input @get:Optional abstract val localDateFactoryCall: Property<String>

  @TaskAction
  fun run() {
    val testSrcFile: File = testSrcFile.get().asFile
    val testBaseSrcFile: File = testBaseSrcFile.get().asFile
    val connectorFactorySrcFile: File = connectorFactorySrcFile.get().asFile
    val testDestFile: File = testDestFile.get().asFile
    val testBaseDestFile: File = testBaseDestFile.get().asFile
    val connectorFactoryDestFile: File = connectorFactoryDestFile.get().asFile
    val connectorPackageName: String = connectorPackageName.get()
    val connectorClassName: String = connectorClassName.get()
    val testKotlinPackage: String = testKotlinPackage.get()
    val testClassName: String = testClassName.get()
    val testBaseKotlinPackage: String = testBaseKotlinPackage.get()
    val testBaseClassName: String = testBaseClassName.get()
    val connectorFactoryKotlinPackage: String = connectorFactoryKotlinPackage.get()
    val connectorFactoryClassName: String = connectorFactoryClassName.get()
    val localDateFullyQualifiedClassName: String = localDateFullyQualifiedClassName.get()
    val convertFromDataConnectLocalDateFunctionName: String? =
      convertFromDataConnectLocalDateFunctionName.orNull
    val localDateFactoryCall: String? = localDateFactoryCall.orNull

    logger.info("testSrcFile: {}", testSrcFile)
    logger.info("testBaseSrcFile: {}", testBaseSrcFile)
    logger.info("connectorFactorySrcFile: {}", connectorFactorySrcFile)
    logger.info("testDestFile: {}", testDestFile)
    logger.info("testBaseDestFile: {}", testBaseDestFile)
    logger.info("connectorFactoryDestFile: {}", connectorFactoryDestFile)
    logger.info("connectorPackageName: {}", connectorPackageName)
    logger.info("connectorClassName: {}", connectorClassName)
    logger.info("testKotlinPackage: {}", testKotlinPackage)
    logger.info("testClassName: {}", testClassName)
    logger.info("testBaseKotlinPackage: {}", testBaseKotlinPackage)
    logger.info("testBaseClassName: {}", testBaseClassName)
    logger.info("connectorFactoryKotlinPackage: {}", connectorFactoryKotlinPackage)
    logger.info("connectorFactoryClassName: {}", connectorFactoryClassName)
    logger.info("localDateFullyQualifiedClassName: {}", localDateFullyQualifiedClassName)
    logger.info(
      "convertFromDataConnectLocalDateFunctionName: {}",
      convertFromDataConnectLocalDateFunctionName
    )
    logger.info("localDateFactoryCall: {}", localDateFactoryCall)

    TestDestFileGenerator(
        srcFile = testSrcFile,
        destFile = testDestFile,
        kotlinPackage = testKotlinPackage,
        className = testClassName,
        superClassName = testBaseClassName,
        connectorPackageName = connectorPackageName,
        connectorClassName = connectorClassName,
        convertFromDataConnectLocalDateFunctionName = convertFromDataConnectLocalDateFunctionName,
        localDateFullyQualifiedClassName = localDateFullyQualifiedClassName,
        localDateFactoryCall = localDateFactoryCall,
        logger = logger,
      )
      .run()

    TestBaseDestFileGenerator(
        srcFile = testBaseSrcFile,
        destFile = testBaseDestFile,
        kotlinPackage = testBaseKotlinPackage,
        className = testBaseClassName,
        connectorPackageName = connectorPackageName,
        connectorClassName = connectorClassName,
        connectorFactoryClassName = connectorFactoryClassName,
        logger = logger,
      )
      .run()

    ConnectorFactoryDestFileGenerator(
        srcFile = connectorFactorySrcFile,
        destFile = connectorFactoryDestFile,
        kotlinPackage = connectorFactoryKotlinPackage,
        className = connectorFactoryClassName,
        connectorPackageName = connectorPackageName,
        connectorClassName = connectorClassName,
        logger = logger,
      )
      .run()
  }
}

private class TestDestFileGenerator(
  val srcFile: File,
  val destFile: File,
  val kotlinPackage: String,
  val className: String,
  val superClassName: String,
  val connectorPackageName: String,
  val connectorClassName: String,
  val localDateFullyQualifiedClassName: String,
  val convertFromDataConnectLocalDateFunctionName: String?,
  val localDateFactoryCall: String?,
  val logger: Logger,
) {

  fun run() {
    logger.info("Reading from file: {}", srcFile)
    val transformer = TextLinesTransformer(srcFile)

    transformer.run {
      atLineThatStartsWith("package ").replaceWith("package $kotlinPackage")
      removeLine("import com.google.firebase.dataconnect.LocalDate")
      replaceWord("DateScalarIntegrationTest", className)
      replaceWord("DemoConnectorIntegrationTestBase", superClassName)
      replaceWord("com.google.firebase.dataconnect.connectors.demo", connectorPackageName)
      replaceWord("DemoConnector", connectorClassName)
      replaceWord("LocalDate", localDateFullyQualifiedClassName)
      convertFromDataConnectLocalDateFunctionName?.let {
        atLineThatStartsWith("import ")
          .insertAbove("import com.google.firebase.dataconnect.$it")
          .insertAbove("import io.kotest.property.arbitrary.map")
        replaceRegex("""\?\.date(\W|$)""", "?.date?.$it()$1")
        replaceRegex("""([^?])\.date(\W|${'$'})""", "$1.date.$it()$2")
        replaceWord("Arb.dataConnect.localDate()", "Arb.dataConnect.localDate().map{it.$it()}")
      }
      localDateFactoryCall?.let {
        replaceText("$localDateFullyQualifiedClassName(", "$localDateFullyQualifiedClassName$it(")
      }
      insertGeneratedFileWarningLines(srcFile, linePrefix = "package ")
      insertGeneratedFileWarningLines(srcFile, linePrefix = "class ")
    }

    logger.info("Writing to file: {}", destFile)
    transformer.writeLines(destFile)
  }
}

private class TestBaseDestFileGenerator(
  val srcFile: File,
  val destFile: File,
  val kotlinPackage: String,
  val className: String,
  val connectorPackageName: String,
  val connectorClassName: String,
  val connectorFactoryClassName: String,
  val logger: Logger,
) {

  fun run() {
    logger.info("Reading from file: {}", srcFile)
    val transformer = TextLinesTransformer(srcFile)

    transformer.run {
      atLineThatStartsWith("package ").replaceWith("package $kotlinPackage")
      replaceWord("DemoConnectorIntegrationTestBase", className)
      replaceWord("com.google.firebase.dataconnect.connectors.demo", connectorPackageName)
      replaceWord("DemoConnector", connectorClassName)
      replaceWord("TestDemoConnectorFactory", connectorFactoryClassName)
      insertGeneratedFileWarningLines(srcFile, linePrefix = "package ")
      insertGeneratedFileWarningLines(srcFile, linePrefix = "abstract class ")
    }

    logger.info("Writing to file: {}", destFile)
    transformer.writeLines(destFile)
  }
}

private class ConnectorFactoryDestFileGenerator(
  val srcFile: File,
  val destFile: File,
  val kotlinPackage: String,
  val className: String,
  val connectorPackageName: String,
  val connectorClassName: String,
  val logger: Logger,
) {

  fun run() {
    logger.info("Reading from file: {}", srcFile)
    val transformer = TextLinesTransformer(srcFile)

    transformer.run {
      atLineThatStartsWith("package ").replaceWith("package $kotlinPackage")
      replaceWord("TestDemoConnectorFactory", className)
      replaceWord("com.google.firebase.dataconnect.connectors.demo", connectorPackageName)
      replaceWord("DemoConnector", connectorClassName)
      insertGeneratedFileWarningLines(srcFile, linePrefix = "package ")
      insertGeneratedFileWarningLines(srcFile, linePrefix = "class ")
    }

    logger.info("Writing to file: {}", destFile)
    transformer.writeLines(destFile)
  }
}
