// Copyright 2019 Google LLC
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

import com.android.build.gradle.LibraryExtension
import com.google.firebase.gradle.plugins.ci.device.FirebaseTestLabExtension
import java.io.File
import java.util.Optional
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenPom
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.getPlugin
import org.gradle.kotlin.dsl.property

open class FirebaseLibraryExtension @Inject constructor(@JvmField val project: Project, @JvmField val type: LibraryType) {
    private val librariesToCoRelease: MutableSet<FirebaseLibraryExtension> = mutableSetOf()

    /** Indicates whether the library has public javadoc.  */
    @JvmField
    var publishJavadoc = true

    /** Indicates whether sources are published alongside the library.  */
    @JvmField
    var publishSources = false

    /** Static analysis configuration.  */
    @JvmField
    val staticAnalysis: FirebaseStaticAnalysis

    /** Firebase Test Lab configuration/  */
    @JvmField
    val testLab: FirebaseTestLabExtension = FirebaseTestLabExtension(project.objects)

    @JvmField
    var groupId: Property<String> = project.objects.property()

    @JvmField
    var artifactId: Property<String> = project.objects.property()

    init {
        if ("ktx" == project.name && project.parent != null) {
            artifactId.set(DefaultProvider { project.parent!!.name + "-ktx" })
            groupId.set(DefaultProvider { project.parent!!.group.toString() })
        } else {
            artifactId.set(DefaultProvider { project.name })
            groupId.set(DefaultProvider { project.group.toString() })
        }
        staticAnalysis = initializeStaticAnalysis(project)
    }

    private var customizePomAction: MavenPom.() -> Unit = {
        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/firebase/firebase-android-sdk.git")
            url.set("https://github.com/firebase/firebase-android-sdk")
        }
    }

    private fun initializeStaticAnalysis(project: Project): FirebaseStaticAnalysis {
        return FirebaseStaticAnalysis(
            projectsFromProperty(project, "firebase.checks.errorproneProjects"),
            projectsFromProperty(project, "firebase.checks.lintProjects")
        )
    }

    private fun projectsFromProperty(project: Project, propertyName: String): Set<String> {
        return if (!project.hasProperty(propertyName)) {
            emptySet()
        } else {
            project.property(propertyName).toString().split(",").filter { it.isNotEmpty() }.toSet()
        }
    }

    /** Configure Firebase Test Lab.  */
    fun testLab(action: Action<FirebaseTestLabExtension>) {
        action.execute(testLab)
    }

    /**
     * Register to be released alongside another Firebase Library project.
     *
     *
     * This will force the released version of the current project to match the one it's released
     * with.
     */
    fun releaseWith(releaseWithProject: Project) {
        try {
            val releaseWithLibrary = releaseWithProject.extensions.getByType<FirebaseLibraryExtension>()
            releaseWithLibrary.librariesToCoRelease.add(this)
            project.version = releaseWithProject.version
            val latestRelease = "latestReleasedVersion"
            if (releaseWithProject.extensions.extraProperties.has(latestRelease)) {
                project
                    .extensions
                    .extraProperties[latestRelease] = releaseWithProject.properties[latestRelease]
            }
        } catch (ex: UnknownDomainObjectException) {
            throw GradleException(
                "Library cannot be released with a project that is not a Firebase Library itself"
            )
        }
    }

    val projectsToRelease: Set<Project>
        get() = setOf(project).plus(librariesToCoRelease.asSequence().map { it.project })

    val librariesToRelease: Set<FirebaseLibraryExtension>
        get() = setOf(this).plus(librariesToCoRelease)

    /** Provides a hook to customize pom generation.  */
    fun customizePom(action: Action<MavenPom>) {
        customizePomAction = action::execute
    }

    fun applyPomCustomization(pom: MavenPom) {
        pom.customizePomAction()
    }

    fun staticAnalysis(action: Action<FirebaseStaticAnalysis?>) {
        action.execute(staticAnalysis)
    }

    val version: String
        get() = project.version.toString()
    val latestReleasedVersion: Optional<String>
        get() = if (project.hasProperty("latestReleasedVersion")) {
            Optional.of(
                project.property("latestReleasedVersion").toString()
            )
        } else Optional.empty()

    val mavenName: String
        get() = "${groupId.get()}:${artifactId.get()}"

    val path: String
        get() = project.path

    val srcDirs: Set<File>
        get() = when (type) {
            LibraryType.ANDROID ->
                project
                    .extensions
                    .getByType<LibraryExtension>()
                    .sourceSets
                    .getByName("main")
                    .java
                    .srcDirs
            LibraryType.JAVA ->
                project
                    .convention
                    .getPlugin<JavaPluginConvention>()
                    .sourceSets
                    .getByName("main")
                    .java
                    .srcDirs
        }

    val runtimeClasspath: String
        get() = if (type == LibraryType.ANDROID) {
            "releaseRuntimeClasspath"
        } else "runtimeClasspath"

    override fun toString(): String {
        return "FirebaseLibraryExtension{name=\"${groupId.get()}:${artifactId.get()}\", project=\"${path}\", type=\"${type}\"}"
    }
}
