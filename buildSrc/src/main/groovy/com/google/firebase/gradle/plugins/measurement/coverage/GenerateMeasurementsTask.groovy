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

import com.google.firebase.gradle.plugins.measurement.utils.enums.ColumnName
import com.google.firebase.gradle.plugins.measurement.utils.enums.TableName
import com.google.firebase.gradle.plugins.measurement.utils.reports.JsonReport
import com.google.firebase.gradle.plugins.measurement.utils.reports.Table
import com.google.firebase.gradle.plugins.measurement.utils.reports.TableReport
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.testing.jacoco.tasks.JacocoReport

/**
 * Generates coverage measurements after unit tests.
 */
class GenerateMeasurementsTask extends DefaultTask {

    /** The file for storing the coverage report. */
    @OutputFile
    File reportFile

    XmlSlurper parser

    String coverageTaskName

    GenerateMeasurementsTask() {
        parser = new XmlSlurper()
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
        parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

        assert project.tasks.withType(JacocoReport).size() == 1:
                'Found multiple tasks which generate coverage reports.'
        coverageTaskName = project.tasks.withType(JacocoReport)[0].name
    }

    @TaskAction
    def generate() {
        if (project.hasProperty("pull_request")) {
            def pullRequestNumber = project.properties["pull_request"]
            generateJson(pullRequestNumber)
        } else {
            generateTableReport()
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

    private def calculateCoverages() {
        def coverages = [:]
        for (p in project.rootProject.subprojects) {
            if (p.name.startsWith('firebase')) {
                def (name, percent) = getCoveragePercentFromReport(p)
                coverages[name] = percent
            }
        }
        return coverages
    }

    private def generateJson(pullRequestNumber) {
        // TODO(yifany@): Consolidate mappings with apksize and iOS.
        def sdkMap = [
                'firebase-common'                : 0,
                'firebase-common-ktx'            : 1,
                'firebase-database'              : 2,
                'firebase-database-collection'   : 3,
                'firebase-datatransport'         : 4,
                'firebase-firestore'             : 5,
                'firebase-firestore-ktx'         : 6,
                'firebase-functions'             : 7,
                'firebase-inappmessaging-display': 8,
                'firebase-storage'               : 9
        ]

        def coverages = calculateCoverages()

        def pullRequestTable = new Table(tableName: TableName.PULL_REQUESTS,
                columnNames: [ColumnName.PULL_REQUEST_ID],
                replaceMeasurements: [[pullRequestNumber]])
        def coverageTable = new Table(tableName: TableName.ANDROID_COVERAGE,
                columnNames: [ColumnName.PULL_REQUEST_ID, ColumnName.SDK_ID, ColumnName.COVERAGE_PERCENT],
                replaceMeasurements: coverages.collect {
                    [pullRequestNumber, sdkMap[it.key], it.value]
                })
        def jsonReport = new JsonReport(tables: [pullRequestTable, coverageTable])

        project.logger.quiet(jsonReport.toString())

        reportFile.withWriter {
            it.write(jsonReport.toString())
        }
    }

    private def generateTableReport() {
        def coverages = calculateCoverages()
        def tableReport = new TableReport(tableName: TableName.ANDROID_COVERAGE,
                columnNames: [ColumnName.SUBPROJECT, ColumnName.COVERAGE_PERCENT])
        coverages.each { k, v -> tableReport.addReplaceMeasurement([k, v]) }
        project.logger.quiet(tableReport.toString())
    }

}
