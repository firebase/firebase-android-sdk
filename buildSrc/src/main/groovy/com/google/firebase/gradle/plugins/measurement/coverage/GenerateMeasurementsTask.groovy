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


package com.google.firebase.gradle.plugins.measurement.coverage

import groovy.json.JsonOutput
import java.text.SimpleDateFormat
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import com.google.firebase.gradle.plugins.FirebaseLibraryExtension
import org.gradle.testing.jacoco.tasks.JacocoReport


public class GenerateMeasurementsTask extends DefaultTask {

    @OutputFile
    File reportFile

    XmlSlurper parser

    String coverageTaskName

    GenerateMeasurementsTask() {
        parser = new XmlSlurper()
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
        parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

        assert project.tasks.withType(JacocoReport).size() == 1 : 'Found multiple tasks which generate coverage reports.'
        coverageTaskName = project.tasks.withType(JacocoReport)[0].name

        dependsOn(findAllProductCoverageTasks())
    }

    @TaskAction
    def generate() {
        if (project.hasProperty("pull_request")) {
            def pullRequestNumber = project.properties["pull_request"]
            generateJson(pullRequestNumber)
        } else {
            throw new IllegalStateException("Cannot find pull request number from project properties.")
        }
    }

    // This method was called in a closure in `generateJson` method. At runtime,
    // the closure is bound to a synthetic child class of this class generated
    // by Gradle.
    // Need to mark as protected or public to be callable within the closure.
    protected def getCoverageForProject(_project) {
        def path = "${_project.jacoco.reportsDir}/${coverageTaskName}/${coverageTaskName}.xml"
        try {
            def report = parser.parse(path)
            def lineCoverage = report.counter.find { it.@type == 'LINE' }
            if (lineCoverage) {
                def covered = Double.parseDouble(lineCoverage.@covered.text())
                def missed = Double.parseDouble(lineCoverage.@missed.text())
                return covered / (covered + missed)
            } else {
                throw new IllegalStateException("Cannot find line coverage section in the report: $path.")
            }
        } catch (FileNotFoundException e) {
            project.logger.warn("Cannot find coverage report for project: $_project.")
            return 0
        }
    }

    private def findAllFirebaseProductProjects() {
        return project.rootProject.allprojects.findAll {
            it.extensions.findByType(FirebaseLibraryExtension) != null
        }
    }

    private def findAllProductCoverageTasks() {
        return findAllFirebaseProductProjects().collect {
            it.tasks.withType(JacocoReport)
        }.flatten()
    }

    private def generateJson(pullRequestNumber) {
        def now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
        def projects = findAllFirebaseProductProjects()

        def replacements = projects.collect {
            [ it.path, pullRequestNumber, getCoverageForProject(it), now ]
        }

        // TODO(yifany@): Better way of formatting json. No hard code names.
        def json = """
            {
                tables: [
                    {
                        table_name: "AndroidCodeCoverage",
                        column_names: ["product_name", "pull_request_id", "coverage_total", "collection_time"],
                        replace_measurements: ${JsonOutput.toJson(replacements)},
                    },
                ],
            }
        """

        project.logger.quiet(json)

        reportFile.withWriter {
            it.write(json)
        }
    }

}
