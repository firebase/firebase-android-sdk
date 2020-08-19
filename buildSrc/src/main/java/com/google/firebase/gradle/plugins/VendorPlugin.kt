// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.gradle.plugins

import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logger
import org.gradle.kotlin.dsl.apply

class VendorPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.all {
            when (this) {
                is LibraryPlugin -> configureAndroid(project)
            }
        }
    }

    fun configureAndroid(project: Project) {
        project.apply(plugin = "LicenseResolverPlugin")

        val vendor = project.configurations.create("vendor")
        project.configurations.all {
            when (name) {
                "compileOnly", "testImplementation", "androidTestImplementation" -> extendsFrom(vendor)
            }
        }

        val jarJar = project.configurations.create("firebaseJarJarArtifact")
        project.dependencies.add("firebaseJarJarArtifact", "org.pantsbuild:jarjar:1.7.2")

        val android = project.extensions.getByType(LibraryExtension::class.java)

        android.registerTransform(VendorTransform(
                android,
                vendor,
                JarJarTransformer(
                        parentPackageProvider = {
                            android.libraryVariants.find { it.name == "release" }!!.applicationId
                        },
                        jarJarProvider = { jarJar.resolve() },
                        project = project,
                        logger = project.logger),
                logger = project.logger))
    }
}

interface JarTransformer {
    fun transform(inputJar: File, outputJar: File, packagesToVendor: Set<String>)
}

class JarJarTransformer(
    private val parentPackageProvider: () -> String,
    private val jarJarProvider: () -> Collection<File>,
    private val project: Project,
    private val logger: Logger
) : JarTransformer {
    override fun transform(inputJar: File, outputJar: File, packagesToVendor: Set<String>) {
        val parentPackage = parentPackageProvider()
        val rulesFile = File.createTempFile(parentPackage, ".jarjar")
        rulesFile.printWriter().use {
            for (externalPackageName in packagesToVendor) {
                it.println("rule $externalPackageName.** $parentPackage.@0")
            }
        }
        logger.info("The following JarJar configuration will be used:\n ${rulesFile.readText()}")

        project.javaexec {
            main = "org.pantsbuild.jarjar.Main"
            classpath = project.files(jarJarProvider())
            args = listOf("process", rulesFile.absolutePath, inputJar.absolutePath, outputJar.absolutePath)
            systemProperties = mapOf("verbose" to "true", "misplacedClassStrategy" to "FATAL")
        }.assertNormalExitValue()
    }
}

