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
 * pull_request} is also required. This value will be placed in the report.
 */
public class GenerateMeasurementsTask extends DefaultTask {

    /**
     * The file storing the SDK map.
     *
     * <p>This may be any type recognized by Gradle as a file. The format of the file's contents is
     * headerless CSVwith a colon as a delimiter: projectName_buildVariant:sdkId. The first column
     * contains both the project name and build variant separated by an hyphen, {@code
     * firestore-aggressive}, for example.
     */
    @InputFile
    File sdkMap

    /**
     * The file for storing the report.
     *
     * <p>This may be any type recognized by Gradle as a file. The contents, if any, will be
     * overwritten by the new report.
     */
    @OutputFile
    File report

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
        def sdks = createSdkMap()
        def sizes = calculateSizes(sdks, project.android.applicationVariants)
        def report = createReport(sizes)

        project.file(this.report).withWriter {
            it.write(report)
        }
    }

    private def calculateSizes(sdks, variants) {
      def sizes = [:]

      variants.each { variant ->
        def name = "${variant.flavorName}-${variant.buildType.name}"
        def apks = variant.outputs.findAll { it.outputFile.name.endsWith(".apk") }
        if (apks.size() > 1) {
            throw new IllegalStateException("${name} produced more than one APK")
        }

        apks.each {
            def size = it.outputFile.size();
            def sdk = sdks[name];

            if (sdk == null) {
                throw new IllegalStateException("$name not included in SDK map")
            }
            sizes[sdk] = size
        }
      }

      return sizes
    }

    // TODO(allisonbm): Remove hard-coding protocol buffers. This code manually generates the
    // text-format protocol buffer report. This eliminates requiring buildSrc to depend on the
    // uploader (or simply, the protocol buffer library), but this isn't the most scalable option.
    private def createReport(sizes) {
        def pullRequestNumber = project.properties["pull_request"]
        if (pullRequestNumber == null) {
            throw new IllegalStateException("`-Ppull_request` not defined")
        }

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
        def path = project.file(sdkMap)

        path.eachLine {
            def delimiter = it.indexOf(":")
            def key = it.substring(0, delimiter).trim()
            def value = it.substring(delimiter + 1).trim()
            map[key] = Integer.parseInt(value)
        }

        return map
    }
}
