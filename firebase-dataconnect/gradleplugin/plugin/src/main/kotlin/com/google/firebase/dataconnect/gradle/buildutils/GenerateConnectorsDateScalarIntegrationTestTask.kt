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

    TestDestFileGenerator(
        srcFile = testSrcFile,
        destFile = testDestFile,
        kotlinPackage = testKotlinPackage,
        className = testClassName,
        superClassName = testBaseClassName,
        connectorPackageName = connectorPackageName,
        connectorClassName = connectorClassName,
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
  val logger: Logger,
) {

  fun run() {
    logger.info("Reading from file: {}", srcFile)
    val transformer = TextLinesTransformer(srcFile)

    transformer.atLineThatStartsWith("package ").replaceWith("package $kotlinPackage")
    transformer.replaceWord("DateScalarIntegrationTest", className)
    transformer.replaceWord("DemoConnectorIntegrationTestBase", superClassName)
    transformer.replaceWord("com.google.firebase.dataconnect.connectors.demo", connectorPackageName)
    transformer.replaceWord("DemoConnector", connectorClassName)

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

    transformer.atLineThatStartsWith("package ").replaceWith("package $kotlinPackage")
    transformer.replaceWord("DemoConnectorIntegrationTestBase", className)
    transformer.replaceWord("com.google.firebase.dataconnect.connectors.demo", connectorPackageName)
    transformer.replaceWord("DemoConnector", connectorClassName)
    transformer.replaceWord("TestDemoConnectorFactory", connectorFactoryClassName)

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

    transformer.atLineThatStartsWith("package ").replaceWith("package $kotlinPackage")
    transformer.replaceWord("TestDemoConnectorFactory", className)
    transformer.replaceWord("com.google.firebase.dataconnect.connectors.demo", connectorPackageName)
    transformer.replaceWord("DemoConnector", connectorClassName)

    logger.info("Writing to file: {}", destFile)
    transformer.writeLines(destFile)
  }
}