class VendorTransform(
    private val android: LibraryExtension,
    private val configuration: Configuration,
    private val jarTransformer: JarTransformer,
    private val logger: Logger
) :
        Transform() {
    override fun getName() = "firebaseVendorTransform"

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return mutableSetOf(QualifiedContent.DefaultContentType.CLASSES)
    }

    override fun isIncremental() = false

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return mutableSetOf(QualifiedContent.Scope.PROJECT)
    }

    override fun getReferencedScopes(): MutableSet<in QualifiedContent.Scope> {
        return mutableSetOf(QualifiedContent.Scope.PROJECT)
    }

    override fun transform(transformInvocation: TransformInvocation) {
        if (configuration.resolve().isEmpty()) {
            logger.warn("Nothing to vendor. " +
                    "If you don't need vendor functionality please disable 'firebase-vendor' plugin. " +
                    "Otherwise use the 'vendor' configuration to add dependencies you want vendored in.")
            for (input in transformInvocation.inputs) {
                for (directoryInput in input.directoryInputs) {
                    val directoryOutput = transformInvocation.outputProvider.getContentLocation(
                            directoryInput.name,
                            setOf(QualifiedContent.DefaultContentType.CLASSES),
                            mutableSetOf(QualifiedContent.Scope.PROJECT),
                            Format.DIRECTORY)
                    directoryInput.file.copyRecursively(directoryOutput, overwrite = true)
                }
            }
            return
        }

        val contentLocation = transformInvocation.outputProvider.getContentLocation(
                "sourceAndVendoredLibraries",
                setOf(QualifiedContent.DefaultContentType.CLASSES),
                mutableSetOf(QualifiedContent.Scope.PROJECT),
                Format.DIRECTORY)
        contentLocation.deleteRecursively()
        contentLocation.mkdirs()
        val tmpDir = File(contentLocation, "tmp")
        tmpDir.mkdirs()
        try {
            val fatJar = process(tmpDir, transformInvocation)
            unzipJar(fatJar, contentLocation)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private fun isTest(transformInvocation: TransformInvocation): Boolean {
        return android.testVariants.find { it.name == transformInvocation.context.variantName } != null
    }

    private fun process(workDir: File, transformInvocation: TransformInvocation): File {
        transformInvocation.context.variantName
        val unzippedDir = File(workDir, "unzipped")
        val unzippedExcludedDir = File(workDir, "unzipped-excluded")
        unzippedDir.mkdirs()
        unzippedExcludedDir.mkdirs()

        val externalCodeDir = if (isTest(transformInvocation)) unzippedExcludedDir else unzippedDir

        for (input in transformInvocation.inputs) {
            for (directoryInput in input.directoryInputs) {
                directoryInput.file.copyRecursively(unzippedDir)
            }
        }

        val ownPackageNames = inferPackages(unzippedDir)

        for (jar in configuration.resolve()) {
            unzipJar(jar, externalCodeDir)
        }
        val externalPackageNames = inferPackages(externalCodeDir) subtract ownPackageNames
        val java = File(externalCodeDir, "java")
        val javax = File(externalCodeDir, "javax")
        if (java.exists() || javax.exists()) {
            // JarJar unconditionally skips any classes whose package name starts with "java" or "javax".
            throw GradleException("Vendoring java or javax packages is not supported. " +
                    "Please exclude one of the direct or transitive dependencies: \n" +
                    configuration.resolvedConfiguration.resolvedArtifacts.joinToString(separator = "\n"))
        }
        val jar = File(workDir, "intermediate.jar")
        zipAll(unzippedDir, jar)
        val outputJar = File(workDir, "output.jar")

        jarTransformer.transform(jar, outputJar, externalPackageNames)
        return outputJar
    }

    private fun inferPackages(dir: File): Set<String> {
        return dir.walk().filter { it.name.endsWith(".class") }.map { it.parentFile.toRelativeString(dir).replace('/', '.') }.toSet()
    }
}

fun unzipJar(jar: File, directory: File) {
    ZipFile(jar).use { zip ->
        zip.entries().asSequence().filter { !it.isDirectory && !it.name.startsWith("META-INF") }.forEach { entry ->
            zip.getInputStream(entry).use { input ->
                val entryFile = File(directory, entry.name)
                entryFile.parentFile.mkdirs()
                entryFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}

fun zipAll(directory: File, zipFile: File) {

    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use {
        zipFiles(it, directory, "")
    }
}

private fun zipFiles(zipOut: ZipOutputStream, sourceFile: File, parentDirPath: String) {
    val data = ByteArray(2048)
    sourceFile.listFiles()?.forEach { f ->
        if (f.isDirectory) {
            val path = if (parentDirPath == "") {
                f.name
            } else {
                parentDirPath + File.separator + f.name
            }
            // Call recursively to add files within this directory
            zipFiles(zipOut, f, path)
        } else {
            FileInputStream(f).use { fi ->
                BufferedInputStream(fi).use { origin ->
                    val path = parentDirPath + File.separator + f.name
                    val entry = ZipEntry(path)
                    entry.time = f.lastModified()
                    entry.isDirectory
                    entry.size = f.length()
                    zipOut.putNextEntry(entry)
                    while (true) {
                        val readBytes = origin.read(data)
                        if (readBytes == -1) {
                            break
                        }
                        zipOut.write(data, 0, readBytes)
                    }
                }
            }
        }
    }
}
