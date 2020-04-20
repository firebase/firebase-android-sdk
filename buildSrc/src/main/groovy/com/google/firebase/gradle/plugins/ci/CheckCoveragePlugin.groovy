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

package com.google.firebase.gradle.plugins.ci

import com.google.firebase.gradle.plugins.FirebaseLibraryExtension
import com.google.firebase.gradle.plugins.FirebaseLibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoReport

class CheckCoveragePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.configure(project.subprojects) { sub ->
            apply plugin: 'jacoco'

            def reportsDir = "${buildDir}/reports/jacoco"
            jacoco {
                toolVersion = '0.8.5'
                reportsDir = reportsDir
            }

            plugins.withType(FirebaseLibraryPlugin.class) {

                sub.tasks.withType(Test) {
                    jacoco {
                        excludes = ['jdk.internal.*']
                        includeNoLocationClasses = true
                    }
                }

                sub.task('checkCoverage', type: JacocoReport) {
                    dependsOn 'check'
                    description 'Generates JaCoCo check coverage report.'
                    group 'verification'

                    def excludes = [
                            '**/R.class',
                            '**/R$*.class',
                            '**/BuildConfig.*',
                            '**/proto/**',
                            '**Manifest*.*'
                    ]
                    classDirectories = files([
                            fileTree(dir: "$buildDir/intermediates/javac/release", excludes: excludes),
                            fileTree(dir: "$buildDir/tmp/kotlin-classes/release", excludes: excludes),
                    ])
                    sourceDirectories = files(['src/main/java', 'src/main/kotlin'])
                    executionData = fileTree(dir: "$buildDir", includes: ['jacoco/*.exec'])

                    def firebaseLibrary = sub.extensions.getByType(FirebaseLibraryExtension.class)
                    def artifactId = firebaseLibrary.artifactId.get()
                    reports {
                        html.destination file("${reportsDir}/${artifactId}/html")
                        xml {
                            enabled true
                            destination file("${reportsDir}/${artifactId}.xml")
                        }
                    }

                    outputs.upToDateWhen { false }
                }
            }
        }
    }
}
