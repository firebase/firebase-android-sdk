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


package com.google.firebase.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Generates size measurements after building the test apps and outputs them as a text-format
 * protocol buffer report.
 *
 * <p>This task requires two properties, an SDK map, as input, and the report, as output. The map is
 * used to convert project names and build variants into the SDK identifiers used by the database.
 * The report path is where the output should be stored. Additionally, a project property, {@code
 * pull_request} is used in the report. Excluding this value will send a human-readable version
 * to standard out.
 */
public class GenerateMeasurementsTask extends DefaultTask {

    /**
     * The file storing the SDK map.
     *
     * <p>This may be any type recognized by Gradle as a file. The format of the file's contents is
     * headerless CSV with a colon as a delimiter: projectName-buildVariant:sdkId. The first column
     * contains both the project name and build variant separated by an hyphen. The SDK ID is the
     * integer identifier used by the SQL database to represent this SDK and build variant pair.
     *
     * <p>A complete example follows:
     * <pre>{@code
     * database-debug:1
     * database-release:2
     * firestore-release:7
     * firestore-debug:4
     *}</pre>
     */
    @InputFile
    File sdkMapFile

    /**
     * The file for storing the report.
     *
     * <p>This may be any type recognized by Gradle as a file. The contents, if any, will be
     * overwritten by the new report.
     */
    @OutputFile
    File reportFile

    @Override
    Task configure(Closure closure) {
        project.android.variantFilter {
            if (it.buildType.name != "aggressive") {
                it.ignore = true;
            }
        }

        outputs.upToDateWhen { false }
        dependsOn "assemble"
        return super.configure(closure)
    }

    @TaskAction
    def generate() {
        // Check if we need to run human-readable or upload mode.
        if (project.hasProperty("pull_request")) {
            def pullRequestNumber = project.properties["pull_request"]
            def sdkMap = createSdkMap()
            def sizes = calculateSizesForUpload(sdkMap, project.android.applicationVariants)
            def report = createReportForUpload(pullRequestNumber, sizes)

            reportFile.withWriter {
                it.write(report)
            }
        } else {
            def sizes = calculateHumanReadableSizes(project.android.applicationVariants)
            printHumanReadableReport(sizes)
        }
    }

    private def calculateHumanReadableSizes(variants) {
        def sizes = [:]
        def processor = {flavor, build, size ->
            sizes[new Tuple2(flavor, build)] = size
        }

        calculateSizesFor(variants, processor)
        return sizes
    }

    private def calculateSizesForUpload(sdkMap, variants) {
        def sizes = [:]
        def processor = { flavor, build, size ->
            def name = "${flavor}-${build}"
            def sdk = sdkMap[name];

            if (sdk == null) {
                throw new IllegalStateException("$name not included in SDK map")
            }
            sizes[sdk] = size
        }

        calculateSizesFor(variants, processor)
        return sizes
    }

    private def calculateSizesFor(variants, processor) {
        // Each variant should have exactly one APK. If there are multiple APKs, then this file is
	// out of sync with our Gradle configuration, and this task fails. If an APK is missing, it
	// is silently ignored, and the APKs from the other variants will be used to build the
	// report.
        variants.each { variant ->
            def flavorName = variant.flavorName
            def buildType = variant.buildType.name
            def apks = variant.outputs.findAll { it.outputFile.name.endsWith(".apk") }
            if (apks.size() > 1) {
	        def msg = "${flavorName}-${buildType} produced more than one APK"
                throw new IllegalStateException(msg)
            }

            // This runs at most once, as each variant at this point has zero or one APK.
            apks.each {
                def size = it.outputFile.size()
                processor.call(flavorName, buildType, size)
            }
        }
    }

    private def printHumanReadableReport(sizes) {
        project.logger.quiet("|------------------        APK Sizes        ------------------|")
        project.logger.quiet("|--    project    --|--  build type   --|--  size in bytes  --|")

        sizes.each { key, value ->
            def line = sprintf("|%-19s|%-19s|%-21s|", key.first, key.second, value)
            project.logger.quiet(line)
        }
    }

    // TODO(allisonbm): Remove hard-coding protocol buffers. This code manually generates the
    // text-format protocol buffer report. This eliminates requiring buildSrc to depend on the
    // uploader (or simply, the protocol buffer library), but this isn't the most scalable option.
    private def createReportForUpload(pullRequestNumber, sizes) {
        def sdkId = 0
        def apkSize = 0

        def pullRequestGroup = """
            groups {
                table_name: "PullRequests"
                column_names: "pull_request_id"
                measurements {
                    values {
                        int_value: ${pullRequestNumber}
                    }
                }
            }
        """

        def sizeGroupHeader = """
            groups {
                table_name: "ApkSizes"
                column_names: "sdk_id"
                column_names: "pull_request_id"
                column_names: "apk_size"
        """

        def sizeGroupEntry = """
                measurements {
                    values {
                        int_value: ${->sdkId}
                    }
                    values {
                        int_value: ${pullRequestNumber}
                    }
                    values {
                        int_value: ${->apkSize}
                    }
                }
        """

        def sizeGroupFooter = """
            }
        """


        def builder = new StringBuilder()
        builder.append(pullRequestGroup)
        builder.append(sizeGroupHeader)

        sizes.each { key, value ->
            // sdkId and apkSize are lazily interpolated into sizeGroupEntry.
            sdkId = key
            apkSize = value
            builder.append(sizeGroupEntry)
        }

        builder.append(sizeGroupFooter)
        return builder.toString()
    }

    private def createSdkMap() {
        def map = [:]

        sdkMapFile.eachLine {
            def delimiter = it.indexOf(":")
            def key = it.substring(0, delimiter).trim()
            def value = it.substring(delimiter + 1).trim()
            map[key] = Integer.parseInt(value)
        }

        return map
    }
}
