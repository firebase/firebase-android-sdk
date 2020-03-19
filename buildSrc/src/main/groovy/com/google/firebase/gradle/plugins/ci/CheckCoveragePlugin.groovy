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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoReport

class CheckCoveragePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.configure(project.subprojects) {
            apply plugin: 'jacoco'

            jacoco {
                toolVersion = '0.8.5'
            }

            tasks.withType(Test) {
                jacoco {
                    excludes = ['jdk.internal.*']
                }
            }

            task('checkCoverage', type: JacocoReport) {
                dependsOn 'check'
                description 'Generates check coverage report and uploads to Codecov.io.'
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
                reports {
                    html.enabled true
                    xml.enabled true
                }

                outputs.upToDateWhen { false }

                doFirst {
                    logger.quiet("Reports directory: ${it.project.jacoco.reportsDir}")
                }

                doLast {
                    upload it
                }
            }

            tasks.withType(Test) {
                jacoco.includeNoLocationClasses true
            }

        }
    }

    private def upload(task) {
        if (System.getenv().containsKey("FIREBASE_CI")) {
            def flag = convert(task.project.path)
            def report = task.reports.xml.destination

            if (System.getenv().containsKey("PROW_JOB_ID")) {
                task.logger.quiet("Prow CI detected.")
                uploadFromProwJobs(task.project, report, flag)
            } else {
                uploadFromCodecovSupportedEnvironment(task.project, report, flag)
            }
        } else {
            task.logger.quiet("Reports upload is enabled only on CI.")
        }
    }

    private def uploadFromProwJobs(project, report, flag) {
        // https://github.com/kubernetes/test-infra/blob/master/prow/jobs.md
        def name = System.getenv("JOB_NAME")
        def type = System.getenv("JOB_TYPE")
        def job = System.getenv("PROW_JOB_ID")
        def build = System.getenv("BUILD_ID")
        def org = System.getenv("REPO_OWNER")
        def repo = System.getenv("REPO_NAME")
        def branch = System.getenv("PULL_BASE_REF")
        def base = System.getenv("PULL_BASE_SHA")
        def head = System.getenv("PULL_PULL_SHA")
        def pr = System.getenv("PULL_NUMBER")

        def commit = head ?: base

        // TODO(yifany): use com.google.firebase.gradle.plugins.measurement.TestLogFinder
        def domain = "android-ci.firebaseopensource.com"
        def bucket = "android-ci"
        def dir = type == "presubmit" ? "pr-logs/pull/${org}_${repo}/${pr}" : "logs"
        def path = "${name}/${build}"
        def url = URLEncoder.encode("https://${domain}/view/gcs/${bucket}/${dir}/${path}", "UTF-8")

        project.exec {
            environment "VCS_COMMIT_ID", "${commit}"
            environment "VCS_BRANCH_NAME", "${branch}"
            environment "VCS_PULL_REQUEST", "${pr}"
            environment "VCS_SLUG", "${org}/${repo}"
            environment "CI_BUILD_URL", "${url}"
            environment "CI_BUILD_ID", "${build}"
            environment "CI_JOB_ID", "${job}"

            commandLine(
                    "bash",
                    "-c",
                    "bash /opt/codecov/uploader.sh -f ${report} -F ${flag}"
            )
        }
    }

    private def uploadFromCodecovSupportedEnvironment(project, report, flag) {
        project.exec {
            commandLine(
                    "bash",
                    "-c",
                    "bash <(curl -s https://codecov.io/bash) -f ${report} -F ${flag}"
            )
        }
    }

    /*
     * Converts a gradle project path to a format complied with Codecov flags.
     *
     * It transforms a gradle project name into PascalCase, removes the leading `:` and
     * replaces all the remaining `:` with `_`.
     *
     * For example, a gradle project path
     *
     *     `:encoders:firebase-encoders-processor:test-support`
     *
     * is converted to
     *
     *     `Encoders_FirebaseEncodersProcessor_TestSupport`
     *
     * after processing.
     *
     * See https://docs.codecov.io/docs/flags#section-flag-creation for details.
     */
    private def convert(path) {
        return path
                .replaceAll(/([:-])([a-z])/, { "${it[1]}${it[2].toUpperCase()}" })
                .replaceAll(/^:/, "")
                .replaceAll(/-/, "")
                .replaceAll(/:/, "_")
    }

}
