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

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPom

@CompileStatic
class LicenseResolver {

    /** Resolve all transitive licenses. */
    Set<ProjectLicense> resolve(Project project, Configuration configuration) {
        return determineExternalLicenses(project, configuration) + determineProjectLicenses(configuration)

    }

    private static Set<ProjectLicense> determineProjectLicenses(Configuration configuration) {
        configuration.allDependencies.findAll {
            it instanceof ProjectDependency
        }.collectMany {
            Project depProject = ((ProjectDependency) it).dependencyProject
            Set<ProjectLicense> transitive = determineProjectLicenses(depProject.configurations.getByName(configuration.name))
            return [determineLicense(depProject)] + transitive
        }.findAll { it != null } as Set
    }

    private static Set<ProjectLicense> determineExternalLicenses(Project project, Configuration configuration) {
        def conf = project.configurations.create('internalAllExternalArtifacts')

        // here we are not adding Project-level dependencies to the configuration but instead
        // recursively determine non-project dependencies. The reason for not including project
        // dependencies is that it causes resolution to fail due to variant ambiguity. Instead
        // project licenses are determined in a separate method.
        configuration.allDependencies.each {
            if (it instanceof ProjectDependency) {
                conf.dependencies.addAll determineDependencies(it, configuration)
            } else {
                conf.dependencies.add it
            }
        }

        return conf.resolve().collect { ProjectLicense.inferProjectLicenseFromArtifact(it) }.findAll {
            it != null
        }.collect {
            new ProjectLicense(name: it.name, explicitLicenseUris: it.licenseUris)
        } as Set
    }

    private static Set<Dependency> determineDependencies(Dependency dep, Configuration configuration) {
        if (dep instanceof ProjectDependency) {
            return dep.dependencyProject.configurations.getByName(configuration.name).allDependencies.collectMany {
                determineDependencies(it, configuration)
            } as Set
        } else {
            return [dep] as Set
        }
    }

    private static ProjectLicense determineLicense(Project p) {
        PublishingExtension publishing = p.extensions.findByType(PublishingExtension)
        if (publishing == null) {
            return null
        }
        Publication pub = publishing.publications.findByName('mavenAar')
        if (pub == null || !(pub instanceof MavenPublication)) {
            return null
        }
        MavenPublication publication = (MavenPublication) pub

        DefaultMavenPom pom = (DefaultMavenPom) publication.pom

        return new ProjectLicense(name: publication.artifactId, explicitLicenseUris: pom.licenses.collect {
            URI.create(it.url.get())
        })
    }
}
