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

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

/** Handles publication versioning and pom validation upon release. */
class Publisher {
    private static String UNRELEASED_VERSION = 'unreleased'
    enum Mode {
        RELEASE,
        SNAPSHOT
    }
    private final Mode mode;
    private final Set<Project> projectsToPublish;

    Publisher(Mode mode, Set<Project> projectsToPublish) {
        this.mode = mode
        this.projectsToPublish = projectsToPublish
    }
    void decorate(Project project, MavenPublication publication) {
        publication.version = determineVersion(project)
        publication.pom.withXml {
            def rootNode = asNode()
            validatePomXml(project, rootNode)
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

    private void validatePomXml(Project p, Node pom) {
        def unreleased = pom.dependencies.dependency.findAll { it.version.text() == UNRELEASED_VERSION }
                .collect { "${it.groupId.text()}:${it.artifactId.text()}"}
        if(unreleased) {
            throw new GradleException("Failed to release $p. Some of its dependencies don't have a released version: $unreleased")
        }
    }

    private static String renderVersion(String baseVersion, Mode mode) {
        return "${baseVersion}${mode == Mode.SNAPSHOT ? '-SNAPSHOT' : ''}"
    }

}
