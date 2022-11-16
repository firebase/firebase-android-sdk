package com.google.firebase.gradle

import java.io.File
import java.io.IOException
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

open class ReleaseGenerator : DefaultTask() {
    @TaskAction
    @Throws(Exception::class)
    fun generateReleaseConfig() {
        val currentRelease = project.property("currentRelease").toString()
        val pastRelease = project.property("pastRelease").toString()
        val rootDir = project.rootDir
        val availableModules = parseSubProjects(rootDir)

        val repo = initRepo(rootDir)
        val headRef = repo.repository.resolve(Constants.HEAD)
        val branchRef = getObjectRefForBranchName(repo, pastRelease)

        val changedDirs = getChangedLibraries(repo, branchRef, headRef, FIREBASE_LIBRARIES)
        val changedModules = findChangedModules(changedDirs, availableModules)
        writeReleaseConfig(rootDir, changedModules, currentRelease)
    }

    private fun findChangedModules(changedDirs: List<String>, availableModules: Set<String>): List<String> = changedDirs.flatMap { directory ->
        val moduleName = directory.replace("/", ":")
        val ktxModuleName = "$moduleName:ktx"
        listOf(moduleName, ktxModuleName).filter { availableModules.contains(it) }
    }

    private fun parseSubProjects(rootDir: File) = File(rootDir, "subprojects.cfg").readLines().filter { !it.startsWith("#") }.toSet()

    @Throws(IOException::class)
    private fun initRepo(repoPath: File): Git {
        if (!repoPath.exists()) {
            throw RuntimeException("Repo path doesn't exist")
        }
        return Git.open(repoPath)
    }

    @Throws(GitAPIException::class)
    private fun getObjectRefForBranchName(repo: Git, branchName: String): ObjectId {
        return repo.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()
                .filter { ref: Ref -> ref.name == "refs/remotes/origin/$branchName" }
                .map { obj: Ref -> obj.objectId }.firstOrNull()
                ?: throw RuntimeException("Could not find branch named $branchName")
    }

    private fun getChangedLibraries(repo: Git, startRef: ObjectId, endRef: ObjectId, directories: List<String>): List<String> {
        return FIREBASE_LIBRARIES.filter { libraryPath: String ->
            repo.log()
                    .addPath("$libraryPath/*.kt")
                    .addPath("$libraryPath/*.java")
                    .addPath("$libraryPath/CHANGELOG.md")
                    .addPath("$libraryPath/*.gradle")
                    .addRange(startRef, endRef)
                    .setMaxCount(1)
                    .call()
                    .iterator().hasNext()
        }
    }

    private fun writeReleaseConfig(configPath: File, libraries: List<String>, releaseName: String) {
        File(configPath, "release.cfg").writeText(
                """
                    [release]
                    name = $releaseName
                    mode = RELEASE
                    
                    [modules]
                    ${libraries.joinToString("\n                    ")}
                """.trimIndent()
        )
    }

    companion object {
        private val FIREBASE_LIBRARIES = listOf(
                "appcheck/firebase-appcheck-debug-testing",
                "appcheck/firebase-appcheck-debug",
                "appcheck/firebase-appcheck-interop",
                "appcheck/firebase-appcheck-playintegrity",
                "appcheck/firebase-appcheck-safetynet",
                "appcheck/firebase-appcheck",
                "firebase-abt",
                "firebase-annotations",
                "firebase-appdistribution",
                "firebase-appdistribution-api",
                "firebase-common",
                "firebase-componenents",
                "firebase-config",
                "firebase-crashlytics",
                "firebase-crashlytics-ndk",
                "firebase-database",
                "firebase-database-collection",
                "firebase-datatransport",
                "firebase-dynamic-links",
                "firebase-firestore",
                "firebase-functions",
                "firebase-inappmessaging",
                "firebase-inappmessaging-display",
                "firebase-installations",
                "firebase-installations-interop",
                "firebase-messaging",
                "firebase-messaging-directboot",
                "firebase-ml-modeldownloader",
                "firebase-perf",
                "firebase-segmentations",
                "firebase-storage",
                "protolite-well-known-types")
    }
}
