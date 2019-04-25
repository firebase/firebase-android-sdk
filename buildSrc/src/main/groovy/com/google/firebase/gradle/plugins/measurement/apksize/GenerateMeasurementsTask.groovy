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


package com.google.firebase.gradle.plugins.measurement.apksize

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Generates size measurements after building the test apps.
 *
 * <p>This task can run in two modes. The first mode, is a dependency for {@link
 * UploadMeasurementsTask} and generates a JSON file with measurement information. This file
 * references database IDs, and is not considered to be human-friendly. The second mode outputs a
 * table to standard out with more useful information. These modes can be toggled by adding the
 * {@code pull_request} flag to the task. See the README for more details.
 *
 * <p>This task requires two properties, an SDK map, as input, and the report, as output. The map is
 * used to convert project names and build variants into the SDK identifiers used by the database.
 * The report path is where the output should be stored. These properties are not used when the task
 * is run in the second, humna-friendly mode. However, they are still required to be specified.
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
        def variants = project.android.applicationVariants

        // Check if we need to run human-readable table or JSON upload mode.
        if (project.hasProperty("pull_request")) {
            def pullRequestNumber = project.properties["pull_request"]
            generateJson(pullRequestNumber, variants)
        } else {
            generateTable(variants)
        }
    }

    private def generateJson(pullRequestNumber, variants) {
        def sdkMap = createSdkMap()
        def builder = new ApkSizeJsonBuilder(pullRequestNumber)
        def variantProcessor = {projectName, buildType, apkSize ->
            def name = "$projectName-$buildType"
            def sdkId = sdkMap[name]

            if (sdkId == null) {
                throw new IllegalStateException("$name not included in SDK map")
            }

            builder.addApkSize(sdkId, apkSize)
        }

        calculateSizes(variants, variantProcessor)
        reportFile.withWriter {
            it.write(builder.toJsonString())
        }
    }

    private def generateTable(variants) {
        def builder = new ApkSizeTableBuilder()
        def variantProcessor = {projectName, buildType, apkSize ->
            builder.addApkSize(projectName, buildType, apkSize)
        }

        calculateSizes(variants, variantProcessor)
        project.logger.quiet(builder.toTableString())
    }

    private def calculateSizes(variants, processor) {
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
