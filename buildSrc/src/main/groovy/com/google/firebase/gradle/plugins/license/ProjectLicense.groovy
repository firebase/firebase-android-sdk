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

package com.google.firebase.gradle.plugins.license

import groovy.util.slurpersupport.GPathResult

class ProjectLicense implements Serializable {
    private static final FileNameFinder FINDER = new FileNameFinder()

    String name
    List<URI> explicitLicenseUris
    ProjectLicense parent

    List<URI> getLicenseUris() {
        return getUris(this)
    }

    private static List<URI> getUris(ProjectLicense license) {
        if (license == null) {
            return []
        }
        return license.explicitLicenseUris ?: getUris(license.parent)
    }

    static ProjectLicense inferProjectLicenseFromArtifact(File artifact) {
        return parsePom(findPomRelativeToArtifact(artifact))
    }

    /**
     * According to gradle's dependency cache layout(files-2.1), the artifacts are stored in the following way:
     *
     * <ul>
     *   <li>${groupId}/${artifactId}/${version}/sha1(file)/*.{jar|aar}
     *   <li>${groupId}/${artifactId}/${version}/sha1(file)/*.pom
     * <ul>
     *
     * <p>So to get to a pom file based on the aar, we need to look for it based on the following
     * pattern: `../{@literal *}/*.pom`
     */
    private static File findPomRelativeToArtifact(File artifact) {
        return FINDER.getFileNames(artifact.parentFile.parentFile.absolutePath, '*/*.pom').collect {
            new File(it)
        }.find()
    }

    private static ProjectLicense parsePom(File pomFile) {
        if (pomFile == null) return null
        pomFile.withReader {
            def project = new XmlSlurper().parse(it)
            def name = project.name
            def licenses = project.licenses.license.collect { URI.create(it.url as String) }
            return new ProjectLicense(
                    name: name,
                    explicitLicenseUris: licenses,
                    parent: determineParent(pomFile.parentFile.parentFile.parentFile.parentFile.parentFile, project.parent))
        }
    }

    // some poms don't include their license directly but instead declare themselves as inheriting
    // it from their parent pom.
    private static ProjectLicense determineParent(File baseDir, GPathResult parentNode) {
        if (!parentNode.childNodes()) {
            return null
        }
        def pomFile = FINDER.getFileNames(baseDir.absolutePath, "$parentNode.groupId/$parentNode.artifactId/$parentNode.version/*/*.pom").collect {
            new File(it)
        }.find()
        return parsePom(pomFile)
    }
}
