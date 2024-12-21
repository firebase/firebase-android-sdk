/*
 * Copyright 2020 Google LLC
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

package com.google.firebase.gradle.plugins

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.LibraryPlugin
import com.google.firebase.gradle.plugins.license.LicenseResolverPlugin
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.utils.extendsFrom

class VendorPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.withType<LibraryPlugin>().configureEach {
            configureAndroid(project)
        }
    }

    fun configureAndroid(project: Project) {
        project.apply<LicenseResolverPlugin>() // TODO: why?

        val vendor = project.configurations.register("vendor")
        val configurations = listOf(
            "releaseCompileOnly",
            "debugImplementation",
            "testImplementation",
            "androidTestImplementation"
        )

        for (config in configurations) {
            project.configurations.named(config).extendsFrom(vendor)
        }

//        val jarJarArtifact = project.configurations.register("firebaseJarJarArtifact") {
//            dependencies.add(project.dependencies.create("org.pantsbuild:jarjar:1.7.2"))
//        }
        // project.dependencies.add("firebaseJarJarArtifact", "org.pantsbuild:jarjar:1.7.2")

        val androidComponents = project.extensions.getByType<LibraryAndroidComponentsExtension>()

        androidComponents.onVariants(
            androidComponents.selector().withBuildType("release")
        ) { variant ->
            val vendorTask =
                project.tasks.register<VendorTask>("${variant.name}VendorTransform") {
                    vendorDependencies.set(vendor)
                    packageName.set(variant.namespace)
                }
            variant.artifacts
                .forScope(ScopedArtifacts.Scope.PROJECT)
                .use(vendorTask)
                .toTransform(
                    ScopedArtifact.CLASSES,
                    VendorTask::inputJars,
                    VendorTask::inputDirs,
                    VendorTask::outputJar,
                )
        }
    }
}

abstract class VendorTask : DefaultTask() {
    @get:[InputFiles Classpath]
    abstract val vendorDependencies: Property<Configuration>

    @get:Input
    abstract val packageName: Property<String>

    @get:InputFiles
    abstract val inputJars: ListProperty<RegularFile>

    @get:InputFiles
    abstract val inputDirs: ListProperty<Directory>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun taskAction() {
        val workDir = temporaryDir.resolve("vendorWork")
        workDir.deleteRecursively()
        val unzippedDir = workDir.resolve("unzipped")
        //val externalCodeDir = unzippedDir.resolve("external")
        val externalCodeDir = workDir.resolve("external")

        for (directory in inputDirs.get()) {
            directory.asFile.copyRecursively(unzippedDir)
        }
        for (jar in inputJars.get()) {
            unzipJar(jar.asFile, unzippedDir)
        }

        val ownPackageNames = inferPackages(unzippedDir)

        for (jar in vendorDependencies.get()) {
            unzipJar(jar, externalCodeDir)
        }
        val externalPackageNames = inferPackages(externalCodeDir) subtract ownPackageNames

        zipWithNamespaceTransformation(
            unzippedDir,
            externalPackageNames,
            packageName.get(),
            outputJar.get().asFile
        )
        zipWithNamespaceTransformation(
            unzippedDir,
            externalPackageNames,
            packageName.get(),
            workDir.resolve("temp.zip")
        )
    }

    private fun inferPackages(dir: File): Set<String> {
        return dir.walkTopDown()
            .filter { it.extension === "class" }
            .map { it.relativeTo(dir).parent.replace(File.separator, ".") }
            .toSet()
    }

    private fun zipWithNamespaceTransformation(
        inputDir: File,
        externalPackages: Set<String>,
        newNamespace: String,
        outputFile: File
    ) {
        ZipOutputStream(outputFile.outputStream()).use { zipOutput ->
            inputDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entryName = file.relativeTo(inputDir).path
                    // Transform class files and external package names
                    val transformedName = if (entryName.endsWith(".class")) {
                        transformPackageName(entryName, externalPackages, newNamespace)
                    } else {
                        entryName // Leave non-class files, including Kotlin metadata, untouched
                    }
                    zipOutput.putNextEntry(ZipEntry(transformedName))
                    file.inputStream().use { it.copyTo(zipOutput) }
                }
            }
        }
    }

    private fun transformPackageName(
        entryName: String,
        externalPackages: Set<String>,
        newNamespace: String
    ): String {
        for (externalPackage in externalPackages) {
            if (entryName.startsWith(externalPackage.replace('.', '/'))) {
                return entryName.replace(
                    externalPackage.replace('.', '/'),
                    newNamespace.replace('.', '/')
                )
            }
        }
        return entryName
    }
}


fun unzipJar(jar: File, directory: File) {
    ZipFile(jar).use { zip ->
        zip
            .entries()
            .asSequence()
            .filter { !it.isDirectory && !it.name.startsWith("META-INF") }
            .forEach { entry ->
                zip.getInputStream(entry).use { input ->
                    val entryFile = File(directory, entry.name)
                    entryFile.parentFile.mkdirs()
                    entryFile.outputStream().use { output -> input.copyTo(output) }
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
            val path =
                if (parentDirPath == "") {
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
