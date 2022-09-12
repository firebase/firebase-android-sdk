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
# Dackka Plugin

The Dackka Plugin is a wrapper around internal tooling at Google called Dackka, which generates
documentation for [Firesite](https://firebase.google.com/docs/reference)

## Dackka

Dackka is an internal-purposed Dokka plugin. Google hosts its documentation on
an internal service called **Devsite**. Firebase hosts their documentation on a
variant of Devsite called **Firesite**. You can click [here](https://firebase.google.com/docs/reference)
to see how that looks. Essentially, it's just Google's way of decorating (and organizing)
documentation.

Devsite expects its files to be in a very specific format. Previously, we would
use an internal Javadoc doclet called [Doclava](https://code.google.com/archive/p/doclava/) - which
allowed us to provide sensible defaults as to how the Javadoc should be
rendered. Then, we would do some further transformations to get the Javadoc
output in-line with what Devsite expects. This was a lengthy process, and came
with a lot of overhead. Furthermore, Doclava does not support kotlindoc and has
been unmaintained for many years.

Dackka is an internal solution to that. Dackka provides a devsite plugin for
Dokka that will handle the job of doclava. Not only does this mean we can cut
out a huge portion of our transformation systems- but the overhead for maintaining
such systems is deferred away to the AndroidX team (the maintainers of Dackka).

## Dackka Usage

The Dackka we use is a fat jar pulled periodically from Dackka nightly builds,
and moved to our own maven repo bucket. Since it's recommended from the AndroidX
team to run Dackka on the command line, the fat jar allows us to ignore all the
miscenalionous dependencies of Dackka (in regards to Dokka especially).

The general process of using Dackka is that you collect the dependencies and
source sets of the gradle project, create a
[Dokka appropriate JSON file](https://kotlin.github.io/dokka/1.7.10/user_guide/cli/usage/#example-using-json),
run the Dackka fat jar with the JSON file as an argument, and publish the
output folder.

## Implementation

Our implementation of Dackka falls into three separate files, and four separate
tasks.

### [GenerateDocumentationTask]

This task is the meat of our Dackka implementation. It's what actually handles
the running of Dackka itself. The task exposes a gradle extension called
[GenerateDocumentationTaskExtension] with various configuration points for
Dackka. This will likely be expanded upon in the future, as configurations are
needed.

The job of this task is to **just** run Dackka. What happens after-the-fact does
not matter to this task. It will take the provided inputs, organize them into
the expected JSON file, and run Dackka with the JSON file as an argument.

### [FiresiteTransformTask]

Dackka was designed with Devsite in mind. The problem though, is that we use
Firesite. Firesite is very similar to Devsite, but there *are* minor differences.

The job of this task is to transform the Dackka output from a Devsite purposed format,
to a Firesite purposed format. This includes removing unnecessary files, fixing
links, removing unnecessary headers, and so forth.

There are open bugs for each transformation, as in an ideal world- they are instead
exposed as configurations from Dackka. Should these configurations be adopted by
Dackka, this task could become unnecessary itself- as we could just configure the task
during generation.

### DackkaPlugin

This plugin is the mind of our Dackka implementation. It manages registering,
and configuring all the tasks for Dackka (that is, the already established
tasks above). While we do not currently offer any configuration for the Dackka
plugin, this could change in the future as needed. Currently, the DackkaPlugin
provides sensible defaults to output directories, package lists, and so forth.

The DackkaPlugin also provides two extra tasks:
[cleanDackkaDocumentation][registerCleanDackkaDocumentation] and
[deleteDackkaGeneratedJavaReferences][registerDeleteDackkaGeneratedJavaReferencesTask].

_cleanDackkaDocumentation_ is exactly what it sounds like, a task to clean up (delete)
the output of Dackka. This is useful when testing Dackka outputs itself- and
shouldn't be apart of the normal flow. The reasoning is that it would otherwise
invalidate the gradle cache.

_deleteDackkaGeneratedJavaReferences_ is a temporary addition. Dackka generates
two separate styles of docs for every source set: Java & Kotlin. Regardless of
whether the source is in Java or Kotlin. The Java output is how the source looks
from Java, and the Kotlin output is how the source looks from Kotlin. We publish
these under two separate categories, which you can see here:
[Java](https://firebase.google.com/docs/reference/android/packages)
or
[Kotlin](https://firebase.google.com/docs/reference/kotlin/packages).
Although, we do not currently publish Java packages with Dackka- and will wait
until we are more comfortable with the output of Dackka to do so. So until then,
this task will remove all generate Java references from the Dackka output.

Currently, the DackkaPlugin builds Java sources separate from Kotlin Sources. There is an open bug
for Dackka in which hidden parent classes and annotations do not hide themselves from children classes.
To work around this, we are currently generating stubs for Java sources via metalava, and feeding the stubs
to Dackka. This will be removed when the bug is fixed, per b/243954517
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

    // TODO(b/243324828): Refactor when fixed, so we no longer need stubs
    private fun registerGenerateDackkaDocumentationTask(project: Project): Provider<GenerateDocumentationTask> {
        val docStubs = project.tasks.register<GenerateStubsTask>("docStubsForDackkaInput")
        val docsTask = project.tasks.register<GenerateDocumentationTask>("generateDackkaDocumentation")
        with(project.extensions.getByType<LibraryExtension>()) {
            libraryVariants.all {
                if (name == "release") {
                    val isKotlin = project.plugins.hasPlugin("kotlin-android")

                    val classpath = runtimeConfiguration.getJars() + project.javadocConfig.getJars() + bootClasspath

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

                        val excludedFiles = if (!isKotlin) projectSpecificSuppressedFiles(project) else emptyList()
                        val fixedJavaSources = if (!isKotlin) listOf(project.docStubs) else sourcesForJava

                        javaSources.set(fixedJavaSources)
                        suppressedFiles.set(excludedFiles)
                        packageListFiles.set(packageLists)

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
