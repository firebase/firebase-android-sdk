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

import digital.wup.android_maven_publish.AndroidMavenPublishPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Zip

/**
 * Enables releasing of the SDKs.
 *
 * <p>The plugin supports multiple workflows.
 *
 * <p><strong>Build all SDK snapshots</strong>
 *
 * <pre>
 * ./gradlew publishAllToLocal # publishes to maven local repo
 * ./gradlew publishAllToBuildDir # publishes to build/m2repository
 * </pre>
 *
 * <p><strong>Prepare a release</strong>
 *
 * <pre>
 * ./gradlew -PprojectsToPublish="firebase-inappmessaging,firebase-inappmessaging-display"\
 *           -PpublishMode=(RELEASE|SNAPSHOT) \
 *           firebasePublish
 * </pre>
 *
 * <ul>
 *   <li>{@code projectsToPublish} is a list of projects to release separated by
 *       {@code projectsToPublishSeparator}(default: ","), these projects will have their versions
 *       depend on the {@code publishMode} parameter.
 *   <li>{@code publishMode} can one of two values: {@code SNAPSHOT} results in version to be
 *       {@code "${project.version}-SNAPSHOT"}. {@code RELEASE} results in versions to be whatever
 *       is specified in the SDKs gradle.properties. Additionally when {@code RELEASE} is
 *       specified, the release validates the pom to make sure no SDKs point to unreleased SDKs.
 *   <li>{@code projectsToPublishSeparator}: separates project names in the {@projectsToPublish}
 *       parameter. Default is: ",".
 *
 * <p>The artifacts will be built to build/m2repository.zip
 *
 * <p><strong>Prepare release(to maven local)</strong>
 *
 * <p>Same as above but publishes artifacts to maven local.
 *
 * <pre>
 * ./gradlew -PprojectsToPublish="firebase-inappmessaging,firebase-inappmessaging-display"\
 *           -PpublishMode=(RELEASE|SNAPSHOT) \
 *           publishProjectsToMavenLocal
 * </pre>
 */
class PublishingPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {

        def projectNamesToPublish = project.properties.getOrDefault('projectsToPublish', '')
        def projectsToPublishSeparator = project.properties.getOrDefault('projectsToPublishSeparator', ',')
        def publishMode = project.properties.getOrDefault('publishMode', 'SNAPSHOT') as Publisher.Mode

        def projectsToPublish = projectNamesToPublish.split(projectsToPublishSeparator)
                .findAll{!it.empty}
                .collect { project.project(it) } as Set<Project>

        project.ext.projectsToPublish = projectsToPublish

        Publisher publisher = new Publisher(publishMode, projectsToPublish)

        def publishAllToLocal = project.task('publishAllToLocal')
        def publishAllToBuildDir = project.task('publishAllToBuildDir')
        def firebasePublish = project.task('firebasePublish')

        project.subprojects {
            afterEvaluate { Project sub ->
                if (!sub.plugins.hasPlugin('com.android.library')) {
                    return
                }

                sub.ext.versionToPublish = publisher.determineVersion(sub)

                sub.apply plugin: AndroidMavenPublishPlugin
                sub.publishing {
                    repositories {
                        maven {
                            url = "file://$rootProject.buildDir/m2repository"
                            name = 'BuildDir'
                        }
                    }
                    publications {
                        mavenAar(MavenPublication) {
                            from sub.components.android
                            pom {
                                licenses {
                                    license {
                                        name = 'The Apache Software License, Version 2.0'
                                        url = 'http://www.apache.org/licenses/LICENSE-2.0.txt'
                                    }
                                }
                                scm {
                                    connection = 'scm:git:https://github.com/firebase/firebase-android-sdk.git'
                                    url = 'https://github.com/firebase/firebase-android-sdk'
                                }
                            }
                            publisher.decorate(sub, it)
                        }
                    }
                    publishAllToLocal.dependsOn "$sub.path:publishMavenAarPublicationToMavenLocal"
                    publishAllToBuildDir.dependsOn "$sub.path:publishMavenAarPublicationToBuildDirRepository"

                }
            }
        }
        project.task('publishProjectsToMavenLocal') {
            projectsToPublish.each { projectToPublish ->
                dependsOn getPublishTask(projectToPublish, 'MavenLocal')
            }
        }

        def publishProjectsToBuildDir = project.task('publishProjectsToBuildDir') { task ->
            projectsToPublish.each { projectToPublish ->
                dependsOn getPublishTask(projectToPublish, 'BuildDirRepository')
            }
        }
        def buildMavenZip = project.task('buildMavenZip', type: Zip) {
            dependsOn publishProjectsToBuildDir

            from "$project.buildDir/m2repository"
            archiveName "$project.buildDir/m2repository.zip"
        }

        firebasePublish.dependsOn buildMavenZip
    }

    private static String getPublishTask(Project p, String repoName) {
        return "${p.path}:publishMavenAarPublicationTo$repoName"
    }
}
