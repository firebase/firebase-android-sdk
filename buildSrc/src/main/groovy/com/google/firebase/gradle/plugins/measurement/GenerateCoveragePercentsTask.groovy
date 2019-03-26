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


package com.google.firebase.gradle.plugins.measurement

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction


public class GenerateCoveragePercentsTask extends DefaultTask {

    @OutputFile
    File reportFile

    @Input
    List<String> packages

    XmlSlurper parser

    GenerateCoveragePercentsTask() {
        parser = new XmlSlurper()
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
        parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    }

    @TaskAction
    def generate() {
        def pullRequestNumber = project.properties["pull_request"] ?: 777
        generateJson(pullRequestNumber, packages)
    }

    private def getCoveragePercentFromReport(path) {
        println "Parsing coverage file: $path"
        def report = parser.parse(path)
        def name = report.@name

        def lineCoverage = report.counter.find {it.@type == 'LINE'}
        def covered = Double.parseDouble(lineCoverage.@covered.text())
        def missed = Double.parseDouble(lineCoverage.@missed.text())
        def percent = covered / (covered + missed)

        return new Tuple(name, percent)
    }


    private def generateJson(pullRequestNumber, packages) {
        def coverages = [:]

        // TODO(yifany@): Consolidate mappings with apksize and iOS.
        def sdkMap = [
                'firebase-common':1,
                'firebase-database':2,
                'firebase-database-collection':3,
                'firebase-firestore':4,
                'firebase-functions':5,
                'firebase-inappmessaging-display':6,
                'firebase-storage':7
        ]

        for (String p: packages) {
            def path = p + '/build/reports/jacoco/checkCoverage/checkCoverage.xml'
            def (name, percent) = getCoveragePercentFromReport(path)
            coverages[sdkMap[name]] = percent
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

        println(json)

        reportFile.withWriter {
            it.write(json)
        }
    }

}
