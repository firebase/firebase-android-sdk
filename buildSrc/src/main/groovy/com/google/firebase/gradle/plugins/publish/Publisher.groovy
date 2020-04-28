// Copyright 2018 Google LLC
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

package com.google.firebase.gradle.plugins.publish

import com.google.firebase.gradle.plugins.FirebaseLibraryExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.publish.maven.MavenPublication

/** Handles publication versioning and pom validation upon release. */
class Publisher {
    private static String UNRELEASED_VERSION = 'unreleased'
    enum Mode {
        RELEASE,
        SNAPSHOT
    }
    private final Mode mode
    private final Set<Project> projectsToPublish

    Publisher(Mode mode, Set<Project> projectsToPublish) {
        this.mode = mode
        this.projectsToPublish = projectsToPublish
    }
    void decorate(Project project, MavenPublication publication) {
        publication.version = determineVersion(project)
        publication.pom.withXml {
            def rootNode = asNode()
            validatePomXml(project, rootNode)
            processDependencies(project, rootNode)
        }
    }

    /** Determines the version to publish a project with. */
    String determineVersion(Project p) {
        if(projectsToPublish.empty || projectsToPublish.contains(p)) {
            return renderVersion(p.version.toString(), mode)
        }
        if (p.hasProperty('latestReleasedVersion')) {
            return p.latestReleasedVersion
        }
        return UNRELEASED_VERSION
    }

    private static void validatePomXml(Project p, Node pom) {
        def unreleased = pom.dependencies.dependency.findAll { it.version.text() == UNRELEASED_VERSION }
                .collect { "${it.groupId.text()}:${it.artifactId.text()}"}
        if(unreleased) {
            throw new GradleException("Failed to release $p. Some of its dependencies don't have a released version: $unreleased")
        }
    }

    private static String renderVersion(String baseVersion, Mode mode) {
        return "${baseVersion}${mode == Mode.SNAPSHOT ? '-SNAPSHOT' : ''}"
    }

    private static void processDependencies(Project project, Node pom) {
        def deps = getDependencyTypes(project)

        pom.dependencies.dependency.each {
            // remove multidex as it is supposed to be added by final applications and is needed for
            // some libraries only for instrumentation tests to build.
            if (it.groupId.text() in ['com.android.support', 'androidx.multidex'] && it.artifactId.text() == 'multidex') {
                it.parent().remove(it)
            }
            it.appendNode('type', [:], deps["${it.groupId.text()}:${it.artifactId.text()}"])

            // change scope to compile to preserve existing behavior
            it.scope.replaceNode {
                createNode('scope', 'compile')
            }
        }
    }

    private static Map<String, String> getDependencyTypes(Project project) {
        def dummyDependencyConfiguration = project.configurations.create('publisherDummyConfig')
        def nonProjectDependencies = project.configurations.releaseRuntimeClasspath.allDependencies.findAll {
            !(it instanceof ProjectDependency)
        }
        dummyDependencyConfiguration.dependencies.addAll(nonProjectDependencies)
        try {
            return project.configurations.releaseRuntimeClasspath.getAllDependencies().collectEntries {
                getType(dummyDependencyConfiguration, it)
            }
        } finally {
            project.configurations.remove(dummyDependencyConfiguration)
        }

    }

    private static def getType(Configuration config, Dependency d) {
        if (d instanceof ProjectDependency) {
            // we currently only support aar libraries to be produced in this repository
            def library = getFirebaseLibrary(d.dependencyProject)
            return [("${library.groupId.get()}:${library.artifactId.get()}" as String): 'aar']
        }
        String path = config.find {
            it.absolutePath.matches(".*\\Q$d.group/$d.name/$d.version/\\E[a-zA-Z0-9]+/\\Q$d.name-$d.version.\\E[aj]ar")
        }?.absolutePath

        if (path && path.endsWith (".aar")) {
            return [("$d.group:$d.name" as String): 'aar']
        } else {
            return [("$d.group:$d.name" as String): 'jar']
        }
    }

    private static FirebaseLibraryExtension getFirebaseLibrary(Project project) {
        return project.extensions.getByType(FirebaseLibraryExtension.class);
    }

}
