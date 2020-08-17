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

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.GradleException
import org.w3c.dom.Element
import org.w3c.dom.NodeList

data class Project(
    val name: String,
    val group: String = "com.example",
    val version: String = "undefined",
    val latestReleasedVersion: String? = null,
    val projectDependencies: Set<Project> = setOf(),
    val externalDependencies: Set<Artifact> = setOf(),
    val releaseWith: Project? = null,
    val customizePom: String? = null,
    val libraryType: LibraryType = LibraryType.ANDROID
) {
    fun generateBuildFile(): String {
        return """
            plugins {
                id 'firebase-${if (libraryType == LibraryType.JAVA) "java-" else ""}library'
            }
            group = '$group'
            version = '$version'
            ${if (latestReleasedVersion != null) "ext.latestReleasedVersion = $latestReleasedVersion" else ""}
            firebaseLibrary {
                ${if (releaseWith != null) "releaseWith project(':${releaseWith.name}')" else ""}
                ${if (customizePom != null) "customizePom {$customizePom}" else ""}
            }
            ${if (libraryType == LibraryType.ANDROID) "android.compileSdkVersion = 26" else ""}

            dependencies {
            ${projectDependencies.joinToString("\n") { "implementation project(':${it.name}')" }}
            ${externalDependencies.joinToString("\n") { "implementation '${it.simpleDepString}'" }}
            }
            """
    }

    fun getPublishedPom(rootDirectory: String): Pom? {
        val v = releaseWith?.version ?: version
        return File(rootDirectory).walk().asSequence()
                .filter { it.isFile }
                .filter {
                    it.path.matches(Regex(".*/${group.replace('.', '/')}/$name/$v.*/.*\\.pom$"))
                }
                .map(Pom::parse)
                .firstOrNull()
    }
}

data class License(val name: String, val url: String)

enum class Type {
    JAR, AAR
}

data class Artifact(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val type: Type = Type.JAR,
    val scope: String = ""
) {
    val simpleDepString: String
        get() = "$groupId:$artifactId:$version"
}

data class Pom(
    val artifact: Artifact,
    val license: License = License(
            name = "The Apache Software License, Version 2.0",
            url = "http://www.apache.org/licenses/LICENSE-2.0.txt"),
    val dependencies: List<Artifact> = listOf()
) {
    companion object {
        fun parse(file: File): Pom {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val childNodes = document.documentElement.childNodes

            var groupId: String? = null
            var artifactId: String? = null
            var version: String? = null
            var type = Type.JAR
            var license: License? = null
            var deps: List<Artifact> = listOf()

            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child !is Element) {
                    continue
                }
                when (child.tagName) {
                    "groupId" -> groupId = child.textContent.trim()
                    "artifactId" -> artifactId = child.textContent.trim()
                    "version" -> version = child.textContent.trim()
                    "packaging" -> type = Type.valueOf(child.textContent.trim().toUpperCase())
                    "licenses" -> license = parseLicense(child.getElementsByTagName("license"))
                    "dependencies" -> deps = parseDeps(child.getElementsByTagName("dependency"))
                }
            }
            if (groupId == null) {
                throw GradleException("'<groupId>' missing in pom")
            }
            if (artifactId == null) {
                throw GradleException("'<artifactId>' missing in pom")
            }
            if (version == null) {
                throw GradleException("'<version>' missing in pom")
            }
            if (license == null) {
                throw GradleException("'<license>' missing in pom")
            }

            return Pom(Artifact(groupId, artifactId, version, type), license, deps)
        }

        private fun parseDeps(nodes: NodeList): List<Artifact> {
            val deps = mutableListOf<Artifact>()
            for (i in 0 until nodes.length) {
                val child = nodes.item(i)
                if (child !is Element) {
                    continue
                }
                deps.add(parseDep(child))
            }
            return deps
        }

        private fun parseDep(dependencies: Element): Artifact {
            var groupId: String? = null
            var artifactId: String? = null
            var version: String? = null
            var type = Type.JAR
            var scope: String? = null

            val nodes = dependencies.childNodes

            for (i in 0 until nodes.length) {
                val child = nodes.item(i)
                if (child !is Element) {
                    continue
                }
                when (child.tagName) {
                    "groupId" -> groupId = child.textContent.trim()
                    "artifactId" -> artifactId = child.textContent.trim()
                    "version" -> version = child.textContent.trim()
                    "type" -> type = Type.valueOf(child.textContent.trim().toUpperCase())
                    "scope" -> scope = child.textContent.trim()
                }
            }
            if (groupId == null) {
                throw GradleException("'<groupId>' missing in pom")
            }
            if (artifactId == null) {
                throw GradleException("'<artifactId>' missing in pom")
            }
            if (version == null) {
                throw GradleException("'<version>' missing in pom")
            }
            if (scope == null) {
                throw GradleException("'<scope>' missing in pom")
            }

            return Artifact(groupId, artifactId, version, type, scope)
        }

        private fun parseLicense(nodes: NodeList): License? {
            if (nodes.length == 0) {
                return null
            }
            val license = nodes.item(0) as Element
            val urlElements = license.getElementsByTagName("url")
            val url = if (urlElements.length == 0) "" else urlElements.item(0).textContent.trim()

            val nameElements = license.getElementsByTagName("name")
            val name = if (nameElements.length == 0) "" else nameElements.item(0).textContent.trim()
            return License(name = name, url = url)
        }
    }
}
