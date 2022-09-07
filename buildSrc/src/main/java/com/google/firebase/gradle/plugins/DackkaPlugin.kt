package com.google.firebase.gradle.plugins

import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.gradle.LibraryExtension
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * Facilitates the creation of Firesite compliant reference documentation.
 *
 * This plugin handles a procedure of processes, all registered under the "kotlindoc" task.
 *
 * Those tasks are:
 *  - Collect the arguments needed to run Dackka
 *  - Run Dackka with [GenerateDocumentationTask] to create the initial reference docs
 *  - Clean up the output with [FiresiteTransformTask] to fix minor inconsistencies
 *  - Remove the java references generated from the task (we do not currently support them)
 *  - Copies the output files to a common directory under the root project's build directory
 *
 *  @see GenerateDocumentationTask
 *  @see FiresiteTransformTask
 *  @see JavadocPlugin
 */
abstract class DackkaPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        prepareJavadocConfiguration(project)
        registerCleanDackkaDocumentation(project)
        project.afterEvaluate {
            if (shouldWePublish(project)) {
                val generateDocumentation = registerGenerateDackkaDocumentationTask(project)
                val outputDirectory = generateDocumentation.flatMap { it.outputDirectory }
                val firesiteTransform = registerFiresiteTransformTask(project, outputDirectory)
                val deleteJavaReferences = registerDeleteDackkaGeneratedJavaReferencesTask(project, outputDirectory)
                val copyOutputToCommonDirectory = registerCopyDackkaOutputToCommonDirectoryTask(project, outputDirectory)

                project.tasks.register("kotlindoc") {
                    group = "documentation"
                    dependsOn(
                        generateDocumentation,
                        firesiteTransform,
                        deleteJavaReferences,
                        copyOutputToCommonDirectory
                    )
                }
            } else {
                project.tasks.register("kotlindoc")
            }
        }
    }

    private fun shouldWePublish(project: Project) =
        project.extensions.getByType<FirebaseLibraryExtension>().publishJavadoc

    private fun prepareJavadocConfiguration(project: Project) {
        val javadocConfig = project.javadocConfig
        javadocConfig.dependencies += project.dependencies.create("com.google.code.findbugs:jsr305:3.0.2")
        javadocConfig.dependencies += project.dependencies.create("com.google.errorprone:error_prone_annotations:2.15.0")
        javadocConfig.attributes.attribute(
            BuildTypeAttr.ATTRIBUTE,
            project.objects.named(BuildTypeAttr::class.java, "release")
        )
    }

    // TODO(b/243534168): Refactor when fixed, so we no longer need stubs
    private fun registerGenerateDackkaDocumentationTask(project: Project): Provider<GenerateDocumentationTask> {
        val docStubs = project.tasks.register<GenerateStubsTask>("docStubsForDackkaInput")
        val docsTask = project.tasks.register<GenerateDocumentationTask>("generateDackkaDocumentation")
        with(project.extensions.getByType<LibraryExtension>()) {
            libraryVariants.all {
                if (name == "release") {
                    val isKotlin = project.plugins.hasPlugin("kotlin-android")

                    val classpath =
                        runtimeConfiguration.getJars() + project.javadocConfig.getJars() + bootClasspath

                    val sourcesForJava = sourceSets.flatMap {
                        it.javaDirectories.map { it.absoluteFile }
                    }

                    docStubs.configure {
                        classPath = project.files(classpath)
                        sources.set(project.provider { sourcesForJava })
                    }

                    docsTask.configure {
                        // this will become useful with the agp upgrade, as they're separate in 7.x+
                        val sourcesForKotlin = emptyList<File>()
                        val packageLists = fetchPackageLists(project)

                        val excludedFiles =
                            if (!isKotlin) projectSpecificSuppressedFiles(project) else emptyList()
                        val fixedJavaSources =
                            if (!isKotlin) listOf(project.docStubs) else sourcesForJava

                        javaSources.set(fixedJavaSources)
                        suppressedFiles.set(excludedFiles)
                        packageListFiles.set(packageLists)

                        kotlinSources.set(sourcesForKotlin)
                        dependencies.set(classpath)

                        kotlinSources.set(sourcesForKotlin)
                        dependencies.set(classpath)

                        applyCommonConfigurations()
                    }
                }
            }
        }

        return docsTask
    }

    private fun fetchPackageLists(project: Project) =
        project.rootProject.fileTree("kotlindoc/package-lists").matching {
            include("**/package-list")
        }.toList()

    // TODO(b/243534168): Remove when fixed
    private fun projectSpecificSuppressedFiles(project: Project): List<File> =
        when (project.name) {
            "firebase-common" -> {
                project.files("${project.docStubs}/com/google/firebase/firestore").toList()
            }
            "firebase-firestore" -> {
                project.files("${project.docStubs}/com/google/firebase/Timestamp.java").toList()
            }
            else -> emptyList()
        }

    private fun GenerateDocumentationTask.applyCommonConfigurations() {
        dependsOnAndMustRunAfter("createFullJarRelease")

        val dackkaFile = project.provider { project.dackkaConfig.singleFile }
        val dackkaOutputDirectory = File(project.buildDir, "dackkaDocumentation")

        dackkaJarFile.set(dackkaFile)
        outputDirectory.set(dackkaOutputDirectory)
    }

    // TODO(b/243833009): Make task cacheable
    private fun registerFiresiteTransformTask(project: Project, outputDirectory: Provider<File>) =
        project.tasks.register<FiresiteTransformTask>("firesiteTransform") {
            dackkaFiles.set(outputDirectory)
        }

    // If we decide to publish java variants, we'll need to address the generated format as well
    // TODO(b/243833009): Make task cacheable
    private fun registerDeleteDackkaGeneratedJavaReferencesTask(project: Project, outputDirectory: Provider<File>) =
        project.tasks.register<Delete>("deleteDackkaGeneratedJavaReferences") {
            mustRunAfter("generateDackkaDocumentation")

            val filesWeDoNotNeed = listOf(
                "reference/client",
                "reference/com"
            )
            val filesToDelete = outputDirectory.map { dir ->
                filesWeDoNotNeed.map {
                    project.files("${dir.path}/$it")
                }
            }

            delete(filesToDelete)
        }

    private fun registerCopyDackkaOutputToCommonDirectoryTask(project: Project, outputDirectory: Provider<File>) =
        project.tasks.register<Copy>("copyDackkaOutputToCommonDirectory") {
            mustRunAfter("deleteDackkaGeneratedJavaReferences")
            mustRunAfter("firesiteTransform")

            val referenceFolder = outputDirectory.map { project.file("${it.path}/reference") }
            val outputFolder = project.file("${project.rootProject.buildDir}/firebase-kotlindoc")

            from(referenceFolder)
            destinationDir = outputFolder
        }

    // Useful for local testing, but may not be desired for standard use (that's why it's not depended on)
    private fun registerCleanDackkaDocumentation(project: Project) =
        project.tasks.register<Delete>("cleanDackkaDocumentation") {
            group = "cleanup"

            delete("${project.buildDir}/dackkaDocumentation")
        }
}
