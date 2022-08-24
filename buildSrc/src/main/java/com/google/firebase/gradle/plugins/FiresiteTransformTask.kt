package com.google.firebase.gradle.plugins

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Fixes minor inconsistencies between what dackka generates, and what firesite actually expects.
 *
 * Should dackka ever expand to offer configurations for these procedures, this class can be replaced.
 *
 * More specifically, it:
 *  - Deletes unnecessary files
 *  - Removes Class and Index headers from _toc.yaml files
 *  - Appends /docs/ to hyperlinks in html files
 *  - Removes the prefix path from book_path
 *  - Removes the firebase prefix from all links
 *
 *  **Please note:**
 *  This task is idempotent- meaning it can safely be ran multiple times on the same set of files.
 */
abstract class FiresiteTransformTask : DefaultTask() {
    @get:InputDirectory
    abstract val dackkaFiles: Property<File>

    @TaskAction
    fun build() {
        val namesOfFilesWeDoNotNeed = listOf(
            "index.html",
            "classes.html",
            "packages.html",
            "package-list"
        )

        dackkaFiles.get().walkTopDown().forEach {
            if (it.name in namesOfFilesWeDoNotNeed) {
                it.delete()
            } else {
                when (it.extension) {
                    "html" -> it.fixHTMLFile()
                    "yaml" -> it.fixYamlFile()
                }
            }
        }
    }

    private fun File.fixHTMLFile() {
        val fixedContent = readText().fixBookPath().fixHyperlinks().removeLeadingFirebaseDomainInLinks()
        writeText(fixedContent)
    }

    private fun File.fixYamlFile() {
        val fixedContent = readText().removeClassHeader().removeIndexHeader().removeLeadingFirebaseDomainInLinks()
        writeText(fixedContent)
    }

    // We don't actually upload class or index files,
    // so these headers will throw not found errors if not removed.
    // TODO(b/243674302): Remove when dackka supports this behavior
    private fun String.removeClassHeader() =
        remove(Regex("- title: \"Class Index\"\n {2}path: \".+\"\n\n"))
    private fun String.removeIndexHeader() =
        remove(Regex("- title: \"Package Index\"\n {2}path: \".+\"\n\n"))

    // We use a common book for all sdks, wheres dackka expects each sdk to have its own book.
    // TODO(b/243674303): Remove when dackka supports this behavior
    private fun String.fixBookPath() =
        remove(Regex("(?<=setvar book_path ?%})(.+)(?=/_book.yaml\\{% ?endsetvar)"))

    // Our documentation lives under /docs/reference/ versus the expected /reference/
    // TODO(b/243674305): Remove when dackka supports this behavior
    private fun String.fixHyperlinks() =
        replace(Regex("(?<=href=\")(/)(?=reference/.*\\.html)"), "/docs/")

    // The documentation will work fine without this. This is primarily to make sure that links
    // resolve to their local counter part. Meaning when the docs are staged, they will resolve to
    // staged docs instead of prod docs- and vise versa.
    private fun String.removeLeadingFirebaseDomainInLinks() =
        remove(Regex("(?<=\")(https://firebase\\.google\\.com)(?=/docs/reference)"))
}
