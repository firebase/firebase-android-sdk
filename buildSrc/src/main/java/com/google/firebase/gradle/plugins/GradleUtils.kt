package com.google.firebase.gradle.plugins

import java.io.File
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy

fun Copy.fromDirectory(directory: Provider<File>) =
    from(directory) {
        into(directory.map { it.name })
    }

/**
 * Creates a file at the buildDir for the given [Project].
 *
 * Syntax sugar for:
 *
 * ```
 * project.file("${project.buildDir}/$path)
 * ```
 */
fun Project.fileFromBuildDir(path: String) = file("$buildDir/$path")

/**
 * Maps a file provider to another file provider as a sub directory.
 *
 * Syntax sugar for:
 *
 * ```
 * fileProvider.map { project.file("${it.path}/$path") }
 * ```
 */
fun Project.childFile(provider: Provider<File>, childPath: String) = provider.map {
    file("${it.path}/$childPath")
}
