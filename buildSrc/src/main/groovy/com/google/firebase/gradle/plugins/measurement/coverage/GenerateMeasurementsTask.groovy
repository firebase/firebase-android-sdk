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

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
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

    private def getCoveragePercentFromReport(_project) {
        def path = "${_project.jacoco.reportsDir}/${coverageTaskName}/${coverageTaskName}.xml"
        try {
            def report = parser.parse(path)
            def name = report.@name
            def lineCoverage = report.counter.find { it.@type == 'LINE' }
            if (lineCoverage) {
                def covered = Double.parseDouble(lineCoverage.@covered.text())
                def missed = Double.parseDouble(lineCoverage.@missed.text())
                def percent = covered / (covered + missed)
                return new Tuple(name, percent)
            } else {
                throw new IllegalStateException("Cannot find line coverage section in the report: $path.")
            }
        } catch (FileNotFoundException e) {
            project.logger.warn("Cannot find coverage report for project: $_project.")
            return new Tuple(_project.name, 0)
        }
    }

    private def generateJson(pullRequestNumber) {
        def coverages = [:]

        // TODO(yifany@): Consolidate mappings with apksize and iOS.
        def sdkMap = [
                'firebase-common': 0,
                'firebase-common-ktx': 1,
                'firebase-database': 2,
                'firebase-database-collection': 3,
                'firebase-firestore': 4,
                'firebase-firestore-ktx': 5,
                'firebase-functions': 6,
                'firebase-inappmessaging-display': 7,
                'firebase-storage': 8
        ]

        for (Project p: project.rootProject.subprojects) {
            if (p.name.startsWith('firebase')) {
                def (name, percent) = getCoveragePercentFromReport(p)
                coverages[sdkMap[name]] = percent
            }
        }

        def replacements = coverages.collect {
            "[$pullRequestNumber, $it.key, $it.value]"
        }.join(", ")

        // TODO(yifany@): Better way of formatting json. No hard code names.
        def json = """
            {
                tables: [
                    {
                        table_name: "PullRequests",
                        column_names: ["pull_request_id"],
                        replace_measurements: [[$pullRequestNumber]],
                    },
                    {
                        table_name: "Coverage2",
                        column_names: ["pull_request_id", "sdk_id", "coverage_percent"],
                        replace_measurements: [$replacements],
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
